package xtdpotato.xero_quest_journal.core.model;

import java.util.List;

/** Server-authored chronicle definitions combined with one player's discovery state. */
public record AnecdoteSnapshot(List<AnecdoteDefinition> entries, List<String> discovered, List<String> unread,
                               List<Completed> completed, List<String> newCompletions) {
    public record Completed(String entryId, String recordId, long completedAt) {}

    public AnecdoteSnapshot(List<AnecdoteDefinition> entries, List<String> discovered, List<String> unread) {
        this(entries, discovered, unread, List.of(), List.of());
    }

    public AnecdoteSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
        discovered = discovered == null ? List.of() : List.copyOf(discovered);
        unread = unread == null ? List.of() : List.copyOf(unread);
        completed = completed == null ? List.of() : List.copyOf(completed);
        newCompletions = newCompletions == null ? List.of() : List.copyOf(newCompletions);
        if (entries.size() > 256 || discovered.size() > 256 || unread.size() > 256
                || completed.size() > 2048 || newCompletions.size() > 2048)
            throw new IllegalArgumentException("Anecdote snapshot exceeds limits");
    }

    public static AnecdoteSnapshot empty() {
        return new AnecdoteSnapshot(List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
