package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

/** A reusable, server-authored loot table used by task rewards and graph actions. */
public record LootDefinition(String id, String name, Mode mode, int rolls, String choiceTitle, List<Entry> entries) {
    public enum Mode {
        RANDOM,
        CHOICE
    }

    public record Entry(String id, String type, String item, int count, int weight,
                        String condition, boolean enabled) {
        public Entry {
            id = id == null || id.isBlank() ? "entry" : id;
            type = type == null || type.isBlank() ? "item" : type;
            item = item == null ? "" : item;
            condition = condition == null || condition.isBlank() ? "always" : condition;
            if (count < 1 || count > 1000000) throw new IllegalArgumentException("Loot count must be 1..1000000");
            if (weight < 0 || weight > 1000000) throw new IllegalArgumentException("Loot weight must be 0..1000000");
            if (id.length() > 64 || type.length() > 32 || item.length() > 8192 || condition.length() > 512)
                throw new IllegalArgumentException("Loot entry is too large");
        }

        public QuestDefinition.Reward reward() {
            return new QuestDefinition.Reward(type, item, count);
        }
    }

    public LootDefinition {
        id = id == null || id.isBlank() ? "unnamed" : id;
        name = name == null ? id : name;
        choiceTitle = choiceTitle == null ? "" : choiceTitle;
        mode = mode == null ? Mode.RANDOM : mode;
        if (rolls < 1 || rolls > 128) throw new IllegalArgumentException("Loot rolls must be 1..128");
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (id.length() > 128 || name.length() > 256 || choiceTitle.length() > 256 || entries.size() > 256)
            throw new IllegalArgumentException("Loot table is too large");
    }

    public LootDefinition(String id, String name, Mode mode, int rolls, List<Entry> entries) {
        this(id, name, mode, rolls, "", entries);
    }

    public static LootDefinition empty(String id) {
        return new LootDefinition(id, id, Mode.RANDOM, 1, "", List.of(
                new Entry("entry-1", "item", "minecraft:diamond", 1, 1, "always", true)));
    }
}
