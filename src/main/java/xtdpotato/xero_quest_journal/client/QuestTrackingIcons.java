package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Small raster emblems and two fading diamond waves on a stable, refresh-independent clock. */
public final class QuestTrackingIcons {
    private static final long ORIGIN = System.nanoTime();
    private QuestTrackingIcons() {}

    public static double waveProgress(long elapsedNanos, int wave) {
        double seconds = Math.floorMod(elapsedNanos, 3_600_000_000L) / 1_000_000_000.0;
        double age = seconds - wave * 0.42;
        return age < 0 || age >= 1.45 ? -1 : age / 1.45;
    }

    public static float waveAlpha(double progress) {
        if (progress < 0 || progress >= 1) return 0;
        return (float) (0.65 * (1 - progress) * Math.min(1, progress / 0.10));
    }

    public static void render(GuiGraphics g, String style, boolean tracked, int x, int y, int size,
                              float alpha, boolean reducedMotion) {
        renderAt(g, style, tracked, x, y, size, alpha, reducedMotion, System.nanoTime() - ORIGIN);
    }

    public static void renderAt(GuiGraphics g, String style, boolean tracked, int x, int y, int size,
                                float alpha, boolean reducedMotion, long elapsedNanos) {
        if (size <= 0 || alpha <= 0) return;
        if (tracked && !reducedMotion) {
            int color = style.equals("main") || style.equals("commission") ? 0xFFFFD078 : 0xFFADDFFF;
            for (int wave = 0; wave < 2; wave++) {
                double progress = waveProgress(elapsedNanos, wave);
                float opacity = waveAlpha(progress) * alpha;
                if (opacity <= 0) continue;
                double radius = size * (0.40 + progress * 0.57);
                diamondRing(g, x + size / 2.0, y + size / 2.0, radius, Math.max(0.8, size / 16.0),
                        color, opacity);
            }
        }
        // Fixed design-space raster keeps every emblem within its configured icon slot.
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 32.0F, size / 32.0F, 1);
        for (int py = 0; py < 32; py++) {
            int run = 0, previous = 0;
            for (int px = 0; px <= 32; px++) {
                int color = px == 32 ? 0 : pixel(style, tracked, px + 0.5 - 16, py + 0.5 - 16);
                if (color != previous) {
                    if (previous != 0) g.fill(run, py, px, py + 1, fade(previous, alpha));
                    run = px;
                    previous = color;
                }
            }
        }
        g.pose().popPose();
    }

    private static int pixel(String style, boolean tracked, double x, double y) {
        if (!tracked) {
            double radius = Math.hypot(x, y);
            double head = Math.hypot(x, y + 3.8);
            boolean pin = head < 6.7 && y <= 0 || y >= -1 && y <= 9 && Math.abs(x) < (9 - y) * 0.62;
            if (pin && head > 2.6) return 0xFFADA897;
            if (radius > 12.2 && radius < 13.5) return 0x606E6D67;
            return 0;
        }
        double d = Math.abs(x) + Math.abs(y);
        boolean world = style.equals("world");
        boolean commission = style.equals("commission");
        boolean gold = style.equals("main");
        if (commission) {
            double max = Math.max(Math.abs(x), Math.abs(y));
            double min = Math.min(Math.abs(x), Math.abs(y));
            double edge = max + min * 1.55;
            if (edge > 14.4) return 0;
            if (edge > 12.6) return 0xFF716380;
            if (edge > 10.1) return 0xFFBD9BFA;
            if (edge > 7.1) return 0xFF6945A0;
            return y > -Math.abs(x) * 1.05 + 1.5 ? 0xFFD6B7FF : 0xFF9265D6;
        }
        if (!emblemShape(x, y, world)) return 0;
        int outline = gold ? 0xFF60503B : 0xFF777E8C;
        int rim = gold ? 0xFFFFC921 : 0xFF85AFF5;
        int dark = gold ? 0xFF9F6C11 : 0xFF3C60AC;
        int core = gold ? 0xFFFFEB30 : 0xFF9DF2FF;
        if (!emblemShape(x + 1.3, y, world) || !emblemShape(x - 1.3, y, world)
                || !emblemShape(x, y + 1.3, world) || !emblemShape(x, y - 1.3, world)) return outline;
        if (d > 9) return rim;
        if (d > 5) return dark;
        return core;
    }

    private static boolean emblemShape(double x, double y, boolean world) {
        double major = Math.max(Math.abs(x), Math.abs(y));
        double minor = Math.min(Math.abs(x), Math.abs(y));
        return major + minor <= 14 || !world && major >= 10 && major + minor <= 17 && major <= 16;
    }

    public static void renderControl(GuiGraphics g, int x, int y, int size, int color) {
        double radius = size * 0.44;
        diamondRing(g, x + size / 2.0, y + size / 2.0, radius, Math.max(1, size / 12.0),
                color, 0.42F);
        MailDecor.diamond(g, x + size / 2, y + size / 2, Math.max(1, size / 7), color);
    }

    private static void diamondRing(GuiGraphics g, double cx, double cy, double radius, double width,
                                    int color, float opacity) {
        int extent = (int) Math.ceil(radius + 1);
        for (int dy = -extent; dy <= extent; dy++) {
            double half = radius - Math.abs(dy + 0.5);
            if (half < 0) continue;
            int yy = (int) Math.floor(cy + dy);
            int left = (int) Math.floor(cx - half);
            int right = (int) Math.ceil(cx + half);
            int thickness = Math.max(1, (int) Math.round(width));
            g.fill(left - 1, yy, left + thickness + 1, yy + 1, fade(color, opacity * 0.16F));
            g.fill(right - thickness - 1, yy, right + 1, yy + 1, fade(color, opacity * 0.16F));
            g.fill(left, yy, Math.min(left + thickness, right), yy + 1, fade(color, opacity));
            g.fill(Math.max(left, right - thickness), yy, right, yy + 1, fade(color, opacity));
        }
    }

    private static int fade(int color, float opacity) {
        return (color & 0xFFFFFF) | Math.clamp(Math.round((color >>> 24) * opacity), 0, 255) << 24;
    }
}
