package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class AnecdoteRecordsEditorScreen extends AnimatedQuestScreen {
    private final Screen parent;
    private final AnecdoteDefinition entry;
    private final Consumer<List<AnecdoteDefinition.Record>> changed;
    private final List<AnecdoteDefinition.Record> records = new ArrayList<>();
    private final SmoothScroll scroll = new SmoothScroll();
    private final List<int[]> rowBounds = new ArrayList<>();
    private int pressed = -1;

    AnecdoteRecordsEditorScreen(Screen parent, AnecdoteDefinition entry,
                                Consumer<List<AnecdoteDefinition.Record>> changed) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.records_title", entry.title()));
        this.parent = parent;
        this.entry = entry;
        this.changed = changed;
        records.addAll(entry.records());
    }

    @Override protected void init() {
        clearWidgets();
        int margin = Math.max(18, width / 18);
        int panelW = Math.min(width - margin * 2, 900);
        int left = (width - panelW) / 2;
        var close = new EditorIconButton(left + panelW - 38, 44,
                Component.translatable("screen.xero_quest_journal.close"),
                this::onClose, ButtonStyleSheet.load(), "anecdote-records-close", "close");
        close.setTooltip(net.minecraft.client.gui.components.Tooltip.create(close.getMessage()));
        addRenderableWidget(close);
    }

    private Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.anecdote." + key, args);
    }

    @Override protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xFF202A3B);
        int margin = Math.max(18, width / 18);
        int panelW = Math.min(width - margin * 2, 900);
        int left = (width - panelW) / 2;
        int top = 42, bottom = height - 48;
        g.fill(left, top, left + panelW, bottom, 0xFFF1E8D8);
        g.renderOutline(left, top, panelW, bottom - top, 0xFFC7B38C);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(24, panelW - 190)),
                left + 14, top + 12, 0xFF53586A, false);
        boolean editable = ClientEditMode.enabled() && minecraft.player != null && minecraft.player.hasPermissions(2);
        if (editable) drawToolbar(g, left + panelW - 166, top + 6, mx, my);
        int listTop = top + 42, listBottom = bottom - 12, rowH = 36, gap = 5;
        scroll.limit(Math.max(0, records.size() * (rowH + gap) - (listBottom - listTop)));
        scroll.advance();
        rowBounds.clear();
        g.enableScissor(left + 6, listTop, left + panelW - 6, listBottom);
        for (int index = 0; index < records.size(); index++) {
            var row = records.get(index);
            int y = listTop + index * (rowH + gap) - scroll.pixels();
            int[] bounds = {left + 12, y, panelW - 24, rowH};
            rowBounds.add(bounds);
            if (y + rowH < listTop || y > listBottom) continue;
            g.fill(bounds[0], y, bounds[0] + bounds[2], y + rowH, 0xFFFAF5EB);
            g.renderOutline(bounds[0], y, bounds[2], rowH, 0xFFD5C198);
            g.drawString(font, font.plainSubstrByWidth(row.title(), bounds[2] - 72),
                    bounds[0] + 10, y + 5, 0xFF4D5567, false);
            g.drawString(font, font.plainSubstrByWidth(row.body().replace('\n', ' '), bounds[2] - 72),
                    bounds[0] + 10, y + 20, 0xFF79766C, false);
            if (editable) g.drawString(font, "\u270e", bounds[0] + bounds[2] - 49, y + 11,
                    0xFF9A773F, false);
            if (editable) g.drawString(font, "\u00d7", bounds[0] + bounds[2] - 22, y + 9,
                    0xFF965B4B, false);
        }
        g.disableScissor();
        if (records.isEmpty()) centered(g, text("records_empty_author"), width / 2, height / 2, 0xFF89816F);
        super.renderContents(g, mx, my, delta);
    }

    private void drawToolbar(GuiGraphics g, int x, int y, int mx, int my) {
        g.fill(x, y, x + 122, y + 24, 0xFFE7D8B8);
        g.renderOutline(x, y, 122, 24, 0xFFC9A65F);
        g.drawString(font, text("record_add"), x + 12, y + 8, 0xFF625640, false);
    }

    private static void centered(GuiGraphics g, Component text, int center, int y, int color) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, text, center - font.width(text) / 2, y, color, false);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (button != 0 || !ClientEditMode.enabled() || minecraft.player == null
                || !minecraft.player.hasPermissions(2)) return false;
        int margin = Math.max(18, width / 18), panelW = Math.min(width - margin * 2, 900);
        int left = (width - panelW) / 2, top = 42;
        if (x >= left + panelW - 166 && x < left + panelW - 44 && y >= top + 6 && y < top + 30) {
            if (records.size() >= AnecdoteDefinition.MAX_RECORDS) return true;
            var fresh = new AnecdoteDefinition.Record(AnecdoteEntryEditorScreen.nextRecordId(entry, records),
                    "新记录", "");
            openRecord(fresh, true);
            return true;
        }
        for (int i = 0; i < rowBounds.size(); i++) {
            int[] b = rowBounds.get(i);
            if (x < b[0] || x >= b[0] + b[2] || y < b[1] || y >= b[1] + b[3]) continue;
            if (x >= b[0] + b[2] - 34) {
                int selected = i;
                minecraft.setScreen(new QuestConfirmScreen(this, text("record_delete_confirm"), () -> {
                    records.remove(selected);
                    changed.accept(List.copyOf(records));
                    minecraft.setScreen(this);
                }));
            } else openRecord(records.get(i), false);
            return true;
        }
        return true;
    }

    private void openRecord(AnecdoteDefinition.Record record, boolean creating) {
        minecraft.setScreen(new AnecdoteRecordEditorScreen(this, entry.withRecords(records), record, creating, updated -> {
            records.clear();
            records.addAll(updated.records());
            changed.accept(List.copyOf(records));
        }));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        scroll.wheel(vertical * 30);
        return true;
    }

    @Override public void onClose() { changed.accept(List.copyOf(records)); closeTo(parent); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}
