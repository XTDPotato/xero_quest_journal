package xtdpotato.xero_quest_journal.core.model;

import java.util.HashSet;
import java.util.List;

/** One independently authored chronicle card and its right-page records. */
public record AnecdoteDefinition(String id, String title, String cardText, String icon, String heading,
                                 List<Record> records, boolean defaultDiscovered,
                                 boolean hideWhenLocked, int order) {
    public static final int MAX_RECORDS = 32;

    public record Record(String id, String title, String body, String completedIcon) {
        public Record(String id, String title, String body) { this(id, title, body, ""); }
        public Record {
            id = clean(id, 64);
            title = clean(title, 256);
            body = body == null ? "" : body.strip();
            completedIcon = completedIcon == null ? "" : completedIcon.strip();
            if (!id.matches("[a-z0-9_.-]{1,64}") || title.isBlank() || body.length() > 8192
                    || completedIcon.length() > 2048)
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
        if (!id.matches("[a-z0-9_.-]{1,64}") || title.isBlank() || cardText.isBlank()
                || icon.length() > 2048 || heading.length() > 256 || records.size() > MAX_RECORDS
                || order < 0 || order > 1_000_000)
            throw new IllegalArgumentException("Invalid anecdote definition");
        var ids = new HashSet<String>();
        for (Record record : records)
            if (record == null || !ids.add(record.id()))
                throw new IllegalArgumentException("Duplicate or missing anecdote record ID");
    }

    private static String clean(String value, int maximum) {
        if (value == null) return "";
        String cleaned = value.strip();
        if (cleaned.length() > maximum) throw new IllegalArgumentException("Anecdote text exceeds limit");
        return cleaned;
    }
}
