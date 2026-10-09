package xtdpotato.xero_quest_journal.core.model;

import java.util.List;
import java.util.Locale;

/** Authored reward-card quality override and optional solid background color. */
public final class QuestRewardQuality {
    public static final List<String> VALUES = List.of(
            "auto", "common", "uncommon", "rare", "epic", "legendary", "mythic");

    private QuestRewardQuality() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (normalized.equals("auto")) return "";
        if (!VALUES.contains(normalized)) throw new IllegalArgumentException("Invalid reward quality");
        return normalized;
    }

    public static String normalizeBackground(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("#[0-9A-F]{6}")) throw new IllegalArgumentException("Invalid reward background");
        return normalized;
    }
}
