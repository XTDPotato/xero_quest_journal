package xtdpotato.xero_quest_journal.client;

/** Prevents coordinate marker animations from overlapping; replay is allowed at the end frame. */
public final class CoordinateTrackAnimation {
    public static final long DURATION_NANOS = 1_000_000_000L;

    private String questId = "";
    private long startedAt;

    public boolean play(String id, long now) {
        if (id == null || id.isBlank() || isPlaying(now)) return false;
        questId = id;
        startedAt = now;
        return true;
    }

    public boolean isPlaying(long now) {
        return !questId.isBlank() && now - startedAt < DURATION_NANOS;
    }

    public float progress(String id, long now) {
        if (!isPlaying(now) || !questId.equals(id)) return 0;
        return Math.clamp((now - startedAt) / (float) DURATION_NANOS, 0, 1);
    }
}
