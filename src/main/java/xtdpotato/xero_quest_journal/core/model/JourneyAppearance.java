package xtdpotato.xero_quest_journal.core.model;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sparse chapter overrides, or server defaults when stored in the journal theme. */
public record JourneyAppearance(Map<String, Element> elements) {
    public static final List<String> SLOTS = List.of("header", "chapter", "tab", "card", "branch", "subtitle",
            "previous", "next", "close", "complete", "marker", "scroll_thumb");

    public JourneyAppearance {
        elements = elements == null ? Map.of() : Map.copyOf(elements);
        if (elements.size() > SLOTS.size() || !SLOTS.containsAll(elements.keySet()))
            throw new IllegalArgumentException("Unknown journey appearance element");
    }
    public static JourneyAppearance empty() { return new JourneyAppearance(Map.of()); }
    public Element get(String key) { return elements.getOrDefault(key, Element.defaults()); }
    public JourneyAppearance with(String key, Element value) {
        var next = new LinkedHashMap<>(elements);
        if (value == null) next.remove(key);
        else next.put(key, value);
        return new JourneyAppearance(next);
    }
    public JourneyAppearance over(JourneyAppearance defaults) {
        var next = new LinkedHashMap<>(defaults.elements);
        next.putAll(elements);
        return new JourneyAppearance(next);
    }

    public record Element(String icon, int iconOffsetX, int iconOffsetY, Float iconScale,
                          int textOffsetX, int textOffsetY, Float textScale,
                          Boolean iconVisible, Boolean textVisible, String text, String background, String textColor) {
        public Element(String icon, int iconOffsetX, int iconOffsetY, Float iconScale,
                       int textOffsetX, int textOffsetY, Float textScale,
                       Boolean iconVisible, Boolean textVisible, String text, String background) {
            this(icon, iconOffsetX, iconOffsetY, iconScale, textOffsetX, textOffsetY, textScale,
                    iconVisible, textVisible, text, background, "");
        }
        public Element(String icon, int iconOffsetX, int iconOffsetY, Float iconScale,
                       int textOffsetX, int textOffsetY, Float textScale,
                       Boolean iconVisible, Boolean textVisible) {
            this(icon, iconOffsetX, iconOffsetY, iconScale, textOffsetX, textOffsetY, textScale,
                    iconVisible, textVisible, "", "");
        }
        public Element {
            icon = icon == null ? "" : icon.strip();
            text = text == null ? "" : text;
            background = background == null ? "" : background.strip();
            textColor = textColor == null ? "" : textColor.strip();
            if (!textColor.isBlank() && !textColor.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
                throw new IllegalArgumentException("Invalid journey text color");
            if (text.length() > 256 || background.length() > 2048
                    || !background.isBlank() && (ResourceLocation.tryParse(background) == null
                    || background.contains("..")))
                throw new IllegalArgumentException("Invalid journey text or background");
            iconScale = iconScale == null ? 1F : iconScale;
            textScale = textScale == null ? 1F : textScale;
            iconVisible = iconVisible == null || iconVisible;
            textVisible = textVisible == null || textVisible;
            if (icon.length() > 2048 || !icon.isBlank() && (ResourceLocation.tryParse(icon) == null
                    || icon.contains("..")))
                throw new IllegalArgumentException("Journey icons must be item/image resource IDs");
            if (!Float.isFinite(iconScale) || iconScale < .1 || iconScale > 4
                    || !Float.isFinite(textScale) || textScale < .5 || textScale > 3
                    || Math.abs((long) iconOffsetX) > 256 || Math.abs((long) iconOffsetY) > 256
                    || Math.abs((long) textOffsetX) > 256 || Math.abs((long) textOffsetY) > 256)
                throw new IllegalArgumentException("Invalid journey appearance dimensions");
        }
        public static Element defaults() { return new Element("", 0, 0, 1F, 0, 0, 1F, true, true); }
    }
}
