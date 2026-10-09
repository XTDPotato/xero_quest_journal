package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

public final class QuestListAccent {
    public static final List<String> VALUES = List.of("auto", "gold", "blue", "none");
    private QuestListAccent() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "auto";
        if (!VALUES.contains(value)) throw new IllegalArgumentException("Unknown list accent: " + value);
        return value;
    }

    public static String resolve(String task, String group, String type, boolean grouped) {
        String choice = normalize(task);
        if (!choice.equals("auto")) return choice;
        choice = normalize(group);
        if (!choice.equals("auto") && grouped) return choice;
        if (!grouped) return "none";
        return switch (QuestDefinition.normalizeType(type)) {
            case "main" -> "gold";
            case "legendary" -> "blue";
            default -> "none";
        };
    }
}
