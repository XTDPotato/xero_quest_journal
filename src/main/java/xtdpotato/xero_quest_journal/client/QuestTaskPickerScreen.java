package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.QuestDefinition;
import xtdpotato.xero_quest_journal.core.network.QuestCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Searchable task list for choosing one or more quest references. */
public final class QuestTaskPickerScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 30;
    private final Screen parent;
    private final String excludedId;
    private final boolean includeGroups;
    private final Consumer<List<String>> selected;
    private final List<QuestDefinition> tasks;
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private String query = "";
    private List<QuestDefinition> filtered = List.of();
    private record Row(QuestDefinition task, QuestCatalog.Group group, int count) {}
    private List<Row> rows = List.of();
    private final Set<String> collapsed = new LinkedHashSet<>();
    private final java.util.Map<String, QuestCatalog.Group> groups = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> membership = new java.util.HashMap<>();
    private EditBox search;
    private int scroll;
    private double smoothScroll, targetScroll, thumbOffset, dragStartY, dragStartScroll;
    private boolean draggingScrollbar, draggingList;
    private boolean multiSelect;
    private boolean allowEmptySelection;
    private boolean singleSelectionOnly;
    private boolean listMoved;
    private double dragStartX;
    private long lastFrame;

    public QuestTaskPickerScreen(Screen parent, String current, String excludedId,
                                 Consumer<List<String>> selected) {
        this(parent, current, excludedId, false, selected);
    }

    public QuestTaskPickerScreen(Screen parent, String current, String excludedId, boolean includeGroups,
                                 Consumer<List<String>> selected) {
        super(Component.translatable("screen.xero_quest_journal.task_picker.title"));
        this.parent = parent;
        this.excludedId = excludedId == null ? "" : excludedId;
        this.includeGroups = includeGroups;
        this.selected = selected;
        tasks = ClientQuestDefinitions.all().stream()
                .filter(task -> !task.id().equals(this.excludedId)).toList();
        ClientQuestDefinitions.groups().forEach(group -> groups.put(group.id(), group));
        tasks.forEach(task -> membership.put(task.id(), ClientQuestDefinitions.groupOf(task.id())));
        if (current != null) {
            for (String id : current.split("[,\\s]+")) {
                if (includeGroups && groups.containsKey(id) || tasks.stream().anyMatch(task -> task.id().equals(id)))
                    selectedIds.add(id);
            }
        }
        multiSelect = selectedIds.size() > 1;
        filter();
    }

    public QuestTaskPickerScreen multipleSelection(boolean allowEmpty) {
        multiSelect = true;
        allowEmptySelection = allowEmpty;
        return this;
    }

    public QuestTaskPickerScreen singleSelectionOnly() {
        singleSelectionOnly = true;
        multiSelect = false;
        return this;
    }

    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal." + key);
    }

    private int panelWidth() { return Math.min(620, width - 24); }
    private int left() { return (width - panelWidth()) / 2; }
    private int listTop() { return 70; }
    private int listBottom() { return Math.max(listTop() + ROW_HEIGHT, height - 58); }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }
    private int maxScroll() { return Math.max(0, rows.size() - visibleRows()); }
    private int trackX() { return left() + panelWidth() + 3; }
    private int thumbHeight() {
        return Math.max(12, (listBottom() - listTop()) * visibleRows() / Math.max(1, rows.size()));
    }
    private double thumbY() {
        return listTop() + (listBottom() - listTop() - thumbHeight()) * smoothScroll / Math.max(1, maxScroll());
    }

    private void filter() {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        filtered = tasks.stream().filter(task -> {
            if (terms.length == 0) return true;
            var group = groups.get(membership.get(task.id()));
            String index = (QuestIcons.searchIndex(task.id(), QuestText.title(task).getString()) + " "
                    + (group == null ? "" : group.id() + " " + group.name())).toLowerCase(Locale.ROOT);
            for (String term : terms) if (!index.contains(term)) return false;
            return true;
        }).toList();
        var next = new ArrayList<Row>();
        var grouped = new java.util.LinkedHashMap<String, List<QuestDefinition>>();
        for (var task : filtered)
            grouped.computeIfAbsent(membership.getOrDefault(task.id(), ""), ignored -> new ArrayList<>()).add(task);
        for (var group : groups.values()) {
            var members = grouped.remove(group.id());
            if ((members == null || members.isEmpty()) && !includeGroups) continue;
            next.add(new Row(null, group, members == null ? 0 : members.size()));
            if (members != null && (!collapsed.contains(group.id()) || !query.isBlank()))
                members.forEach(task -> next.add(new Row(task, group, 0)));
        }
        grouped.values().forEach(members -> members.forEach(task -> next.add(new Row(task, null, 0))));
        rows = List.copyOf(next);
        targetScroll = smoothScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll = (int) smoothScroll;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = left(), w = panelWidth();
        search = new EditBox(font, x, 38, w, 20, text("task_picker.search"));
        search.setMaxLength(256);
        search.setValue(query);
        search.setHint(text("task_picker.search"));
        search.setResponder(value -> {
            query = value;
            scroll = 0;
            smoothScroll = targetScroll = 0;
            filter();
        });
        addRenderableWidget(search);
        search.setFocused(true);
        addRenderableWidget(new EditorIconButton(width - 36, 8,
                text("task_picker.close"), this::onClose, styles,
                "task-picker-close", "close"));
        int buttonY = height - 30;
        if (multiSelect) {
            addRenderableWidget(new StyledButton(x, buttonY, (w - 8) / 2, 22,
                    text("task_picker.single_mode"), ignored -> {
                        multiSelect = false;
                        selectedIds.clear();
                        init();
                    }, ButtonStyleSheet.load(), "task-picker-single", "toolbar-button"));
            addRenderableWidget(new StyledButton(x + (w + 8) / 2, buttonY, (w - 8) / 2, 22,
                    Component.translatable("screen.xero_quest_journal.task_picker.confirm",
                            selectedIds.size()), ignored -> finish(), ButtonStyleSheet.load(),
                    "task-picker-confirm", "toolbar-button"));
        } else if (!singleSelectionOnly) {
            addRenderableWidget(new StyledButton(x, buttonY, w, 22, text("task_picker.enable_multiple"),
                    ignored -> {
                        multiSelect = true;
                        selectedIds.clear();
                        init();
                    }, ButtonStyleSheet.load(), "task-picker-multiple", "toolbar-button"));
        }
    }

    private void finish() {
        List<String> result = new ArrayList<>(selectedIds);
        if (!result.isEmpty() || allowEmptySelection) selected.accept(List.copyOf(result));
        closeTo(parent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderContents(g, mouseX, mouseY, partialTick);
        int x = left(), right = x + panelWidth();
        long now = System.nanoTime();
        double blend = lastFrame == 0 ? 1 : 1 - Math.exp(-18 * Math.min(.1, (now - lastFrame) / 1e9));
        lastFrame = now;
        if (!draggingScrollbar && !draggingList) {
            smoothScroll += (targetScroll - smoothScroll) * blend;
            if (Math.abs(targetScroll - smoothScroll) < 0.01) smoothScroll = targetScroll;
        }
        scroll = (int) smoothScroll;
        int offset = (int) Math.round((smoothScroll - scroll) * ROW_HEIGHT);
        g.drawCenteredString(font, text("task_picker.title"), width / 2, 12, 0xFFFFFFFF);
        g.fill(x, listTop(), right, listBottom(), 0xCC171B21);
        g.enableScissor(x, listTop(), right, listBottom());
        for (int row = 0; row <= visibleRows() && row + scroll < rows.size(); row++) {
            Row entry = rows.get(row + scroll);
            QuestDefinition task = entry.task();
            int y = listTop() + row * ROW_HEIGHT - offset;
            boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (task == null) {
                g.fill(x + 1, y, right - 1, y + ROW_HEIGHT - 1,
                        selectedIds.contains(entry.group().id()) ? 0xFF45556A
                                : hovered ? 0xFF3C4C49 : 0xFF303E3B);
                g.pose().pushPose();
                g.pose().translate(x + 13, y + 14, 0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                        collapsed.contains(entry.group().id()) && query.isBlank() ? 180 : -90));
                JournalNavButton.drawIcon(g, "back", -6, -6, 12, 0xFFE3EAE5);
                g.pose().popPose();
                QuestIcons.render(g, entry.group().icon(), x + 25, y + 6, 18);
                String count = Integer.toString(entry.count());
                g.drawString(font, font.plainSubstrByWidth(entry.group().name(),
                        Math.max(1, panelWidth() - 64 - font.width(count))), x + 50, y + 10, 0xFFE3EAE5, false);
                g.drawString(font, count, right - font.width(count) - 10, y + 10, 0xFFA9C3B4, false);
                continue;
            }
            boolean chosen = selectedIds.contains(task.id());
            g.fill(x + 1, y, right - 1, y + ROW_HEIGHT - 1,
                    chosen ? 0xFF45556A : hovered ? 0xFF394450 : 0xFF272D35);
            if (multiSelect) {
                JournalNavButton.drawIcon(g, chosen ? "choose" : "add", x + 7, y + 7, 16,
                        chosen ? 0xFFB9E6C8 : 0xFFCBD4DE);
            }
            int indent = entry.group() == null ? 0 : 12;
            QuestIcons.render(g, task.icon(), x + indent + (multiSelect ? 32 : 8), y + 6, 18);
            int textX = x + indent + (multiSelect ? 56 : 32);
            int available = Math.max(1, right - textX - 8);
            String title = font.plainSubstrByWidth(QuestText.title(task).getString(), available);
            g.drawString(font, title, textX, y + 3, 0xFFE6E9ED, false);
            String groupName = entry.group() == null ? text("task_picker.ungrouped").getString() : entry.group().name();
            String detail = groupName + "  ·  " + QuestText.type(task).getString() + "  ·  " + task.id();
            g.drawString(font, font.plainSubstrByWidth(detail, available), textX, y + 16,
                    0xFFABB8C3, false);
        }
        g.disableScissor();
        if (filtered.isEmpty()) {
            g.drawCenteredString(font, text("task_picker.empty"), width / 2, listTop() + 12, 0xFFABB8C3);
        }
        if (rows.size() > visibleRows()) {
            g.fill(trackX(), listTop(), trackX() + 5, listBottom(), 0xFF303741);
            g.fill(trackX(), (int) thumbY(), trackX() + 5, (int) thumbY() + thumbHeight(), 0xFF9DB4D2);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputBlocked()) return true;
        if (button == 1 && mouseX >= left() && mouseX < left() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) Math.floor(smoothScroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                var row = rows.get(index);
                minecraft.keyboardHandler.setClipboard(row.task() == null ? row.group().id() : row.task().id());
            }
            return true;
        }
        if (button == 0 && maxScroll() > 0 && mouseX >= trackX() - 2 && mouseX < trackX() + 9
                && mouseY >= listTop() && mouseY < listBottom()) {
            draggingScrollbar = true;
            thumbOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight()
                    ? mouseY - thumbY() : thumbHeight() / 2.0;
            dragThumb(mouseY);
            return true;
        }
        if (button == 0 && mouseX >= left() && mouseX < left() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            int index = (int) Math.floor(smoothScroll + (mouseY - listTop()) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                draggingList = true;
                listMoved = false;
                dragStartX = mouseX;
                dragStartY = mouseY;
                dragStartScroll = smoothScroll;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragThumb(double y) {
        targetScroll = smoothScroll = Math.clamp((y - listTop() - thumbOffset) * maxScroll()
                / Math.max(1, listBottom() - listTop() - thumbHeight()), 0, maxScroll());
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScrollbar) { dragThumb(y); return true; }
        if (button == 0 && draggingList) {
            listMoved |= Math.abs(y - dragStartY) >= 4 || Math.abs(x - dragStartX) >= 4;
            targetScroll = smoothScroll = Math.clamp(dragStartScroll + (dragStartY - y) / ROW_HEIGHT, 0, maxScroll());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        if (button == 0 && draggingList) {
            draggingList = false;
            if (!listMoved && Math.abs(y - dragStartY) < 4 && Math.abs(x - dragStartX) < 4
                    && x >= left() && x < left() + panelWidth()
                    && y >= listTop() && y < listBottom()) {
                int index = (int) Math.floor(smoothScroll + (y - listTop()) / ROW_HEIGHT);
                if (index >= 0 && index < rows.size()) {
                    var row = rows.get(index);
                    if (row.task() == null) {
                        if (includeGroups) {
                            String id = row.group().id();
                            if (multiSelect) {
                                if (!selectedIds.add(id)) selectedIds.remove(id);
                                init();
                            } else {
                                selected.accept(List.of(id));
                                closeTo(parent);
                            }
                            return true;
                        }
                        if (!collapsed.add(row.group().id())) collapsed.remove(row.group().id());
                        filter();
                        return true;
                    }
                    String id = row.task().id();
                    if (multiSelect) {
                        if (!selectedIds.add(id)) selectedIds.remove(id);
                        init();
                    } else {
                        selected.accept(List.of(id));
                        closeTo(parent);
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && x >= left() && x < left() + panelWidth() + 8
                && y >= listTop() && y < listBottom()) {
            targetScroll = Math.clamp(targetScroll - vertical * 2.5, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeTo(parent);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) {
        backdrop(g, 0xF01C2026);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override protected int animationHeaderBottom() { return 60; }
    @Override protected int animationFooterTop() { return height - 48; }
}
