package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.QuestVariable;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Two independent catalogs with selection, search and stable name/date sorting. */
final class QuestVariableListScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final String taskId;
    private final Consumer<String> insert;
    private final BiConsumer<String, String> changed;
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private EditBox globalSearch, taskSearch;
    private String globalFilter = "", taskFilter = "";
    private final String[] selected = {"", ""};
    private boolean globalByDate, taskByDate;
    private final double[] scroll = {0, 0}, targetScroll = {0, 0};
    private int draggingSide = -1;
    private boolean draggingThumb;
    private double dragStartY, dragStartScroll, thumbOffset;
    private long lastFrame;

    QuestVariableListScreen(Screen parent, String taskId, Consumer<String> insert,
                            BiConsumer<String, String> changed) {
        super(text("list"));
        this.parent = parent;
        this.taskId = taskId;
        this.insert = insert;
        this.changed = changed;
    }
    private static Component text(String key) {
        return Component.translatable("screen.xero_quest_journal.variable." + key);
    }
    private int left(int side) { return side == 0 ? 12 : width / 2 + 6; }
    private int paneWidth(int side) { return side == 0 ? width / 2 - 20 : width / 2 - 18; }
    private int rows() { return Math.max(1, (height - 159) / 24); }
    private int listBottom() { return height - 43; }
    private int trackX(int side) { return left(side) + paneWidth(side) - 5; }
    private int maxScroll(int side) { return Math.max(0, entries(side).size() * 24 - (listBottom() - 120)); }
    private int thumbHeight(int side) {
        int h = listBottom() - 120;
        return Math.max(14, h * h / Math.max(h, entries(side).size() * 24));
    }
    private double thumbY(int side) {
        return 120 + (listBottom() - 120 - thumbHeight(side)) * scroll[side] / Math.max(1, maxScroll(side));
    }
    private List<QuestVariable> entries(int side) {
        String scope = side == 0 ? "globe" : taskId;
        String filter = (side == 0 ? globalFilter : taskFilter).toLowerCase(java.util.Locale.ROOT);
        Comparator<QuestVariable> order = (side == 0 ? globalByDate : taskByDate)
                ? Comparator.comparingLong(QuestVariable::created).reversed().thenComparing(QuestVariable::id)
                : Comparator.comparing(QuestVariable::name).thenComparing(QuestVariable::id);
        var values = new java.util.ArrayList<>(ClientQuestDefinitions.variables().stream()
                .filter(variable -> variable.scope().equals(scope)).toList());
        if (side == 1 && taskId.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}"))
            for (String name : List.of("progress_current", "progress_required", "progress_ratio"))
                values.add(new QuestVariable(taskId + "." + name, "0", 0, "number", "player"));
        return values.stream().filter(variable -> variable.id().toLowerCase(java.util.Locale.ROOT).contains(filter))
                .sorted(order).toList();
    }
    @Override protected void init() {
        if (globalSearch != null) { globalFilter = globalSearch.getValue(); taskFilter = taskSearch.getValue(); }
        clearWidgets();
        for (int side = 0; side < 2; side++) {
            int s = side, x = left(side), w = paneWidth(side);
            action(side == 0 ? "global" : "task", x, 36, w - 86, () -> {});
            action("add", x + w - 82, 36, 82, () -> openEdit(s == 0 ? "globe" : taskId, null));
            EditBox search = new EditBox(font, x, 67, w, 19, text("search"));
            search.setMaxLength(128);
            search.setValue(side == 0 ? globalFilter : taskFilter);
            search.setHint(text("search"));
            search.setResponder(query -> {
                if (s == 0) globalFilter = query;
                else taskFilter = query;
                scroll[s] = targetScroll[s] = 0;
            });
            addRenderableWidget(search);
            if (side == 0) globalSearch = search; else taskSearch = search;
            action((side == 0 ? globalByDate : taskByDate) ? "sort_date" : "sort_name",
                    x, 90, w, () -> {
                        if (s == 0) globalByDate = !globalByDate;
                        else taskByDate = !taskByDate;
                        scroll[s] = targetScroll[s] = 0;
                        init();
                    });
            var edit = action("edit", x, height - 29, (w - 4) / 2, () -> {
                var variable = ClientQuestDefinitions.variables().stream()
                        .filter(v -> v.id().equals(selected[s])).findFirst().orElse(null);
                if (variable != null && !xtdpotato.xero_quest_journal.core.io.QuestVariableStore
                        .builtInLocation(variable.id())) openEdit(variable.scope(), variable);
            });
            var selectedVariable = ClientQuestDefinitions.variables().stream()
                    .filter(v -> v.id().equals(selected[s])).findFirst().orElse(null);
            edit.active = selectedVariable != null
                    && !xtdpotato.xero_quest_journal.core.io.QuestVariableStore.builtInLocation(selectedVariable.id());
            var place = action("insert", x + (w + 4) / 2, height - 29, (w - 4) / 2, () -> {
                if (!selected[s].isEmpty()) { insert.accept(selected[s]); minecraft.setScreen(parent); }
            });
            place.active = !selected[s].isEmpty();
            targetScroll[s] = Math.clamp(targetScroll[s], 0, maxScroll(s));
            scroll[s] = Math.clamp(scroll[s], 0, maxScroll(s));
        }
        var close = addRenderableWidget(new StyledButton(width - 32, 7, 20, 20, Component.literal("\u00d7"),
                ignored -> onClose(), styles, "variable-close", "toolbar-button"));
        close.setTooltip(Tooltip.create(text("close")));
    }
    private StyledButton action(String key, int x, int y, int w, Runnable action) {
        var button = addRenderableWidget(new StyledButton(x, y, w, 20, text(key), ignored -> action.run(),
                styles, "variable-" + key, "toolbar-button"));
        button.setTooltip(Tooltip.create(text(key)));
        return button;
    }
    private void openEdit(String scope, QuestVariable existing) {
        minecraft.setScreen(new QuestVariableEditScreen(this, taskId, scope, existing, (oldId, newId) -> {
            changed.accept(oldId, newId);
            for (int side = 0; side < 2; side++) if (selected[side].equals(oldId)) selected[side] = "";
            selected[newId.startsWith("globe.") ? 0 : 1] = newId;
        }));
    }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        backdrop(g, 0xFF1D2228);
        g.drawString(font, title, 12, 12, 0xFFF1D8A3, false);
        long now = System.nanoTime();
        double blend = lastFrame == 0 ? 1 : 1 - Math.exp(-18 * Math.min(.1, (now - lastFrame) / 1e9));
        lastFrame = now;
        QuestVariable hovered = null;
        for (int side = 0; side < 2; side++) {
            int x = left(side), w = paneWidth(side);
            scroll[side] += (targetScroll[side] - scroll[side]) * blend;
            int first = (int) scroll[side] / 24, offset = (int) scroll[side] % 24;
            var entries = entries(side);
            g.fill(x, 116, x + w, height - 42, 0xFF141A1F);
            g.enableScissor(x, 117, x + w, height - 43);
            for (int row = 0; row <= rows() + 1 && row + first < entries.size(); row++) {
                QuestVariable variable = entries.get(row + first);
                int y = 120 + row * 24 - offset;
                boolean over = mouseX >= x && mouseX < trackX(side)
                        && mouseY >= Math.max(120, y) && mouseY < Math.min(listBottom(), y + 22);
                g.fill(x + 3, y, x + w - 3, y + 22,
                        variable.id().equals(selected[side]) ? 0xFF526D72 : over ? 0xFF39464A : 0xFF293238);
                g.drawString(font, font.plainSubstrByWidth(variable.name(), w - 18),
                        x + 8, y + 6, 0xFFE7ECEC);
                if (over) hovered = variable;
            }
            g.disableScissor();
            if (maxScroll(side) > 0) {
                g.fill(trackX(side), 120, trackX(side) + 4, listBottom(), 0xFF303741);
                g.fill(trackX(side), (int) thumbY(side), trackX(side) + 4,
                        (int) thumbY(side) + thumbHeight(side), 0xFF9DB4D2);
            }
        }
        super.renderContents(g, mouseX, mouseY, delta);
        if (hovered != null && draggingSide < 0) {
            var details = new java.util.ArrayList<Component>();
            details.add(Component.literal(hovered.id()));
            details.add(text("initial").copy().append(": " + hovered.initial()));
            details.add(text("type").copy().append(": ").append(text("type." + hovered.type())));
            details.add(text(hovered.teamShared() ? "sharing.team" : "sharing.player"));
            String liveValue = ClientControlState.variable(hovered.id());
            if (liveValue == null && hovered.readOnly()) {
                var progress = ClientQuestProgress.view(hovered.scope());
                if (progress != null) {
                    liveValue = switch (hovered.name()) {
                        case "progress_current" -> Integer.toString(progress.current());
                        case "progress_required" -> Integer.toString(progress.required());
                        case "progress_ratio" -> progress.required() <= 0 ? "0"
                                : Double.toString(progress.current() / (double) progress.required());
                        default -> null;
                    };
                }
            }
            if (liveValue != null) details.add(text("current_value").copy().append(": " + liveValue));
            if (hovered.readOnly()) details.add(text("progress_readonly"));
            g.renderTooltip(font, details, java.util.Optional.empty(), mouseX, mouseY);
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 && button != 1 || y < 120 || y >= listBottom()) return false;
        for (int side = 0; side < 2; side++) {
            int left = left(side);
            if (x < left || x >= left + paneWidth(side)) continue;
            if (button == 0 && x >= trackX(side) - 2 && maxScroll(side) > 0) {
                draggingSide = side;
                draggingThumb = true;
                thumbOffset = y >= thumbY(side) && y < thumbY(side) + thumbHeight(side)
                        ? y - thumbY(side) : thumbHeight(side) / 2.0;
                dragThumb(y);
                return true;
            }
            int index = (int) ((scroll[side] + y - 120) / 24);
            var entries = entries(side);
            if (index >= 0 && index < entries.size()) {
                if (button == 1) {
                    minecraft.keyboardHandler.setClipboard(entries.get(index).id());
                    return true;
                }
                selected[side] = entries.get(index).id();
                draggingSide = side;
                draggingThumb = false;
                dragStartY = y;
                dragStartScroll = scroll[side];
                init();
            }
            return true;
        }
        return false;
    }
    private void dragThumb(double y) {
        int side = draggingSide;
        scroll[side] = targetScroll[side] = Math.clamp((y - 120 - thumbOffset) * maxScroll(side)
                / Math.max(1, listBottom() - 120 - thumbHeight(side)), 0, maxScroll(side));
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingSide >= 0) {
            if (draggingThumb) dragThumb(y);
            else scroll[draggingSide] = targetScroll[draggingSide] = Math.clamp(
                    dragStartScroll + dragStartY - y, 0, maxScroll(draggingSide));
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingSide >= 0) { draggingSide = -1; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= 116 && y < height - 42) for (int side = 0; side < 2; side++) {
            if (x < left(side) || x >= left(side) + paneWidth(side)) continue;
            targetScroll[side] = Math.clamp(targetScroll[side] - vertical * 48, 0, maxScroll(side));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void onClose() { closeTo(parent); }
}
