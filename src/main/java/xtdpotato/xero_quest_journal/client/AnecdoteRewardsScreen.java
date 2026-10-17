package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import xtdpotato.xero_quest_journal.core.model.AnecdoteDefinition;
import xtdpotato.xero_quest_journal.core.model.AnecdoteSnapshot;

import java.util.List;

/** Recent completions become permanent chronicle stickers; unread stickers enter one at a time. */
public final class AnecdoteRewardsScreen extends AnimatedQuestScreen {
    private static final long STAGGER_NANOS = 95_000_000L;
    private static final long STICKER_NANOS = 420_000_000L;
    private final Screen parent;
    private final long openedAt = System.nanoTime();
    private String selectedKey = "";
    private String pressedKey = "";
    private boolean acknowledged;
    private int left, top, widthPage, heightPage, gridLeft, gridTop, gridBottom, tile, columns, rightLeft;
    private List<AnecdoteSnapshot.Completed> visible = List.of();
    private List<Rect> bounds = List.of();

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    public AnecdoteRewardsScreen(Screen parent) {
        super(Component.translatable("screen.xero_quest_journal.anecdote.rewards"));
        this.parent = parent;
        visible = AnecdoteClientState.snapshot().completed();
        selectedKey = visible.stream().findFirst().map(AnecdoteRewardsScreen::key).orElse("");
    }

    private static String key(AnecdoteSnapshot.Completed value) {
        return value.entryId() + "/" + value.recordId();
    }

    private Component text(String key, Object... args) {
        return Component.translatable("screen.xero_quest_journal.anecdote." + key, args);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float delta) {
        if (widthPage == 0 || width != lastWidth || height != lastHeight) layout();
        lastWidth = width;
        lastHeight = height;
        visible = AnecdoteClientState.snapshot().completed();
        if (visible.stream().noneMatch(value -> key(value).equals(selectedKey)))
            selectedKey = visible.stream().findFirst().map(AnecdoteRewardsScreen::key).orElse("");
        drawBackdrop(g);
        drawBook(g);
        drawStickers(g, mx, my);
        drawSelected(g);
        JournalNavButton.drawIcon(g, "mail_close", left + widthPage - 22, top - 18, 22, 0xFFF3E4BF);
        if (mx >= left + widthPage - 22 && mx < left + widthPage && my >= top - 18 && my < top + 4)
            g.renderOutline(left + widthPage - 24, top - 20, 26, 26, 0xFFEAC477);
        super.renderContents(g, mx, my, delta);
    }

    private int lastWidth = -1, lastHeight = -1;

    private void layout() {
        widthPage = Math.min(width - 36, Math.max(340, Math.round(width * .84F)));
        heightPage = Math.min(height - 42, Math.max(240, Math.round(height * .78F)));
        left = (width - widthPage) / 2;
        top = (height - heightPage) / 2 + 8;
        int half = widthPage / 2;
        gridLeft = left + 28;
        gridTop = top + 66;
        gridBottom = top + heightPage - 22;
        rightLeft = left + half + 8;
        columns = widthPage < 560 ? 2 : 4;
        int gap = 10;
        int cellWidth = (half - 52 - (columns - 1) * gap) / columns;
        tile = Math.max(34, Math.min(cellWidth, Math.round(cellWidth * .88F)));
    }

    private void drawBackdrop(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xB51A2B43);
    }

    private void drawBook(GuiGraphics g) {
        g.fill(left - 6, top - 5, left + widthPage + 6, top + heightPage + 8, 0xFF354A68);
        g.fill(left - 3, top - 3, left + widthPage + 3, top + heightPage + 5, 0xFFB9A77E);
        g.fill(left, top, left + widthPage / 2, top + heightPage, 0xFFF2E5CA);
        g.fill(left + widthPage / 2 + 4, top, left + widthPage, top + heightPage, 0xFFF7F3EB);
        g.renderOutline(left - 3, top - 3, widthPage + 6, heightPage + 8, 0xFFD9C795);
        g.fill(left + widthPage / 2 - 2, top + 2, left + widthPage / 2 + 6, top + heightPage - 2, 0x33534A39);
        g.fill(left + 26, top + 24, left + widthPage - 26, top + 54, 0xFFFFEBC6);
        MailDecor.frame(g, left + 18, top + 32, Math.min(widthPage / 2 - 38, 250), 42, true);
        centered(g, text("rewards"), left + 18 + Math.min(widthPage / 2 - 38, 250) / 2,
                top + 46, 0xFF765221);
        centered(g, text("completed_count", visible.size()), rightLeft + (widthPage / 2 - 16) / 2,
                top + 28, 0xFF56586C);
    }

    private void drawStickers(GuiGraphics g, int mx, int my) {
        int gap = 10, step = tile + gap, half = widthPage / 2;
        int rowHeight = tile + 24;
        int rows = (visible.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, rows * rowHeight - (gridBottom - gridTop));
        stickerScroll.limit(maxScroll);
        stickerScroll.advance();
        var hitBounds = new java.util.ArrayList<Rect>(visible.size());
        var unreadKeys = java.util.Set.copyOf(AnecdoteClientState.snapshot().newCompletions());
        int entering = 0;
        g.enableScissor(left + 8, gridTop, left + half - 8, gridBottom);
        long elapsed = Math.max(0, System.nanoTime() - openedAt);
        for (int index = 0; index < visible.size(); index++) {
            var completion = visible.get(index);
            int col = index % columns, row = index / columns;
            int x = gridLeft + col * step;
            int y = gridTop + row * rowHeight - stickerScroll.pixels();
            int stickerX = x, stickerY = y;
            Rect rect = new Rect(stickerX, stickerY, tile, tile + 20);
            hitBounds.add(rect);
            String id = key(completion);
            int revealOrder = unreadKeys.contains(id) ? entering++ : -1;
            if (rect.y() + rect.h() < gridTop || rect.y() > gridBottom) continue;
            boolean hover = rect.contains(mx, my);
            boolean held = id.equals(pressedKey);
            float enter = revealOrder >= 0
                    ? Math.clamp((elapsed - revealOrder * STAGGER_NANOS) / (float) STICKER_NANOS, 0, 1) : 1;
            float arrivalScale = revealOrder >= 0 ? 1.35F - .35F * easeOutBack(enter) : 1F;
            float scale = (held ? .95F : hover ? 1.05F : 1F) * arrivalScale;
            int centerX = stickerX + tile / 2, centerY = stickerY + tile / 2;
            g.pose().pushPose();
            g.pose().translate(centerX, centerY, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-centerX, -centerY, 0);
            int color = id.equals(selectedKey) ? 0xFFE6A54A : 0xFFCBBB99;
            MailDecor.circle(g, stickerX + 2, stickerY + 2, tile - 4, id.equals(selectedKey)
                    ? 0xFFFFE8BA : 0xFFF9F1E2);
            MailDecor.circle(g, stickerX + 5, stickerY + 5, tile - 10, 0x00FFFFFF);
            g.renderOutline(stickerX + 1, stickerY + 1, tile - 2, tile - 2, color);
            var entry = findEntry(completion.entryId());
            var record = findRecord(entry, completion.recordId());
            String icon = record == null || record.completedIcon().isBlank()
                    ? entry == null ? "" : entry.icon() : record.completedIcon();
            if (!icon.isBlank()) QuestIcons.render(g, icon, stickerX + 8, stickerY + 8, tile - 16, tile - 16, enter);
            String caption = entry == null ? completion.entryId()
                    : entry.cardText();
            float captionScale = Math.min(1, (tile + 8F) / Math.max(1, font.width(caption)));
            MailDecor.centered(g, font, font.plainSubstrByWidth(caption,
                    Math.max(1, (int) ((tile + 8) / captionScale))),
                    centerX, stickerY + tile + 5, captionScale, 0xFF596071);
            g.pose().popPose();
        }
        g.disableScissor();
        bounds = List.copyOf(hitBounds);
        if (visible.isEmpty()) centered(g, text("no_completed"), left + widthPage / 4, gridTop + 42, 0xFF8B806A);
        long animationComplete = STICKER_NANOS + Math.max(0, entering - 1) * STAGGER_NANOS;
        if (entering > 0 && elapsed >= animationComplete && !acknowledged) {
            acknowledged = true;
            AnecdoteClientState.markRewardsRead();
        }
    }

    private final SmoothScroll stickerScroll = new SmoothScroll();

    private void drawSelected(GuiGraphics g) {
        var completion = visible.stream().filter(value -> key(value).equals(selectedKey)).findFirst().orElse(null);
        if (completion == null) {
            centered(g, text("no_selected_completion"), rightLeft + (widthPage / 2 - 22) / 2,
                    top + heightPage / 2, 0xFF8B806A);
            return;
        }
        var entry = findEntry(completion.entryId());
        var record = findRecord(entry, completion.recordId());
        int panelWidth = widthPage / 2 - 52;
        int center = rightLeft + (widthPage / 2 - 16) / 2;
        centered(g, text("recent_completion"), center, top + 74, 0xFF927447);
        centered(g, Component.literal(entry == null ? completion.entryId() : entry.title()),
                center, top + 101, 0xFF4D5367);
        if (record != null) {
            centered(g, Component.literal(record.title()), center, top + 128, 0xFF736346);
            int textY = top + 158;
            for (var line : font.split(Component.literal(record.body()), panelWidth)) {
                if (textY > top + heightPage - 40) break;
                g.drawString(font, line, rightLeft + 24, textY, 0xFF596071, false);
                textY += font.lineHeight + 3;
            }
        }
    }

    private AnecdoteDefinition findEntry(String id) {
        return AnecdoteClientState.snapshot().entries().stream().filter(entry -> entry.id().equals(id))
                .findFirst().orElse(null);
    }

    private static AnecdoteDefinition.Record findRecord(AnecdoteDefinition entry, String id) {
        return entry == null ? null : entry.records().stream().filter(record -> record.id().equals(id))
                .findFirst().orElse(null);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (x >= left + widthPage - 22 && x < left + widthPage && y >= top - 18 && y < top + 4
                && button == 0) { onClose(); return true; }
        if (button == 0) {
            for (int index = 0; index < bounds.size(); index++) if (bounds.get(index).contains(x, y)) {
                pressedKey = key(visible.get(index));
                return true;
            }
        }
        if (button == 1) {
            for (int index = 0; index < bounds.size(); index++) if (bounds.get(index).contains(x, y)) {
                selectedKey = key(visible.get(index));
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && !pressedKey.isBlank()) {
            String released = pressedKey;
            pressedKey = "";
            for (int index = 0; index < bounds.size(); index++)
                if (key(visible.get(index)).equals(released) && bounds.get(index).contains(x, y)) {
                    selectedKey = released;
                    return true;
                }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        stickerScroll.wheel(vertical * 32);
        return true;
    }

    private static float easeOutBack(float value) {
        float t = Math.clamp(value, 0, 1) - 1;
        float overshoot = 1.35F;
        return 1 + (overshoot + 1) * t * t * t + overshoot * t * t;
    }

    private void centered(GuiGraphics g, Component value, int centerX, int y, int color) {
        g.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    @Override public void onClose() { closeBookTo(parent); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
}
