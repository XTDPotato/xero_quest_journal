package xtdpotato.xero_quest_journal.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-owned journal colors. Missing keys leave the existing design untouched. */
public record QuestUiTheme(Map<String, String> colors, String overviewImage, JourneyAppearance journeyAppearance) {
    public static final String DEFAULT_OVERVIEW_IMAGE = "minecraft:textures/gui/title/background/panorama_0.png";
    public QuestUiTheme(Map<String, String> colors) { this(colors, ""); }
    public QuestUiTheme(Map<String, String> colors, String overviewImage) {
        this(colors, overviewImage, JourneyAppearance.empty());
    }
    public static final List<String> SURFACES = List.of("background", "panel", "header", "accent");
    public static final List<String> BUTTONS = List.of("button", "overview", "track", "complete", "claim");
    public static final List<String> STATES = List.of("Background", "Hover", "Pressed", "Border", "Text");
    public static final List<String> KEYS = keys();

    private static List<String> keys() {
        var keys = new java.util.ArrayList<String>(SURFACES);
        for (String button : BUTTONS) for (String state : STATES) keys.add(button + state);
        return List.copyOf(keys);
    }

    public QuestUiTheme {
        journeyAppearance = journeyAppearance == null ? JourneyAppearance.empty() : journeyAppearance;
        overviewImage = overviewImage == null ? "" : overviewImage.strip();
        validateOverviewImage(overviewImage);
        colors = colors == null ? Map.of() : Map.copyOf(colors);
        if (colors.size() > 40) throw new IllegalArgumentException("Too many theme colors");
        colors.forEach(QuestUiTheme::validate);
    }

    public static QuestUiTheme empty() { return new QuestUiTheme(Map.of()); }

    public static boolean knownKey(String key) { return KEYS.contains(key); }

    public static void validate(String key, String color) {
        boolean known = SURFACES.contains(key) || BUTTONS.stream()
                .anyMatch(button -> STATES.stream().anyMatch(state -> (button + state).equals(key)));
        if (!known || color == null || !color.isBlank() && !color.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("Invalid theme color: " + key);
    }

    public QuestUiTheme with(String key, String color) {
        validate(key, color);
        var next = new LinkedHashMap<>(colors);
        if (color.isBlank()) next.remove(key);
        else next.put(key, color);
        return new QuestUiTheme(next, overviewImage, journeyAppearance);
    }

    public QuestUiTheme withOverviewImage(String image) { return new QuestUiTheme(colors, image, journeyAppearance); }
    public QuestUiTheme withJourneyAppearance(JourneyAppearance value) { return new QuestUiTheme(colors, overviewImage, value); }

    public static void validateOverviewImage(String image) {
        if (image == null || image.length() > 2048) throw new IllegalArgumentException("Invalid overview image");
        if (image.isBlank()) return;
        var id = net.minecraft.resources.ResourceLocation.tryParse(image);
        if (id == null || id.getPath().contains("..")
                || !(id.getPath().endsWith(".png") || id.getPath().endsWith(".jpg")
                || id.getPath().endsWith(".jpeg")
                || id.getNamespace().equals("xero_quest_local") && id.getPath().matches("(?:[0-9a-f]{2})+")))
            throw new IllegalArgumentException("Overview background must be an image resource");
    }

    public String get(String key) { return colors.getOrDefault(key, ""); }
}
