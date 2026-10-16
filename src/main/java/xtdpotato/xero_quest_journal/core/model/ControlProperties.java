package xtdpotato.xero_quest_journal.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Properties that may be changed by a trigger without changing control identity or graph metadata. */
public final class ControlProperties {
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("value", "");
        DEFAULTS.put("x", "0"); DEFAULTS.put("y", "0");
        DEFAULTS.put("width", "-2"); DEFAULTS.put("height", "-2");
        DEFAULTS.put("visibility", "visible"); DEFAULTS.put("alpha", "1.0");
        DEFAULTS.put("layout_gravity", "inherit");
        for (String key : List.of("layout_marginLeft", "layout_marginTop", "layout_marginRight",
                "layout_marginBottom", "paddingLeft", "paddingTop", "paddingRight", "paddingBottom"))
            DEFAULTS.put(key, "0");
        DEFAULTS.put("background", "transparent");
        DEFAULTS.put("borderColor", "transparent");
        DEFAULTS.put("borderWidth", "0");
        DEFAULTS.put("borderEnabled", "true");
        DEFAULTS.put("shape", "rectangle");
        DEFAULTS.put("cornerRadius", "0");
        for (String suffix : List.of("Hover", "Pressed")) {
            DEFAULTS.put("background" + suffix, "inherit");
            DEFAULTS.put("borderColor" + suffix, "inherit");
            DEFAULTS.put("borderWidth" + suffix, "");
            DEFAULTS.put("borderEnabled" + suffix, "inherit");
            DEFAULTS.put("shape" + suffix, "inherit");
            DEFAULTS.put("cornerRadius" + suffix, "");
        }
        DEFAULTS.put("gravity", "top|left");
        DEFAULTS.put("orientation", "vertical"); DEFAULTS.put("spacing", "4");
        DEFAULTS.put("textColor", "#ffffff"); DEFAULTS.put("textStyle", "normal");
        DEFAULTS.put("textSize", "1.0"); DEFAULTS.put("maxLines", "0");
        DEFAULTS.put("shadowColor", "#000000"); DEFAULTS.put("shadowRadius", "0");
        DEFAULTS.put("shadowDx", "0"); DEFAULTS.put("shadowDy", "0");
        DEFAULTS.put("scaleType", "fit_xy");
        DEFAULTS.put("autoplay", "true"); DEFAULTS.put("loop", "false");
        DEFAULTS.put("muted", "false"); DEFAULTS.put("volume", "1.0");
        DEFAULTS.put("showControls", "true"); DEFAULTS.put("displayText", "");
        DEFAULTS.put("entryIcon", "minecraft:paper"); DEFAULTS.put("entryProgress", "0/1");
        DEFAULTS.put("buttonImage", "");
        DEFAULTS.put("buttonImageHover", "inherit"); DEFAULTS.put("buttonImagePressed", "inherit");
        DEFAULTS.put("buttonImageWidth", "16"); DEFAULTS.put("buttonImageHeight", "16");
        DEFAULTS.put("buttonImageGap", "4"); DEFAULTS.put("buttonImagePosition", "left");
        DEFAULTS.put("textColorHover", "inherit"); DEFAULTS.put("textColorPressed", "inherit");
    }
    private ControlProperties() {}
    public static List<String> all() { return List.copyOf(DEFAULTS.keySet()); }
    public static String defaultValue(String key) { return DEFAULTS.getOrDefault(key, ""); }
    public static List<String> forKind(String kind) {
        return DEFAULTS.keySet().stream().filter(key -> applicable(kind, key)).toList();
    }
    public static boolean applicable(String kind, String key) {
        if (!DEFAULTS.containsKey(key)) return false;
        if (key.equals("displayText"))
            return List.of("audio", "video", "structure_enter", "quest_progress", "quest_objective").contains(kind);
        if (key.startsWith("buttonImage") || key.equals("textColorHover") || key.equals("textColorPressed"))
            return button(kind);
        if (key.equals("value")) return !List.of("linear", "relative").contains(kind);
        if (key.equals("gravity")) return List.of("linear", "relative").contains(kind);
        if (key.equals("orientation") || key.equals("spacing"))
            return List.of("linear", "relative").contains(kind);
        if (key.startsWith("text") || key.equals("maxLines") || key.startsWith("shadow"))
            return List.of("text", "button", "structure_enter", "quest_entry_text", "quest_title",
                    "quest_description", "quest_progress", "quest_status", "quest_type", "quest_objective",
                    "quest_place").contains(kind);
        if (key.equals("scaleType")) return List.of("image", "video").contains(kind);
        if (List.of("autoplay", "loop", "muted", "volume").contains(key))
            return List.of("audio", "video").contains(kind);
        if (key.equals("showControls")) return kind.equals("audio");
        if (key.startsWith("entry")) return kind.equals("quest_entry_text");
        return true;
    }
    public static boolean button(String kind) { return kind.equals("button") || kind.equals("structure_enter"); }
}
