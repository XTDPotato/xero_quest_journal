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
    private final UiMotion bookOpen = new UiMotion(false, System.nanoTime());
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

    private int fixedSlots() {
        return 10;
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
        int animation = ClientPreferences.get().reducedMotion ? 0 : 280;
        bookOpen.target(true, System.nanoTime(), animation);
        bookOpen.advance(System.nanoTime(), animation);
        g.pose().pushPose();
        g.pose().translate(left + widthPage / 2F, top + heightPage / 2F, 0);
        float scale = .08F + .92F * bookOpen.eased();
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-(left + widthPage / 2F), -(top + heightPage / 2F), 0);
        drawBook(g);
        drawStickers(g, mx, my);
        drawSelected(g);
        JournalNavButton.drawIcon(g, "close", left + widthPage - 22, top - 18, 22, 0xFFF3E4BF);
        if (mx >= left + widthPage - 22 && mx < left + widthPage && my >= top - 18 && my < top + 4)
            g.renderOutline(left + widthPage - 24, top - 20, 26, 26, 0xFFEAC477);
        g.pose().popPose();
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
        g.fillGradient(0, 0, width, height, 0xD92D405A, 0xE71A2638);
        for (int y = 12; y < height; y += 38)
            for (int x = (y / 38 % 2) * 19; x < width; x += 38)
                if ((x / 19 + y / 19) % 5 == 0) g.fill(x, y, x + 2, y + 2, 0x12F5DFAE);
    }

    private void drawBook(GuiGraphics g) {
        AnecdoteBookArt.spread(g, left, top, widthPage, heightPage);
        AnecdoteBookArt.ribbon(g, left + 22, top - 19, Math.max(88, Math.min(widthPage / 2 - 40, 248)),
                text("rewards").getString());
        centered(g, text("rewards.record_heading"), rightLeft + (widthPage / 2 - 16) / 2,
                top + 24, 0xFF55586A);
        int half = widthPage / 2;
        for (int x = left + 24; x < left + half - 18; x += 8)
            g.fill(x, top + 59, x + 4, top + 60, 0x66D6C294);
        for (int y = top + 74; y < top + heightPage - 44; y += 8)
            g.fill(left + half - 18, y, left + half - 17, y + 4, 0x66D6C294);
    }

    private void drawStickers(GuiGraphics g, int mx, int my) {
        int gap = 10, step = tile + gap, half = widthPage / 2;
        int rowHeight = tile + 24;
        int rows = (Math.max(fixedSlots(), visible.size()) + columns - 1) / columns;
        int maxScroll = Math.max(0, rows * rowHeight - (gridBottom - gridTop));
        stickerScroll.limit(maxScroll);
        stickerScroll.advance();
        var hitBounds = new java.util.ArrayList<Rect>(visible.size());
        var unreadKeys = java.util.Set.copyOf(AnecdoteClientState.snapshot().newCompletions());
        int entering = 0;
        g.enableScissor(left + 8, gridTop, left + half - 8, gridBottom);
        long elapsed = Math.max(0, System.nanoTime() - openedAt);
        int slots = Math.max(fixedSlots(), visible.size());
        for (int index = 0; index < slots; index++) {
            int col = index % columns, row = index / columns;
            int x = gridLeft + col * step;
            int y = gridTop + row * rowHeight - stickerScroll.pixels();
            int stickerX = x, stickerY = y;
            Rect rect = new Rect(stickerX, stickerY, tile, tile + 20);
            boolean occupied = index < visible.size();
            if (occupied) hitBounds.add(rect);
            String id = occupied ? key(visible.get(index)) : "";
            int revealOrder = occupied && unreadKeys.contains(id) ? entering++ : -1;
            if (rect.y() + rect.h() < gridTop || rect.y() > gridBottom) continue;
            boolean hover = !occupied && mx >= stickerX && mx < stickerX + tile
                    && my >= stickerY && my < stickerY + tile;
            boolean held = occupied && id.equals(pressedKey);
            float enter = revealOrder >= 0
                    ? Math.clamp((elapsed - revealOrder * STAGGER_NANOS) / (float) STICKER_NANOS, 0, 1) : 1;
            float arrivalScale = revealOrder >= 0 ? 1.35F - .35F * easeOutBack(enter) : 1F;
            float scale = (held ? .95F : hover ? 1.05F : 1F) * arrivalScale;
            int centerX = stickerX + tile / 2, centerY = stickerY + tile / 2;
            g.pose().pushPose();
            g.pose().translate(centerX, centerY, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-centerX, -centerY, 0);
            if (occupied) drawFilledSticker(g, visible.get(index), stickerX, stickerY, tile,
                    centerX, held, hover, enter);
            else drawEmptySticker(g, stickerX, stickerY, tile, centerX, hover);
            g.pose().popPose();
        }
        g.disableScissor();
        bounds = List.copyOf(hitBounds);
        if (visible.isEmpty())
            centered(g, text("no_completed"), left + widthPage / 4, gridTop + tile + 30, 0xFF8B806A);
        long animationComplete = STICKER_NANOS + Math.max(0, entering - 1) * STAGGER_NANOS;
        if (entering > 0 && elapsed >= animationComplete && !acknowledged) {
            acknowledged = true;
            AnecdoteClientState.markRewardsRead();
        }
    }

    private final SmoothScroll stickerScroll = new SmoothScroll();

    private void drawFilledSticker(GuiGraphics g, AnecdoteSnapshot.Completed completion,
                                   int x, int y, int size, int centerX, boolean held, boolean hover, float enter) {
        int paper = held ? 0xFFFFE4A8 : hover ? 0xFFFFF5DB : 0xFFFFF8E9;
        int edge = key(completion).equals(selectedKey) ? 0xFFE6A54A : 0xFFCBBB99;
        g.fill(x + 3, y + 3, x + size - 3, y + size - 2, 0x1F806344);
        g.fill(x + 2, y + 1, x + size - 2, y + size - 5, paper);
        g.renderOutline(x + 2, y + 1, size - 4, size - 6, edge);
        for (int px = x + 7; px < x + size - 7; px += 7)
            g.fill(px, y + 4, px + 1, y + size - 9, 0x0FC6A568);
        var entry = findEntry(completion.entryId());
        var record = findRecord(entry, completion.recordId());
        String icon = record == null || record.completedIcon().isBlank()
                ? entry == null ? "" : entry.icon() : record.completedIcon();
        if (!icon.isBlank()) QuestIcons.render(g, icon, x + 7, y + 6, size - 14, size - 24, enter);
        String caption = entry == null ? completion.entryId() : entry.cardText();
        float captionScale = Math.min(1, (size - 8F) / Math.max(1, font.width(caption)));
        MailDecor.centered(g, font, font.plainSubstrByWidth(caption,
                        Math.max(1, (int) ((size - 8) / captionScale))),
                centerX, y + size - 13, captionScale, 0xFF596071);
    }

    private void drawEmptySticker(GuiGraphics g, int x, int y, int size, int centerX, boolean hover) {
        g.fill(x + 2, y + 1, x + size - 2, y + size - 5, hover ? 0xFFFFF5DB : 0xFFF8F1E5);
        g.renderOutline(x + 2, y + 1, size - 4, size - 6, 0xFFD6C49F);
        drawHeart(g, centerX, y + size / 2 - 2, Math.max(2, size / 30), 0x66DEBD83);
    }

    private static void drawHeart(GuiGraphics g, int center, int y, int scale, int color) {
        int[][] rows = {{-3, -2, 2, 3}, {-4, -3, 3, 4}, {-4, -3, 3, 4},
                {-3, -2, 2, 3}, {-2, -1, 1, 2}, {-1, 0, 0, 1}};
        for (int row = 0; row < rows.length; row++)
            g.fill(center + rows[row][0] * scale, y + row * scale,
                    center + rows[row][3] * scale, y + (row + 1) * scale, color);
    }

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
        centered(g, text("rewards.progress_heading"), center, top + 59, 0xFF927447);
        int progress = visible.size() % 10;
        if (progress == 0 && !visible.isEmpty()) progress = 10;
        int badgeSize = Math.max(80, Math.min(118, widthPage / 9));
        int badgeX = center - badgeSize / 2, badgeY = top + 78;
        MailDecor.circle(g, badgeX, badgeY, badgeSize, 0xFFE7D3A8);
        MailDecor.circle(g, badgeX + 5, badgeY + 5, badgeSize - 10, 0xFFFFF9EA);
        g.renderOutline(badgeX + 7, badgeY + 7, badgeSize - 14, badgeSize - 14, 0xFFC9A564);
        centered(g, Component.literal(progress + "/10"), center,
                badgeY + badgeSize / 2 - font.lineHeight / 2, 0xFF9C6C22);
        for (int side : new int[]{-1, 1}) {
            int ribbonX = side < 0 ? center - badgeSize / 2 - 25 : center + badgeSize / 2 + 5;
            g.fill(ribbonX, badgeY + badgeSize / 2, ribbonX + 20, badgeY + badgeSize / 2 + 5, 0xFFD1AE6B);
            g.fill(ribbonX + (side < 0 ? 0 : 15), badgeY + badgeSize / 2 + 5,
                    ribbonX + (side < 0 ? 5 : 20), badgeY + badgeSize / 2 + 12, 0xFFB98E4F);
        }
        int descriptionY = badgeY + badgeSize + 20;
        for (var line : font.split(text("rewards.progress_description", progress), panelWidth)) {
            g.drawString(font, line, rightLeft + 22, descriptionY, 0xFF626272, false);
            descriptionY += font.lineHeight + 4;
        }
        int rewardY = top + heightPage - 116;
        int titleY = descriptionY + 8;
        centered(g, text("recent_completion"), center, titleY, 0xFF927447);
        titleY += font.lineHeight + 8;
        centered(g, Component.literal(entry == null ? completion.entryId() : entry.title()),
                center, titleY, 0xFF4D5367);
        int textY = titleY + font.lineHeight + 7;
        if (record != null) {
            centered(g, Component.literal(record.title()), center, textY, 0xFF736346);
            textY += font.lineHeight + 6;
            g.enableScissor(rightLeft + 16, textY, rightLeft + widthPage / 2 - 16, rewardY - 10);
            for (var line : font.split(Component.literal(record.body()), panelWidth)) {
                g.drawString(font, line, rightLeft + 24, textY, 0xFF596071, false);
                textY += font.lineHeight + 3;
            }
            g.disableScissor();
        }
        int rewardX = rightLeft + 18, rewardW = widthPage / 2 - 36;
        g.fill(rewardX, rewardY, rewardX + rewardW, rewardY + 72, 0xFFFFF8E8);
        g.renderOutline(rewardX, rewardY, rewardW, 72, 0xFFE3C98D);
        g.drawString(font, text("reward_label"), rewardX + 10, rewardY + 7, 0xFF927447, false);
        g.fill(rewardX + rewardW - 56, rewardY + 3, rewardX + rewardW - 3, rewardY + 69, 0x1FD3B976);
        if (entry != null && !entry.rewards().isEmpty()) {
            int itemX = rewardX + 10;
            for (var reward : entry.rewards()) {
                RewardCardRenderer.render(g, font, QuestRewardsScreen.icon(reward.type(), reward.item()),
                        reward.count(), reward.quality(), reward.background(), itemX, rewardY + 22,
                        .78F, 1, false, false);
                itemX += 31;
                if (itemX > rewardX + rewardW - 58) break;
            }
        } else drawGift(g, rewardX + rewardW - 30, rewardY + 36, 0xFFB8B1A1);
    }

    private void drawGift(GuiGraphics g, int centerX, int centerY, int color) {
        int s = 2;
        g.fill(centerX - 8 * s, centerY - 2 * s, centerX + 8 * s, centerY + 6 * s, color);
        g.fill(centerX - 9 * s, centerY - 5 * s, centerX + 9 * s, centerY - 2 * s, color);
        g.fill(centerX - 1 * s, centerY - 5 * s, centerX + 1 * s, centerY + 6 * s, 0xFFEAE0C9);
        g.fill(centerX - 6 * s, centerY - 8 * s, centerX - 2 * s, centerY - 5 * s, color);
        g.fill(centerX + 2 * s, centerY - 8 * s, centerX + 6 * s, centerY - 5 * s, color);
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
                && button == 0) {
            closeBookTo(parent);
            return true;
        }
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
