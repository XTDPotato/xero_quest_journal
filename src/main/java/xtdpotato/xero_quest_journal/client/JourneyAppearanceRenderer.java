package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import xtdpotato.xero_quest_journal.core.model.JourneyAppearance.Element;

/** The editor preview and live timeline use identical appearance transforms. */
final class JourneyAppearanceRenderer {
    private JourneyAppearanceRenderer() {}

    static void icon(GuiGraphics g, Element style, String fallback, int x, int y, int w, int h,
                     int color, boolean cover) {
        if (!style.iconVisible()) return;
        String source = style.icon().isBlank() ? fallback : style.icon();
        if (source == null || source.isBlank()) return;
        g.pose().pushPose();
        g.pose().translate(x + w / 2F + style.iconOffsetX(), y + h / 2F + style.iconOffsetY(), 0);
        g.pose().scale(style.iconScale(), style.iconScale(), 1);
        g.pose().translate(-w / 2F, -h / 2F, 0);
        if (source.equals("@marker")) {
            int size = Math.min(w, h) / 2, cx = w / 2, cy = h / 2;
            for (int row = -size; row <= size; row++)
                g.fill(cx - size + Math.abs(row), cy + row, cx + size - Math.abs(row) + 1, cy + row + 1, color);
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
            int size = Math.min(w, h);
            JournalNavButton.drawIcon(g, source, (w - size) / 2, (h - size) / 2, size, color);
        }
        g.pose().popPose();
    }

    static void text(GuiGraphics g, Font font, Element style, String text, int x, int y,
                     int width, int color, boolean centered) {
        if (!style.textVisible()) return;
        if (!style.text().isBlank()) text = style.text();
        color = ButtonStyle.parseColor(style.textColor(), color);
        String clipped = font.plainSubstrByWidth(text, Math.max(1, (int) (width / style.textScale())));
        g.pose().pushPose();
        g.pose().translate(x + style.textOffsetX(), y + style.textOffsetY(), 0);
        g.pose().scale(style.textScale(), style.textScale(), 1);
        g.drawString(font, clipped, centered ? -font.width(clipped) / 2 : 0, 0, color, false);
        g.pose().popPose();
    }

    static boolean hit(Element style, double px, double py, int x, int y, int size) {
        if (!style.iconVisible()) return false;
        double half = size * style.iconScale() / 2. + 3;
        return Math.abs(px - x - size / 2. - style.iconOffsetX()) <= half
                && Math.abs(py - y - size / 2. - style.iconOffsetY()) <= half;
    }
}
