package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

public final class QuestTrackingStyle {
    public static final List<String> VALUES = List.of("auto", "main", "legendary", "world", "commission");

    private QuestTrackingStyle() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "auto";
        if (!VALUES.contains(value)) throw new IllegalArgumentException("Unknown tracking style: " + value);
        return value;
    }

    public static String resolve(String value, String type) {
        String style = normalize(value);
        if (!style.equals("auto")) return style;
        return switch (QuestDefinition.normalizeType(type)) {
            case "main" -> "main";
            case "legendary" -> "legendary";
            case "commission" -> "commission";
            default -> "world";
        };
    }
}
