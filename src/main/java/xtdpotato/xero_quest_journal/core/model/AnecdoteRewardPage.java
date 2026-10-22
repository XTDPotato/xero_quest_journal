package xtdpotato.xero_quest_journal.core.model;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Stable oldest-first ten-record batches; visual page resets never erase completion history. */
public final class AnecdoteRewardPage {
    public static final int SIZE = 10;
    private AnecdoteRewardPage() {}
    public static List<AnecdoteSnapshot.Completed> ordered(List<AnecdoteSnapshot.Completed> records) {
        return records.stream().sorted(Comparator.comparingLong(AnecdoteSnapshot.Completed::completedAt)
                .thenComparing(AnecdoteSnapshot.Completed::entryId).thenComparing(AnecdoteSnapshot.Completed::recordId)).toList();
    }
    public static String batchId(List<AnecdoteSnapshot.Completed> records, int page) {
        int start = page * SIZE;
        if (page < 0 || start + SIZE > records.size()) return "";
        String joined = String.join("\n", records.subList(start, start + SIZE).stream()
                .map(value -> value.entryId() + "/" + value.recordId()).toList());
        return "__xero_anecdote_" + UUID.nameUUIDFromBytes(joined.getBytes(StandardCharsets.UTF_8));
    }
}
