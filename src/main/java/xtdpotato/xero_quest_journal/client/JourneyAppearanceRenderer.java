package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance.Element;

/** The editor preview and live timeline use identical appearance transforms. */
final class JourneyAppearanceRenderer {
    private JourneyAppearanceRenderer() {}

    static void icon(GuiGraphics g, Element style, String fallback, int x, int y, int w, int h,
                     int color, boolean cover) {
        if (!style.iconVisible()) return;
        String source = style.icon().isBlank() ? fallback : style.icon();
        if (source == null || source.isBlank()) return;
        int baseW = w, baseH = h;
        w = style.iconWidth() > 0 ? style.iconWidth() : w;
        h = style.iconHeight() > 0 ? style.iconHeight() : h;
        g.pose().pushPose();
        g.pose().translate(x + baseW / 2F + style.iconOffsetX(), y + baseH / 2F + style.iconOffsetY(), 0);
        g.pose().scale(style.iconScale(), style.iconScale(), 1);
        g.pose().translate(-w / 2F, -h / 2F, 0);
        if (source.equals("@marker")) {
            chapterMarker(g, w, h, color);
        } else if (source.equals("@complete")) {
            QuestStatusIcons.render(g, "complete", 0, 0, Math.min(w, h));
        } else if (source.equals("@vertical")) {
            g.fill(0, 0, w, h, color);
        } else if (source.equals("@next")) {
            g.pose().translate(w, h, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
            JournalNavButton.drawIcon(g, "back", 0, 0, Math.min(w, h), color);
        } else if (cover && (ClientMediaManager.validImageSource(source) || source.contains("textures/"))) {
            ClientMediaManager.renderImageCover(g, source, 0, 0, w, h, 1);
        } else {
            if (style.iconWidth() > 0 || style.iconHeight() > 0) {
                g.pose().pushPose();
                g.pose().scale(w / 32F, h / 32F, 1);
                JournalNavButton.drawIcon(g, source, 0, 0, 32, color);
                g.pose().popPose();
            } else {
                int size = Math.min(w, h);
                JournalNavButton.drawIcon(g, source, (w - size) / 2, (h - size) / 2, size, color);
            }
        }
        g.pose().popPose();
    }

    static void chapterMarker(GuiGraphics g, int w, int h, int color) {
        // Rasterized in the same box as custom icons, with translucent outer glow and a hollow rim.
        for (int row = 0; row < h; row++) {
            int run = 0, previous = 0;
            for (int column = 0; column <= w; column++) {
                double d = Math.abs((column + .5 - w / 2.0) / Math.max(1, w / 2.0))
                        + Math.abs((row + .5 - h / 2.0) / Math.max(1, h / 2.0));
                int pixel = column == w || d > 1 ? 0
                        : d > .90 ? 0x225DDEF5 : d > .80 ? 0x557DCFE8
                        : d > .66 ? 0xCC95D8ED : d > .43 ? 0x226CA9C0 : color;
                if (pixel != previous) {
                    if (previous != 0) g.fill(run, row, column, row + 1, previous);
                    run = column; previous = pixel;
                }
            }
        }
    }

    static void text(GuiGraphics g, Font font, Element style, String text, int x, int y,
                     int width, int color, boolean centered) {
        if (!style.textVisible()) return;
        if (!style.text().isBlank()) text = style.text();
        color = ButtonStyle.parseColor(style.textColor(), color);
        width = style.textWidth() > 0 ? style.textWidth() : width;
        int maxHeight = style.textHeight() > 0 ? style.textHeight() : font.lineHeight;
        g.pose().pushPose();
        g.pose().translate(x + style.textOffsetX(), y + style.textOffsetY(), 0);
        g.pose().scale(style.textScale(), style.textScale(), 1);
        int lineY = 0;
        for (var line : font.split(Component.literal(text), Math.max(1, (int) (width / style.textScale())))) {
            if (lineY > 0 && (style.textHeight() == 0
                    || (lineY + font.lineHeight) * style.textScale() > maxHeight)) break;
            int lineX = centered ? -font.width(line) / 2 : 0;
            g.drawString(font, line, lineX, lineY, color, false);
            lineY += font.lineHeight + 2;
        }
        g.pose().popPose();
    }

    static boolean hit(Element style, double px, double py, int x, int y, int size) {
        if (!style.iconVisible()) return false;
        double halfW = (style.iconWidth() > 0 ? style.iconWidth() : size) * style.iconScale() / 2. + 3;
        double halfH = (style.iconHeight() > 0 ? style.iconHeight() : size) * style.iconScale() / 2. + 3;
        return Math.abs(px - x - size / 2. - style.iconOffsetX()) <= halfW
                && Math.abs(py - y - size / 2. - style.iconOffsetY()) <= halfH;
    }
}
