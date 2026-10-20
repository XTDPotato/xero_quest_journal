package xtdpotato.xero_quest_journal.client;

import xtdpotato.xero_quest_journal.platform.PlatformServices;

import java.util.ArrayList;
import java.util.List;

/** Places Xero HUD overlays around a reserved minimap area and already placed notices. */
public final class HudOverlayLayout {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean intersects(Rect other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    private static final List<Rect> RESERVED = new ArrayList<>();

    private HudOverlayLayout() {}

    public static void beginFrame(int screenWidth, int screenHeight) {
        RESERVED.clear();
        reserveMinimap(screenWidth, screenHeight);
    }

    public static void clearReservations() {
        RESERVED.clear();
    }

    static boolean minimapModPresent() {
        return List.of("xaerominimap", "xaeroworldmap", "journeymap", "voxelmap", "ftbchunks")
                .stream().anyMatch(PlatformServices::isModLoaded);
    }

    public static Corner minimapCorner() {
        String value = ClientPreferences.get().minimapCorner;
        return switch (value == null ? "top_right" : value) {
            case "top_left" -> Corner.TOP_LEFT;
            case "bottom_left" -> Corner.BOTTOM_LEFT;
            case "bottom_right" -> Corner.BOTTOM_RIGHT;
            default -> Corner.TOP_RIGHT;
        };
    }

    public static Corner cornerFromPercent(int xPercent, int yPercent) {
        boolean right = xPercent >= 50;
        boolean bottom = yPercent >= 50;
        if (right) return bottom ? Corner.BOTTOM_RIGHT : Corner.TOP_RIGHT;
        return bottom ? Corner.BOTTOM_LEFT : Corner.TOP_LEFT;
    }

    public static Rect place(int screenWidth, int screenHeight, int width, int height,
                      Corner preferred, int margin) {
        int maxWidth = Math.max(1, screenWidth - margin * 2);
        int maxHeight = Math.max(1, screenHeight - margin * 2);
        width = Math.min(Math.max(1, width), maxWidth);
        height = Math.min(Math.max(1, height), maxHeight);
        for (Corner corner : order(preferred)) {
            int x = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT
                    ? screenWidth - margin - width : margin;
            int y = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT
                    ? screenHeight - margin - height : margin;
            Rect candidate = new Rect(Math.max(margin, x), Math.max(margin, y), width, height);
            if (RESERVED.stream().noneMatch(candidate::intersects)) {
                RESERVED.add(candidate);
                return candidate;
            }
        }
        Rect fallback = new Rect(margin, margin, width, height);
        RESERVED.add(fallback);
        return fallback;
    }

    public static Rect place(int screenWidth, int screenHeight, Rect desired, Corner preferred, int margin) {
        if (RESERVED.stream().noneMatch(desired::intersects)) {
            RESERVED.add(desired);
            return desired;
        }
        return place(screenWidth, screenHeight, desired.width(), desired.height(), preferred, margin);
    }

    public static boolean avoids(Rect first, Rect second) {
        return !first.intersects(second);
    }

    private static List<Corner> order(Corner preferred) {
        List<Corner> result = new ArrayList<>(List.of(preferred, Corner.TOP_LEFT, Corner.TOP_RIGHT,
                Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT));
        return result.stream().distinct().toList();
    }

    private static void reserveMinimap(int width, int height) {
        ClientPreferences preferences = ClientPreferences.get();
        if (!preferences.hudAutoAvoidMinimap || !minimapModPresent()) return;
        reserveMinimapArea(width, height, minimapCorner());
    }

    public static void reserveMinimapArea(int width, int height, Corner corner) {
        int mapWidth = Math.min(Math.max(112, width / 4), Math.max(1, width / 2));
        int mapHeight = Math.min(Math.max(84, height / 4), Math.max(1, height / 2));
        int margin = 6;
        int x = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT
                ? width - mapWidth - margin : margin;
        int y = corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT
                ? height - mapHeight - margin : margin;
        RESERVED.add(new Rect(Math.max(0, x), Math.max(0, y), mapWidth, mapHeight));
    }
}
