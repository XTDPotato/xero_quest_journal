package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.mailbox.MailboxRuntime;

/** One reference-style image/action/time layout for the editor preview and mailbox. */
public final class MailBannerRenderer {
    private MailBannerRenderer() {}

    public static String remainingLabel(MailboxRuntime.View mail) {
        if (mail.bannerTiming().mode().equals("none")) return "";
        long seconds = ClientMailbox.bannerRemainingSeconds(mail);
        if (seconds < 0) return Component.translatable("screen.xero_quest_journal.mailbox.banner_time_unknown").getString();
        if (seconds == Long.MAX_VALUE) return Component.translatable("screen.xero_quest_journal.mailbox.expiry.permanent").getString();
        boolean game = mail.bannerTiming().mode().equals("mail") ? mail.gameTime() : mail.bannerTiming().gameTime();
        return formatRemaining(seconds, game);
    }

    public static String formatRemaining(long seconds, boolean game) {
        long day = game ? 1200 : 86400;
        String unit = seconds >= day ? (game ? "game_days" : "days")
                : seconds >= 3600 ? "hours" : seconds >= 60 ? "minutes" : "seconds";
        long divisor = seconds >= day ? day : seconds >= 3600 ? 3600 : seconds >= 60 ? 60 : 1;
        return Component.translatable("screen.xero_quest_journal.mailbox.banner_remaining." + unit,
                (seconds + divisor - 1) / divisor).getString();
    }

    public static void draw(GuiGraphics graphics, Font font, String image, boolean action, String remaining,
                            int x, int y, int width, int height, boolean hovered) {
        ClientMediaManager.renderImageCover(graphics, image, x, y, width, height, 1);
        graphics.renderOutline(x, y, width, height, hovered ? 0xFFFFFFFF : 0xFFD9D2C2);
        int inset = Math.max(6, Math.min(10, width / 40));
        if (!remaining.isBlank()) {
            float scale = Math.min(1.2F, (width - 2F * inset) / Math.max(1, font.width(remaining)));
            int right = x + width - inset;
            MailDecor.text(graphics, font, remaining, right - Math.round(font.width(remaining) * scale),
                    y + inset, scale, 0xFFFFFFFF);
        }
        if (!action) return;
        int h = Math.clamp(height * 2 / 5, 22, 32), w = Math.min(width - 2 * inset, h * 4);
        int bx = x + width - inset - w, by = y + height - inset - h;
        int color = hovered ? 0xFF434B5B : 0xFF303642;
        MailDecor.circle(graphics, bx, by, h, color);
        MailDecor.circle(graphics, bx + w - h, by, h, color);
        graphics.fill(bx + h / 2, by, bx + w - h / 2, by + h, color);
        int circle = h - 8, cx = bx + w - circle - 6, cy = by + 4;
        MailDecor.circle(graphics, cx, cy, circle, 0xFFFFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(cx + circle / 2F, cy + circle / 2F, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        JournalNavButton.drawIcon(graphics, "back", -circle / 2 + 3, -circle / 2 + 3, circle - 6, 0xFF303642);
        graphics.pose().popPose();
        String label = Component.translatable("screen.xero_quest_journal.mailbox.go_to_task").getString();
        float scale = Math.min(1.35F, (w - circle - 16F) / Math.max(1, font.width(label)));
        MailDecor.centered(graphics, font, label, bx + (w - circle - 8) / 2,
                by + Math.round((h - font.lineHeight * scale) / 2), scale, 0xFFFFFFFF);
    }
}
