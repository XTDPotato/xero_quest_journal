package xtdpotato.xero_quest_journal.forgecompat;

import java.util.List;

public final class ForgeCompat {
    private ForgeCompat() {
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int right(net.minecraft.client.gui.components.AbstractWidget widget) {
        return widget.getX() + widget.getWidth();
    }

    public static int bottom(net.minecraft.client.gui.components.AbstractWidget widget) {
        return widget.getY() + widget.getHeight();
    }

    public static ForgeCheckboxBuilder checkbox(net.minecraft.network.chat.Component label,
                                                net.minecraft.client.gui.Font font) {
        return new ForgeCheckboxBuilder(label, font);
    }

    public static <T> T last(List<T> values) {
        return values.get(values.size() - 1);
    }
}
