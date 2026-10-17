package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

/** A reusable, server-authored loot table used by task rewards and graph actions. */
public record LootDefinition(String id, String name, Mode mode, int rolls, String choiceTitle, List<Entry> entries,
                             String displayIcon) {
    public static final String DEFAULT_DISPLAY_ICON = "xero_quest_journal:textures/gui/navigation/gift.png";

    public static String normalizeDisplayIcon(String icon) {
        String value = icon == null ? "" : icon.strip();
        if (value.length() > 8192) throw new IllegalArgumentException("Loot display icon is too large");
        return value.isBlank() ? DEFAULT_DISPLAY_ICON : value;
    }
    public enum Mode {
        RANDOM,
        CHOICE
    }

    /** Client-facing, non-executable table data. Command strings are intentionally omitted. */
    public record Display(String id, String name, Mode mode, int rolls, String choiceTitle,
                          String displayIcon, List<DisplayEntry> entries) {
        public Display {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }

    public record DisplayEntry(String id, String type, String item, int count, int weight,
                               boolean enabled, String quality, String background) {}

    public record Entry(String id, String type, String item, int count, int weight,
                        String condition, boolean enabled, String quality, String background) {
        public Entry(String id, String type, String item, int count, int weight,
                     String condition, boolean enabled) {
            this(id, type, item, count, weight, condition, enabled, "", "");
        }

        public Entry {
            id = id == null || id.isBlank() ? "entry" : id;
            type = type == null || type.isBlank() ? "item" : type;
            item = item == null ? "" : item;
            condition = condition == null || condition.isBlank() ? "always" : condition;
            quality = type.equals("item") ? QuestRewardQuality.normalize(quality) : "";
            background = type.equals("item") ? QuestRewardQuality.normalizeBackground(background) : "";
            if (count < 1 || count > 1000000) throw new IllegalArgumentException("Loot count must be 1..1000000");
            if (weight < 0 || weight > 1000000) throw new IllegalArgumentException("Loot weight must be 0..1000000");
            if (id.length() > 64 || type.length() > 32 || item.length() > 8192 || condition.length() > 512)
                throw new IllegalArgumentException("Loot entry is too large");
        }

        public QuestDefinition.Reward reward() {
            return new QuestDefinition.Reward(type, item, count, quality, background);
        }
    }

    public LootDefinition {
        id = id == null || id.isBlank() ? "unnamed" : id;
        name = name == null ? id : name;
        choiceTitle = choiceTitle == null ? "" : choiceTitle;
        displayIcon = normalizeDisplayIcon(displayIcon);
        mode = mode == null ? Mode.RANDOM : mode;
        if (rolls < 1 || rolls > 128) throw new IllegalArgumentException("Loot rolls must be 1..128");
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (id.length() > 128 || name.length() > 256 || choiceTitle.length() > 256 || entries.size() > 256)
            throw new IllegalArgumentException("Loot table is too large");
    }

    public LootDefinition(String id, String name, Mode mode, int rolls, List<Entry> entries) {
        this(id, name, mode, rolls, "", entries);
    }

    public LootDefinition(String id, String name, Mode mode, int rolls, String choiceTitle, List<Entry> entries) {
        this(id, name, mode, rolls, choiceTitle, entries, DEFAULT_DISPLAY_ICON);
    }

    public static LootDefinition empty(String id) {
        return new LootDefinition(id, id, Mode.RANDOM, 1, "", List.of(
                new Entry("entry-1", "item", "minecraft:diamond", 1, 1, "always", true, "", "")));
    }

    public Display display() {
        return new Display(id, name, mode, rolls, choiceTitle, displayIcon,
                entries.stream().map(entry -> new DisplayEntry(entry.id(), entry.type(),
                        entry.type().equals("item") ? entry.item() : "", entry.count(), entry.weight(),
                        entry.enabled(), entry.quality(), entry.background())).toList());
    }
}
