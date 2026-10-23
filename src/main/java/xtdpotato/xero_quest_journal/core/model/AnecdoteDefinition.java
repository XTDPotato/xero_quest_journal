package xtdpotato.xero_quest_journal.core.model;

import java.util.HashSet;
import java.util.List;

/** One independently authored chronicle card and its right-page records. */
public record AnecdoteDefinition(String id, String title, String cardText, String icon, String heading,
                                 List<Record> records, boolean defaultDiscovered,
                                 boolean hideWhenLocked, int order,
                                 List<QuestDefinition.Reward> rewards, AnecdoteCardLayout cardLayout,
                                 AnecdoteRewardLayout rewardLayout) {
    public static final int MAX_RECORDS = 32;

    public record Record(String id, String title, String body, String completedIcon,
                         String generatedQuestId) {
        public Record(String id, String title, String body) { this(id, title, body, "", ""); }
        public Record(String id, String title, String body, String completedIcon) {
            this(id, title, body, completedIcon, "");
        }
        public Record {
            id = clean(id, 64);
            title = clean(title, 256);
            body = body == null ? "" : body.strip();
            completedIcon = completedIcon == null ? "" : completedIcon.strip();
            generatedQuestId = generatedQuestId == null ? "" : generatedQuestId.strip();
            if (!id.matches("[a-z0-9_.-]{1,64}") || title.isBlank() || body.length() > 8192
                    || completedIcon.length() > 2048
                    || !generatedQuestId.isBlank() && !generatedQuestId.matches("[a-z0-9_.-]{1,64}"))
                throw new IllegalArgumentException("Invalid anecdote record");
        }
    }

    public AnecdoteDefinition {
        id = clean(id, 64);
        title = clean(title, 128);
        cardText = clean(cardText, 128);
        icon = icon == null ? "" : icon.strip();
        heading = clean(heading, 256);
        records = records == null ? List.of() : List.copyOf(records);
        cardLayout = cardLayout == null ? AnecdoteCardLayout.defaults() : cardLayout;
        rewardLayout = rewardLayout == null ? AnecdoteRewardLayout.defaults() : rewardLayout;
        rewards = rewards == null || rewards.isEmpty()
                ? List.of(new QuestDefinition.Reward("item", "minecraft:diamond", 50, "legendary", ""))
                : List.copyOf(rewards);
        if (!id.matches("[a-z0-9_.-]{1,64}") || title.isBlank() || cardText.isBlank()
                || icon.length() > 2048 || heading.length() > 256 || records.size() > MAX_RECORDS
                || order < 0 || order > 1_000_000)
            throw new IllegalArgumentException("Invalid anecdote definition");
        var ids = new HashSet<String>();
        for (Record record : records)
            if (record == null || !ids.add(record.id()))
                throw new IllegalArgumentException("Duplicate or missing anecdote record ID");
    }

    public AnecdoteDefinition(String id, String title, String cardText, String icon, String heading,
                              List<Record> records, boolean defaultDiscovered,
                              boolean hideWhenLocked, int order, List<QuestDefinition.Reward> rewards) {
        this(id, title, cardText, icon, heading, records, defaultDiscovered, hideWhenLocked, order,
                rewards, AnecdoteCardLayout.defaults(), AnecdoteRewardLayout.defaults());
    }
    public AnecdoteDefinition(String id, String title, String cardText, String icon, String heading,
                              List<Record> records, boolean defaultDiscovered, boolean hideWhenLocked,
                              int order, List<QuestDefinition.Reward> rewards, AnecdoteCardLayout cardLayout) {
        this(id, title, cardText, icon, heading, records, defaultDiscovered, hideWhenLocked, order,
                rewards, cardLayout, AnecdoteRewardLayout.defaults());
    }

    public AnecdoteDefinition withRecords(List<Record> next) {
        return new AnecdoteDefinition(id, title, cardText, icon, heading, next, defaultDiscovered,
                hideWhenLocked, order, rewards, cardLayout, rewardLayout);
    }
    public AnecdoteDefinition withRewards(List<QuestDefinition.Reward> next) {
        return new AnecdoteDefinition(id, title, cardText, icon, heading, records, defaultDiscovered,
                hideWhenLocked, order, next, cardLayout, rewardLayout);
    }
    public AnecdoteDefinition withRewardLayout(AnecdoteRewardLayout next) {
        return new AnecdoteDefinition(id, title, cardText, icon, heading, records, defaultDiscovered,
                hideWhenLocked, order, rewards, cardLayout, next);
    }

    public AnecdoteDefinition(String id, String title, String cardText, String icon, String heading,
                              List<Record> records, boolean defaultDiscovered,
                              boolean hideWhenLocked, int order) {
        this(id, title, cardText, icon, heading, records, defaultDiscovered, hideWhenLocked, order,
                List.of(new QuestDefinition.Reward("item", "minecraft:diamond", 50, "legendary", "")));
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        String cleaned = value.strip();
        if (cleaned.length() > maximum) throw new IllegalArgumentException("Anecdote text exceeds limit");
        return cleaned;
    }
}
