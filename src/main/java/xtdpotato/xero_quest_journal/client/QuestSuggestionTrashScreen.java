package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bulk-removal staging screen; entries are hidden only after the confirmation dialog succeeds. */
public final class QuestSuggestionTrashScreen extends AnimatedQuestScreen {
    private static final int ROW_HEIGHT = 28;
    private final Screen parent;
    private final QuestValuePickerScreen picker;
    private final String caption;
    private final String context;
    private final List<String> candidates;
    private final Runnable refreshed;
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    private final SmoothScroll scroll = new SmoothScroll();
    private final ButtonStyleSheet styles = ButtonStyleSheet.load();
    private EditBox search;
    private String query = "";
    private String error = "";
    private boolean scrollbarDragging;
    private double scrollbarGrab;

    QuestSuggestionTrashScreen(QuestValuePickerScreen parent, String caption, String context,
                               List<String> candidates, Runnable refreshed) {
        super(text("title"));
        this.parent = parent;
        this.picker = parent;
        this.caption = caption;
        this.context = context;
        this.candidates = List.copyOf(candidates);
        this.refreshed = refreshed;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.value_picker.trash." + key, args);
    }
    private int left() { return 24; }
    private int right() { return width - 24; }
    private int listTop() { return 106; }
    private int listBottom() { return height - 54; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }
    private int maxScroll() { return Math.max(0, filtered().size() - visibleRows()); }
    private int trackX() { return right() - 7; }
    private List<String> filtered() {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return candidates;
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(normalized)
                || QuestIcons.searchIndex(value, value).contains(normalized)).toList();
    }
    private int thumbHeight() {
        return Math.max(14, (listBottom() - listTop()) * visibleRows() / Math.max(1, filtered().size()));
    }
    private int thumbTop() {
        return listTop() + (int) Math.round((listBottom() - listTop() - thumbHeight())
                * scroll.position() / Math.max(1, scroll.maximum()));
    }

    @Override protected void init() {
        clearWidgets();
        int x = left(), w = right() - left();
        search = new EditBox(font, x, 48, w, 22, text("search"));
        search.setMaxLength(256);
        search.setValue(query);
        search.setHint(text("search"));
        search.setResponder(value -> {
            query = value;
            scroll.jump(0);
        });
        addRenderableWidget(search);
        search.setFocused(true);
        addRenderableWidget(new StyledButton(x, 78, 92, 20, text("select_all"),
                ignored -> {
                    selected.clear();
                    selected.addAll(filtered());
                    updateDeleteControl();
                }, styles, "trash-select-all", "toolbar-button"));
        addRenderableWidget(new StyledButton(x + 98, 78, 92, 20, text("invert"),
                ignored -> filtered().forEach(value -> {
                    if (!selected.remove(value)) selected.add(value);
                    updateDeleteControl();
                }), styles, "trash-invert", "toolbar-button"));
        var remove = new StyledButton(right() - 144, height - 36, 144, 24,
                text("delete", selected.size()), ignored -> confirmDelete(), styles,
                "trash-delete", "toolbar-button");
        remove.leadingIcon("xero_quest_journal:textures/gui/navigation/delete.png");
        remove.active = !selected.isEmpty();
        addRenderableWidget(remove);
        addRenderableWidget(new StyledButton(right() - 244, height - 36, 92, 24,
                Component.translatable("gui.cancel"), ignored -> closeTo(parent),
                styles, "trash-cancel", "toolbar-button"));
    }

    private void updateDeleteControl() {
        children().stream().filter(StyledButton.class::isInstance).map(StyledButton.class::cast)
                .filter(widget -> widget.styleId().equals("trash-delete")).findFirst().ifPresent(widget -> {
                    widget.setMessage(text("delete", selected.size()));
                    widget.active = !selected.isEmpty();
                });
    }

    private void confirmDelete() {
        if (selected.isEmpty()) return;
        Set<String> deleting = Set.copyOf(selected);
        minecraft.setScreen(new QuestConfirmScreen(this,
                text("confirm", deleting.size()), text("delete_confirm"), () -> {
            if (QuestSuggestionOverrides.removeAll(context, deleting)) {
                refreshed.run();
                minecraft.setScreen(picker);
            } else {
                error = text("save_failed").getString();
                minecraft.setScreen(this);
                init();
            }
        }));
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        scroll.limit(maxScroll());
        scroll.advance();
        backdrop(g, 0xF0181C22);
        int x = left(), r = right(), top = listTop(), bottom = listBottom();
        g.fill(0, 0, width, 38, 0xFF252C34);
        g.drawString(font, title, x, 14, 0xFFF1D49A, false);
        g.drawString(font, text("source", caption), x, 31, 0xFFB6C0CD, false);
        g.fill(x, top, r - (scroll.maximum() > 0 ? 10 : 0), bottom, 0xFF1A2027);
        g.enableScissor(x, top, r - 8, bottom);
        List<String> values = filtered();
        int first = scroll.pixels() / ROW_HEIGHT;
        int offset = scroll.pixels() % ROW_HEIGHT;
        for (int row = 0; row <= visibleRows() && first + row < values.size(); row++) {
            String value = values.get(first + row);
            int y = top + row * ROW_HEIGHT - offset;
            boolean hovered = mouseX >= x && mouseX < r - 8 && mouseY >= y && mouseY < y + ROW_HEIGHT;
            boolean checked = selected.contains(value);
            if (hovered) g.fill(x, y, r - 8, y + ROW_HEIGHT - 1, 0xFF303A46);
            int cx = x + 10, cy = y + 7;
            g.fill(cx, cy, cx + 14, cy + 14, 0xFF12171D);
            g.renderOutline(cx, cy, 14, 14, checked ? 0xFF9FC8BA : 0xFF758392);
            if (checked) {
                g.fill(cx + 3, cy + 7, cx + 6, cy + 10, 0xFF9FC8BA);
                g.fill(cx + 6, cy + 9, cx + 12, cy + 4, 0xFF9FC8BA);
            }
            g.drawString(font, font.plainSubstrByWidth(value, r - x - 62), x + 34,
                    y + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFE2E7EC, false);
            g.fill(x + 8, y + ROW_HEIGHT - 1, r - 8, y + ROW_HEIGHT, 0xFF333C46);
        }
        g.disableScissor();
        if (values.isEmpty())
            g.drawCenteredString(font, text("empty"), width / 2, top + 12, 0xFFABB8C3);
        if (scroll.maximum() > 0) {
            g.fill(trackX(), top, trackX() + 4, bottom, 0xFF303741);
            g.fill(trackX(), thumbTop(), trackX() + 4, thumbTop() + thumbHeight(), 0xFF9DB4D2);
        }
        g.fill(0, height - 45, width, height, 0xFF252C34);
        g.drawString(font, text("selected", selected.size()), left(), height - 26, 0xFF9FC8BA, false);
        if (!error.isBlank()) g.drawString(font, error, width / 2, height - 26, 0xFFFF9B8F, false);
        super.renderContents(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (inputBlocked()) return true;
        if (button == 0 && scroll.maximum() > 0 && x >= trackX() - 2 && x < right()
                && y >= listTop() && y < listBottom()) {
            scrollbarDragging = true;
            scrollbarGrab = y >= thumbTop() && y < thumbTop() + thumbHeight()
                    ? y - thumbTop() : thumbHeight() / 2.0;
            dragThumb(y);
            return true;
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (button == 0 && x >= left() && x < right() - 8 && y >= listTop() && y < listBottom()) {
            int index = (scroll.pixels() + (int) (y - listTop())) / ROW_HEIGHT;
            List<String> values = filtered();
            if (index >= 0 && index < values.size()) {
                String value = values.get(index);
                if (!selected.remove(value)) selected.add(value);
                updateDeleteControl();
            }
            return true;
        }
        return true;
    }

    private void dragThumb(double y) {
        double travel = Math.max(1, listBottom() - listTop() - thumbHeight());
        scroll.jump((y - listTop() - scrollbarGrab) * scroll.maximum() / travel);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && scrollbarDragging) { dragThumb(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && scrollbarDragging) { scrollbarDragging = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (inputBlocked()) return true;
        if (x >= left() && x < right() && y >= listTop() && y < listBottom()) {
            scroll.wheel(vertical * 30);
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (inputBlocked()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE) { closeTo(parent); return true; }
        if (key == GLFW.GLFW_KEY_HOME) { scroll.to(0); return true; }
        if (key == GLFW.GLFW_KEY_END) { scroll.to(scroll.maximum()); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { closeTo(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
