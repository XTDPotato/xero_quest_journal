package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared frame and separators for the mailbox's reference-style dialogs. */
final class MailDecor {
    private MailDecor() {}
    static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                     int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }
    static void centered(GuiGraphics g, net.minecraft.client.gui.Font font, String value,
                         int center, int y, float scale, int color) {
        text(g, font, value, center - Math.round(font.width(value) * scale / 2), y, scale, color);
    }
    static void circle(GuiGraphics g, int x, int y, int size, int color) {
        double radius = size / 2.0;
        for (int row = 0; row < size; row++) {
            double dy = row + .5 - radius;
            int inset = Math.max(0, (int) Math.ceil(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)) - .5));
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color);
        }
    }
    static void diamond(GuiGraphics g, int x, int y, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            g.fill(x - half, y + row, x + half + 1, y + row + 1, color);
        }
    }
    static void divider(GuiGraphics g, int x, int right, int y, int color) {
        g.fill(x, y, right, y + 1, color);
        diamond(g, x + 4, y, 3, color);
        diamond(g, right - 4, y, 3, color);
        diamond(g, (x + right) / 2, y, 2, color);
    }
    static void frame(GuiGraphics g, int x, int y, int w, int h, boolean light) {
        int border = light ? 0xFFFFEAD2 : 0xFF656873;
        g.fill(x, y, x + w, y + h, light ? 0xFFFFFFFC : 0xFF424A5C);
        g.renderOutline(x, y, w, h, border);
        g.renderOutline(x + 4, y + 4, w - 8, h - 8, border);
        JournalNavButton.drawIcon(g, "mail_corner", x - 3, y - 3, 16, 0xFFFFD996);
        JournalNavButton.drawIcon(g, "mail_corner_tr", x + w - 13, y - 3, 16, 0xFFFFD996);
        JournalNavButton.drawIcon(g, "mail_corner_bl", x - 3, y + h - 13, 16, 0xFFFFD996);
        JournalNavButton.drawIcon(g, "mail_corner_br", x + w - 13, y + h - 13, 16, 0xFFFFD996);
    }
}
