package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Pixel-built book frame and the overview map-scroll emblem. */
final class AnecdoteBookArt {
    private AnecdoteBookArt() {}

    static void spread(GuiGraphics g, int left, int top, int width, int height) {
        int middle = left + width / 2;
        int cover = 0xFF394F70, coverLight = 0xFF637A9A, edge = 0xFF9B693E;
        int gold = 0xFFE1BD70, paper = 0xFFF4E9D4;
        g.fill(left - 14, top - 5, left + width + 14, top + height + 15, 0x50211919);
        // Stepped cover corners and stacked page edges keep the book silhouette crisp at GUI scale.
        g.fill(left - 10, top + 8, left + width + 10, top + height - 3, 0xFF293D59);
        g.fill(left - 8, top - 2, left + width + 8, top + height + 10, cover);
        g.fill(left - 5, top - 5, left + width + 5, top + height + 7, coverLight);
        g.fill(left - 2, top - 7, left + width + 2, top + height + 5, gold);
        for (int page = 0; page < 4; page++) {
            int inset = 5 - page;
            int y = top + height - 3 + page * 3;
            g.fill(left - inset, y, left + width + inset, y + 3, 0xFFB8A47F - page * 0x00020202);
            g.fill(left - inset, top + page * 2, left + width + inset, top + 4 + page * 2,
                    0xFFE7D8BA - page * 0x00020202);
        }
        g.fill(left - 2, top - 4, left + width + 2, top + height + 2, edge);
        g.fill(left, top - 2, left + width, top + height, gold);
        int pageTop = top + 3, pageBottom = top + height - 3;
        g.fill(left + 4, pageTop, middle - 2, pageBottom, paper);
        g.fill(middle + 2, pageTop, left + width - 4, pageBottom, 0xFFF8F1E4);
        g.fill(left + 7, pageTop + 5, middle - 5, pageTop + 10, 0xFFE9D8B8);
        g.fill(middle + 5, pageTop + 5, left + width - 7, pageTop + 10, 0xFFEDE1C9);
        g.fill(left + 7, pageBottom - 7, middle - 5, pageBottom - 3, 0xFFD8C7A6);
        g.fill(middle + 5, pageBottom - 7, left + width - 7, pageBottom - 3, 0xFFE5D8BE);
        g.fill(middle - 7, pageTop + 1, middle + 7, pageBottom - 1, 0x22332324);
        g.fill(middle - 2, pageTop + 1, middle, pageBottom - 1, 0xFFBBA57E);
        g.fill(middle, pageTop + 1, middle + 3, pageBottom - 1, 0xFFFFF9EC);

        int borderLeft = left + 7, borderTop = pageTop + 7;
        int borderRight = left + width - 7, borderBottom = pageBottom - 7;
        g.renderOutline(borderLeft, borderTop, middle - borderLeft - 5, borderBottom - borderTop, 0x66A88E63);
        g.renderOutline(middle + 5, borderTop, borderRight - middle - 5, borderBottom - borderTop, 0x66A88E63);
        for (int px = left + 14; px < middle - 12; px += 36)
            for (int py = pageTop + 16 + ((px / 36) % 2) * 5; py < pageBottom - 12; py += 36)
                g.fill(px, py, px + 2, py + 2, 0x18A48655);
        for (int px = middle + 12; px < left + width - 14; px += 36)
            for (int py = pageTop + 16 + ((px / 36) % 2) * 5; py < pageBottom - 12; py += 36)
                g.fill(px, py, px + 2, py + 2, 0x18A48655);
        corner(g, borderLeft + 2, borderTop + 2, 1, gold);
        corner(g, borderRight - 2, borderTop + 2, -1, gold);
        corner(g, borderLeft + 2, borderBottom - 2, 1, gold);
        corner(g, borderRight - 2, borderBottom - 2, -1, gold);
        g.fill(middle - 2, top - 5, middle + 3, top + 1, gold);
        g.fill(middle - 2, top + height - 1, middle + 3, top + height + 4, gold);
        drawBookmark(g, middle + width / 7, top + height + 1, Math.max(8, Math.min(20, width / 70)));
    }

    private static void corner(GuiGraphics g, int x, int y, int direction, int color) {
        int horizontal = direction < 0 ? x - 4 : x;
        g.fill(horizontal, y, horizontal + 4, y + 1, color);
        g.fill(direction < 0 ? x - 1 : x, y, direction < 0 ? x : x + 1, y + 5, color);
        g.fill(direction < 0 ? x - 5 : x + 3, y + 2, direction < 0 ? x - 3 : x + 5, y + 3, 0x55A48655);
        g.fill(direction < 0 ? x - 3 : x + 1, y + 4, direction < 0 ? x - 1 : x + 3, y + 5, 0x55A48655);
    }

    private static void drawBookmark(GuiGraphics g, int center, int top, int width) {
        g.fill(center - width / 2, top, center + width / 2 + 1, top + width, 0xFFD5B35F);
        g.fill(center - 2, top + width - 4, center, top + width - 1, 0xFFA77739);
        g.fill(center, top + width - 4, center + 3, top + width - 1, 0xFFA77739);
    }

    static void ribbon(GuiGraphics g, int x, int y, int width, String title) {
        g.fill(x + 4, y + 5, x + width + 4, y + 30, 0xFFCF9B55);
        g.fill(x, y, x + width, y + 26, 0xFFFFE9B6);
        g.fill(x + 5, y + 4, x + width - 5, y + 22, 0xFFFFF2D4);
        g.fill(x + 4, y + 26, x + 13, y + 35, 0xFFCC9852);
        g.fill(x + width - 13, y + 26, x + width - 4, y + 35, 0xFFCC9852);
        g.renderOutline(x, y, width, 26, 0xFFE2BD76);
        int textWidth = MinecraftFonts.width(title);
        g.drawString(MinecraftFonts.font(), title, x + (width - textWidth) / 2, y + 8, 0xFF765221, false);
    }

    private static final class MinecraftFonts {
        static net.minecraft.client.gui.Font font() { return net.minecraft.client.Minecraft.getInstance().font; }
        static int width(String value) { return font().width(value); }
    }

    static void mapScroll(GuiGraphics g, int x, int y, int size, int color) {
        float scale = size / 16F;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        // The ink occupies rows 1..11; center its visible bounds within the 16px cell.
        g.pose().translate(0, 2, 0);
        int paper = color, shade = 0xFFB58A50, ink = 0xFF715335;
        String[] rows = {
                "................",
                "...ss......dd...",
                "..sppss...dppd..",
                ".spphppss.dpipd.",
                ".sphhpps.ddppd..",
                "..sphpps...dd...",
                "...spps....p....",
                "....spps...p....",
                ".....spps.pp....",
                "......sppps.....",
                ".......sss......",
                "......s.........",
                "................",
                "................",
                "................",
                "................"
        };
        for (int py = 0; py < rows.length; py++) {
            for (int px = 0; px < rows[py].length(); px++) {
                int pixel = switch (rows[py].charAt(px)) {
                    case 's' -> shade;
                    case 'p' -> paper;
                    case 'h' -> 0xFFF9E8B9;
                    case 'd' -> ink;
                    case 'i' -> 0xFFFFE7A0;
                    default -> 0;
                };
                if (pixel != 0) g.fill(px, py, px + 1, py + 1, pixel);
            }
        }
        g.pose().popPose();
    }
}
