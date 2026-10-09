package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Shared surface renderer for authored controls. */
final class ControlAppearance {
    private ControlAppearance() {}

    static void draw(GuiGraphics graphics, int x, int y, int width, int height,
                     String shape, int radius, int background, int border, int borderWidth) {
        if (width <= 0 || height <= 0) return;
        int edge = Math.min(Math.max(0, borderWidth), Math.min(width, height) / 2);
        if ((border >>> 24) != 0 && edge > 0) {
            for (int row = 0; row < height; row++) {
                int outerInset = inset(shape, radius, width, height, row);
                int left = x + outerInset, right = x + width - outerInset;
                if (row < edge || row >= height - edge) {
                    graphics.fill(left, y + row, right, y + row + 1, border);
                } else {
                    int innerInset = inset(shape, Math.max(0, radius - edge),
                            width - edge * 2, height - edge * 2, row - edge);
                    int innerLeft = x + edge + innerInset;
                    int innerRight = x + width - edge - innerInset;
                    if (innerLeft > left) graphics.fill(left, y + row, innerLeft, y + row + 1, border);
                    if (right > innerRight) graphics.fill(innerRight, y + row, right, y + row + 1, border);
                }
            }
        }
        if ((background >>> 24) != 0) fill(graphics, x + edge, y + edge, width - edge * 2,
                height - edge * 2, shape, Math.max(0, radius - edge), background);
    }

    private static void fill(GuiGraphics graphics, int x, int y, int width, int height,
                             String shape, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        for (int row = 0; row < height; row++) {
            int inset = inset(shape, radius, width, height, row);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    static int inset(String shape, int radius, int width, int height, int row) {
        if (shape.equals("ellipse")) {
            double normalized = (row + 0.5 - height / 2.0) / (height / 2.0);
            return (int) Math.ceil(width / 2.0 * (1 - Math.sqrt(Math.max(0, 1 - normalized * normalized))));
        }
        int corner = shape.equals("pill") ? Math.min(width, height) / 2
                : shape.equals("rounded") ? Math.min(radius, Math.min(width, height) / 2) : 0;
        if (corner == 0) return 0;
        int dy = row < corner ? corner - row - 1 : row >= height - corner ? row - (height - corner) : 0;
        return (int) Math.ceil(corner - Math.sqrt(Math.max(0, corner * corner - dy * dy)));
    }
}
