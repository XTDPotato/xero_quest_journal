package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.network.QuestNetwork;

import java.util.ArrayDeque;
import java.util.Deque;

/** Top-center notification, separate from the bottom-center status tips. */
public final class MailArrivalToast {
    private static final Deque<QuestNetwork.MailboxNoticePayload> queue = new ArrayDeque<>();
    private static QuestNetwork.MailboxNoticePayload current;
    private static long started;
    private static int left, top, right, bottom;
    private static QuestNetwork.MailboxNoticePayload pressed;
    private static boolean pressedClose;

    private MailArrivalToast() {}

    public static void enqueue(QuestNetwork.MailboxNoticePayload notice) {
        if (queue.size() >= 4) queue.removeFirst();
        queue.addLast(notice);
        tick();
    }

    public static void clear() {
        queue.clear();
        current = null;
        pressed = null;
    }

    public static void tick() {
        long now = System.nanoTime();
        if (current != null && now - started > duration() * 1_000_000L) current = null;
        if (current == null && !queue.isEmpty()) {
            current = queue.removeFirst();
            started = now;
        }
    }

    public static boolean visible() { return current != null; }

    public static boolean click(double x, double y) {
        if (current == null || x < left || x >= right || y < top || y >= bottom) return false;
        pressed = current;
        pressedClose = showClose() && x >= right - (bottom - top);
        return true;
    }

    public static boolean release(double x, double y) {
        if (pressed == null) return false;
        var held = pressed; pressed = null;
        if (current == held && x >= left && x < right && y >= top && y < bottom
                && pressedClose == (showClose() && x >= right - (bottom - top))) {
            if (pressedClose) current = null;
            else open();
        }
        return true;
    }
    private static boolean showClose() {
        return Boolean.parseBoolean(MailboxAnimationStore.attribute("notice", "close", "false"));
    }

    public static void open() {
        Minecraft game = Minecraft.getInstance();
        if (game.player == null) return;
        String id = current == null ? "" : current.id();
        current = null;
        ClientMailbox.request();
        game.setScreen(new MailboxScreen(game.screen instanceof MailboxScreen ? null : game.screen, id));
    }

    public static void render(GuiGraphics graphics) {
        tick();
        Minecraft game = Minecraft.getInstance();
        if (current == null || game.player == null) return;
        int screenWidth = game.getWindow().getGuiScaledWidth();
        int availableWidth = Math.max(1, screenWidth - 24);
        int referenceWidth = Math.max(80, screenWidth / 3);
        int width = Math.min(Math.min(availableWidth, referenceWidth),
                Math.round(MailboxAnimationStore.scalar("notice", "width", 250, 80, 1600)));
        left = (screenWidth - width) / 2;
        right = left + width;
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        float enter = (float) Math.clamp(elapsed / .28, 0, 1);
        float exit = (float) Math.clamp((duration() / 1000.0 - elapsed) / .3, 0, 1);
        float opacity = Math.min(enter, exit);
        int height = Math.round(MailboxAnimationStore.scalar("notice", "height", 30, 18, 100));
        top = 12 - Math.round((1 - enter) * (height + 12));
        bottom = top + height;
        drawBanner(graphics, left, top, width, height, opacity);
    }

    private static int duration() { return Math.round(MailboxAnimationStore.scalar("notice", "duration", 6000, 1000, 30000)); }

    static void drawBanner(GuiGraphics graphics, int x, int y, int width, int height, float opacity) {
        var game = Minecraft.getInstance();
        graphics.fill(x, y, x + width, y + height, faded(
                MailboxAnimationStore.color("notice", "background", 0x801D2026), opacity));
        String background = MailboxAnimationStore.attribute("notice", "background", "");
        if (!background.isBlank()) ClientMediaManager.renderImageCover(graphics, background, x, y, width, height, opacity);
        int border = faded(MailboxAnimationStore.color("notice", "border", 0x800A0C10), opacity);
        graphics.renderOutline(x, y, width, height, border);
        int innerBorder = faded(MailboxAnimationStore.color("notice", "inner-border", 0x8080858D), opacity);
        if (height >= 24) graphics.renderOutline(x + 2, y + 2, width - 4, height - 4, innerBorder);
        if (opacity < .05f) return;
        int color = faded(MailboxAnimationStore.color("notice", "text", 0xFFF4F5F7), opacity);
        int iconColor = noticeIconColor(opacity);
        int iconSize = Math.max(12, Math.min(height - 6, 36));
        int inset = height <= 30 ? 6 : 12;
        JournalNavButton.drawIcon(graphics, MailboxAnimationStore.attribute("notice", "icon", "mail_envelope"),
                x + inset, y + (height - iconSize) / 2, iconSize, iconColor);
        if (showClose()) JournalNavButton.drawIcon(graphics, "mail_close",
                x + width - height + (height - iconSize) / 2, y + (height - iconSize) / 2, iconSize, iconColor);
        String label = MailboxAnimationStore.attribute("notice", "text", "");
        if (label.isEmpty()) label = Component.translatable("screen.xero_quest_journal.mailbox.notice").getString();
        int textLeft = x + inset + iconSize + 8;
        int textRight = x + width - (showClose() ? height : inset);
        int textWidth = Math.max(1, textRight - textLeft);
        float scale = Math.min(MailboxAnimationStore.scalar("notice", "text-scale", 1F, .5F, 3),
                Math.min((height - 6F) / game.font.lineHeight, textWidth / (float) Math.max(1, game.font.width(label))));
        scale = Math.max(.5F, scale);
        label = game.font.plainSubstrByWidth(label, Math.max(1, (int) (textWidth / scale)));
        MailDecor.centered(graphics, game.font, label, (textLeft + textRight) / 2,
                y + Math.round((height - game.font.lineHeight * scale) / 2), scale, color);
    }

    private static int faded(int color, float opacity) {
        return Math.round((color >>> 24) * opacity) << 24 | color & 0xFFFFFF;
    }

    static int noticeIconColor(float opacity) {
        return faded(MailboxAnimationStore.color("notice", "icon", 0x80C8C8C8), opacity);
    }
}
