package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Latest-first item acquisition notices for a tracked item objective. */
public final class ItemPickupToast {
    private static final int MAX_VISIBLE = 4;
    private static final long LIFETIME = 4_200_000_000L;
    private static final long ENTER = 320_000_000L;
    private static final long SLOT_SHIFT = 220_000_000L;
    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private static long pausedAt;

    private static final class Entry {
        final QuestNetwork.ItemPickupNoticePayload notice;
        long startedAt;
        long slotChangedAt;
        int slot;
        int previousSlot;

        Entry(QuestNetwork.ItemPickupNoticePayload notice, long now) {
            this.notice = notice;
            startedAt = now;
            slotChangedAt = now;
        }
    }

    private ItemPickupToast() {}

    public static void enqueue(QuestNetwork.ItemPickupNoticePayload notice) {
        if (notice == null || notice.stack().isEmpty()) return;
        long now = System.nanoTime();
        var entry = new Entry(notice, now);
        entry.previousSlot = -1;
        entries.addFirst(entry);
        while (entries.size() > MAX_VISIBLE) entries.removeLast();
        updateSlots(now);
    }

    public static void tick() {
        Minecraft game = Minecraft.getInstance();
        long now = System.nanoTime();
        if (game.player == null || game.screen != null) {
            if (pausedAt == 0) pausedAt = now;
            return;
        }
        if (pausedAt != 0) {
            for (Entry entry : entries) {
                entry.startedAt += Math.max(0, now - Math.max(pausedAt, entry.startedAt));
                entry.slotChangedAt += Math.max(0, now - Math.max(pausedAt, entry.slotChangedAt));
            }
            pausedAt = 0;
        }
        entries.removeIf(entry -> now - entry.startedAt >= LIFETIME);
        updateSlots(now);
    }

    public static void clear() {
        entries.clear();
        pausedAt = 0;
    }

    public static int visibleCount() {
        return entries.size();
    }

    public static QuestNetwork.ItemPickupNoticePayload newest() {
        Entry entry = entries.peekFirst();
        return entry == null ? null : entry.notice;
    }

    private static void updateSlots(long now) {
        int slot = 0;
        for (Entry entry : entries) {
            if (entry.slot != slot) {
                entry.previousSlot = entry.slot;
                entry.slot = slot;
                entry.slotChangedAt = now;
            }
            slot++;
        }
    }

    public static void render(GuiGraphics graphics) {
        if (entries.isEmpty()) return;
        Minecraft game = Minecraft.getInstance();
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int screenHeight = game.getWindow().getGuiScaledHeight();
        int leftMargin = 10;
        var preferences = ClientPreferences.get();
        var geometry = NoticeGeometry.pickup(screenWidth, preferences.itemPickupToastScalePercent,
                preferences.itemPickupToastTextScalePercent, game.font);
        float groupScale = geometry.scale();
        int width = geometry.width();
        if (width * groupScale < 72) return;
        int rowHeight = geometry.rowHeight();
        int headerHeight = geometry.headerHeight();
        int renderedWidth = Math.round(width * groupScale);
        int desiredX = leftMargin + Math.round(Math.max(0, screenWidth - renderedWidth - leftMargin * 2)
                * ClientPreferences.get().itemPickupToastXPercent / 100F);
        int panelHeight = geometry.height();
        int renderedHeight = Math.round(panelHeight * groupScale);
        int desiredY = Math.round(Math.max(0, screenHeight - renderedHeight)
                * ClientPreferences.get().itemPickupToastYPercent / 100F);
        var placed = HudOverlayLayout.place(screenWidth, screenHeight,
                new HudOverlayLayout.Rect(desiredX, desiredY, renderedWidth, renderedHeight),
                HudOverlayLayout.cornerFromPercent(ClientPreferences.get().itemPickupToastXPercent,
                        ClientPreferences.get().itemPickupToastYPercent), 8);
        int left = placed.x();
        int panelTop = placed.y();
        graphics.pose().pushPose();
        graphics.pose().translate(left, panelTop, 0);
        graphics.pose().scale(groupScale, groupScale, 1);
        int rowsTop = headerHeight;
        long now = System.nanoTime();
        boolean reduced = ClientPreferences.get().reducedMotion;
        List<Entry> snapshot = new ArrayList<>(entries);

        float headerAlpha = 0;
        for (Entry entry : snapshot)
            headerAlpha = Math.max(headerAlpha, alpha(now - entry.startedAt, reduced));
        if (headerAlpha > 0) {
            float headerScale = geometry.textScale() * 1.1F;
            String action = snapshot.getFirst().notice.action();
            int headerWidth = Math.min(width, Math.max(150, Math.round(game.font.width(
                    Component.translatable("screen.xero_quest_journal.item_pickup." + (action.equals("submit") ? "submitted" : "title"))) * headerScale) + 20));
            int headerX = (width - headerWidth) / 2;
            int headerY = 0;
            graphics.fillGradient(headerX, headerY, headerX + headerWidth, headerY + headerHeight - 5,
                    fade(0x684A78A2, headerAlpha), fade(0x52334F75, headerAlpha));
            Component title = Component.translatable("screen.xero_quest_journal.item_pickup." + (action.equals("submit") ? "submitted" : "title"));
            graphics.pose().pushPose();
            graphics.pose().translate(headerX + headerWidth / 2F, headerY + headerHeight / 2F, 0);
            graphics.pose().scale(headerScale, headerScale, 1);
            graphics.pose().translate(-(headerX + headerWidth / 2F), -(headerY + headerHeight / 2F), 0);
            graphics.drawCenteredString(game.font, title, headerX + headerWidth / 2, headerY + headerHeight / 2 - game.font.lineHeight / 2,
                    fade(0xFFF4F3F0, headerAlpha));
            graphics.pose().popPose();
        }

        for (Entry entry : snapshot) {
            long elapsed = now - entry.startedAt;
            float alpha = alpha(elapsed, reduced);
            if (alpha <= 0) continue;
            float enter = reduced ? 1 : smooth(Math.min(1, elapsed / (float) ENTER));
            float shift = reduced ? 1 : smooth(Math.min(1,
                    (now - entry.slotChangedAt) / (float) SLOT_SHIFT));
            int oldY = rowsTop + Math.max(0, entry.previousSlot) * rowHeight;
            int targetY = rowsTop + entry.slot * rowHeight;
            int y = Math.round(oldY + (targetY - oldY) * shift);
            int x = -Math.round((1 - enter) * 32);
            int rowRight = width;

            graphics.fillGradient(x, y, rowRight, y + rowHeight - 2,
                    fade(0x673A628C, alpha), fade(0x592E4E70, alpha));
            graphics.fill(x, y, x + 2, y + rowHeight - 2, fade(0x9AA8C5DE, alpha));
            graphics.fill(rowRight - 2, y + 1, rowRight, y + rowHeight - 3,
                    fade(0x554D7EAA, alpha));
            graphics.fill(x + 2, y + rowHeight - 3, rowRight - 2, y + rowHeight - 2,
                    fade(0x555B85AA, alpha));

            int progressLeft = x + 8;
            float requestedTextScale = geometry.textScale();
            String progressLabel = entry.notice.current() + "/" + entry.notice.required();
            int progressWidth = Math.min(width * 2 / 5, Math.max(Math.min(76, width / 3), Math.round(game.font.width(progressLabel) * requestedTextScale) + 10));
            int progressHeight = rowHeight - 10;
            int progressTop = y + 5;
            graphics.fillGradient(progressLeft, progressTop, progressLeft + progressWidth,
                    progressTop + progressHeight, fade(0x9B5E55BA, alpha), fade(0x864251A0, alpha));
            float progressScale = Math.min(requestedTextScale, (progressWidth - 8F) / Math.max(1, game.font.width(progressLabel)));
            graphics.pose().pushPose();
            graphics.pose().translate(progressLeft + progressWidth / 2F, progressTop + progressHeight / 2F, 0);
            graphics.pose().scale(progressScale, progressScale, 1);
            graphics.pose().translate(-(progressLeft + progressWidth / 2F),
                    -(progressTop + progressHeight / 2F), 0);
            graphics.drawCenteredString(game.font,
                    Component.literal(progressLabel), progressLeft + progressWidth / 2,
                    progressTop + progressHeight / 2 - game.font.lineHeight / 2, fade(0xFFFFFFFF, alpha));
            graphics.pose().popPose();

            int iconX = progressLeft + progressWidth + 6;
            int iconSize = 16;
            int iconY = y + (rowHeight - iconSize) / 2;
            ItemStack icon = entry.notice.stack().copy();
            icon.setCount(1);
            graphics.pose().pushPose();
            graphics.pose().translate(iconX + iconSize / 2F, iconY + iconSize / 2F, 0);
            graphics.pose().translate(-(iconX + iconSize / 2F), -(iconY + iconSize / 2F), 0);
            graphics.renderItem(icon, iconX, iconY);
            graphics.pose().popPose();
            Component itemName = Component.translatable("screen.xero_quest_journal.item_pickup.row." + entry.notice.action()).append(" ").append(icon.getHoverName());
            int textX = iconX + 22;
            int countWidth = game.font.width("x" + entry.notice.pickedCount());
            float textScale = Math.min(requestedTextScale,
                    Math.max(.25F, (rowRight - textX - 16F) / Math.max(1, countWidth + game.font.width("...") + 10)));
            int available = Math.max(1, (int) ((rowRight - textX - countWidth * textScale - 26) / textScale));
            Component name = Component.literal(game.font.plainSubstrByWidth(itemName.getString(), available));
            int textY = y + Math.round((rowHeight - game.font.lineHeight * textScale) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(textX, textY, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(game.font, name, 0, 0, fade(0xFFF2F0E9, alpha), true);
            graphics.pose().popPose();
            int countX = rowRight - Math.round(countWidth * textScale) - 16;
            graphics.pose().pushPose();
            graphics.pose().translate(countX, textY, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(game.font, "x" + entry.notice.pickedCount(), 0, 0,
                    fade(entry.notice.action().equals("submit") ? 0xFF9BE3B4 : 0xFFE4EAF0, alpha), true);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    private static float alpha(long elapsed, boolean reduced) {
        if (elapsed < 0 || elapsed >= LIFETIME) return 0;
        if (reduced) return elapsed < LIFETIME - 350_000_000L ? 1
                : 1 - (elapsed - (LIFETIME - 350_000_000L)) / 350_000_000F;
        if (elapsed < ENTER) return smooth(elapsed / (float) ENTER);
        long fadeStart = LIFETIME - 420_000_000L;
        if (elapsed < fadeStart) return 1;
        return 1 - Math.clamp((elapsed - fadeStart) / 420_000_000F, 0, 1);
    }

    private static float smooth(float value) {
        float t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int fade(int color, float alpha) {
        return color & 0x00FFFFFF | Math.clamp(Math.round((color >>> 24) * alpha), 0, 255) << 24;
    }
}
