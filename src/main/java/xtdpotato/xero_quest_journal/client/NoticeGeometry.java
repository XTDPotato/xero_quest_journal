package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.Font;

/** Shared logical sizes keep runtime notices and their editor outlines in the same coordinate system. */
public final class NoticeGeometry {
    public record Task(float scale, int width, int height, int textX, float textScale, int titleY, int hintY) {}
    public record Pickup(float scale, int width, int height, int rowHeight, int headerHeight, float textScale) {}
    private NoticeGeometry() {}

    public static Task task(int screenWidth, int scalePercent, int textPercent, int iconPercent) {
        float scale = Math.min(scalePercent / 100F, Math.max(.1F, (screenWidth - 16) / 430F));
        float textScale = textPercent / (100F * scale);
        float iconScale = iconPercent / (100F * scale);
        int textX = Math.max(74, Math.round(58 * iconScale) + 60);
        int width = Math.min(Math.max(1, (int) ((screenWidth - 16) / scale)),
                Math.max(430, textX + Math.round(140 * textScale) + 16));
        int titleY = 4 + Math.round(10 * textScale) + 4;
        int hintY = titleY + Math.round(14 * textScale) + 5;
        int height = Math.max(90, hintY + Math.round(10 * textScale) + 20);
        height = Math.max(height, Math.round(90 * iconScale));
        return new Task(scale, width, height, textX, textScale, titleY, hintY);
    }

    public static Pickup pickup(int screenWidth, int scalePercent, int textPercent, Font font) {
        float scale = Math.min(scalePercent / 100F, Math.max(.5F, (screenWidth - 20) / 300F));
        int width = Math.max(1, Math.min(320, Math.round((screenWidth - 20) / scale)));
        float textScale = textPercent / (100F * scale);
        int rowHeight = Math.max(30, (int) Math.ceil(font.lineHeight * textScale) + 12);
        int headerHeight = Math.max(20, (int) Math.ceil(font.lineHeight * textScale * 1.1) + 8);
        return new Pickup(scale, width, headerHeight + 4 * rowHeight, rowHeight, headerHeight, textScale);
    }
}
