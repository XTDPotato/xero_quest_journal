package xtdpotato.xero_quest_journal.client;

/**
 * The small, render-friendly subset of CSS used by native journal buttons.
 * Colors are ARGB values; opacity is applied after the color is resolved.
 */
public record ButtonStyle(
        int backgroundColor,
        int borderColor,
        int borderWidth,
        int textColor,
        float opacity,
        int padding,
        boolean textShadow
) {
    public static final int FRAME_COLOR = 0xFF9DB4D2;

    public static ButtonStyle defaults() {
        return new ButtonStyle(0xFF46556F, FRAME_COLOR, 1, 0xFFFFFFFF, 1.0F, 2, false);
    }

    public ButtonStyle merge(String property, String value) {
        return switch (property) {
            case "background", "background-color" ->
                    new ButtonStyle(parseColor(value, backgroundColor), borderColor, borderWidth, textColor,
                            opacity, padding, textShadow);
            case "border", "border-color" ->
                    new ButtonStyle(backgroundColor, parseColor(value, borderColor), borderWidth, textColor,
                            opacity, padding, textShadow);
            case "border-width" ->
                    new ButtonStyle(backgroundColor, borderColor, parseInt(value, borderWidth), textColor,
                            opacity, padding, textShadow);
            case "color", "text-color" ->
                    new ButtonStyle(backgroundColor, borderColor, borderWidth, parseColor(value, textColor),
                            opacity, padding, textShadow);
            case "opacity" ->
                    new ButtonStyle(backgroundColor, borderColor, borderWidth, textColor,
                            parseOpacity(value, opacity), padding, textShadow);
            case "padding" ->
                    new ButtonStyle(backgroundColor, borderColor, borderWidth, textColor,
                            opacity, parseInt(value, padding), textShadow);
            case "text-shadow" ->
                    new ButtonStyle(backgroundColor, borderColor, borderWidth, textColor,
                            opacity, padding, parseBoolean(value, textShadow));
            default -> this;
        };
    }

    public static int parseColor(String raw, int fallback) {
        String value = raw.trim();
        if (value.startsWith("#")) {
            try {
                long parsed = Long.parseLong(value.substring(1), 16);
                if (value.length() == 7) {
                    return 0xFF000000 | (int) parsed;
                }
                if (value.length() == 9) {
                    // CSS uses RRGGBBAA; Minecraft uses AARRGGBB.
                    return (int) ((parsed & 0xFF) << 24 | (parsed >>> 8) & 0x00FFFFFF);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "transparent" -> 0;
            case "white" -> 0xFFFFFFFF;
            case "black" -> 0xFF000000;
            case "red" -> 0xFFFF0000;
            case "green" -> 0xFF008000;
            case "blue" -> 0xFF0000FF;
            case "yellow" -> 0xFFFFFF00;
            default -> fallback;
        };
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Math.max(0, Math.min(32, Integer.parseInt(raw.replace("px", "").trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseOpacity(String raw, float fallback) {
        try {
            float parsed = Float.parseFloat(raw.trim());
            return Float.isFinite(parsed) ? Math.max(0.0F, Math.min(1.0F, parsed)) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off", "none" -> false;
            default -> fallback;
        };
    }
}
