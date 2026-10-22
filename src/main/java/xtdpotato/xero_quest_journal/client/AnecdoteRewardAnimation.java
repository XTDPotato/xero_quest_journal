package xtdpotato.xero_quest_journal.client;

/** Sequential stamping and a separate full-page clear; independent of frame rate and window size. */
final class AnecdoteRewardAnimation {
    static final long STAMP = 420_000_000L, CLEAR = 500_000_000L;
    private int displayed, target, entering = -1;
    private long started, clearing;
    AnecdoteRewardAnimation(int displayed) { this.displayed = target = Math.max(0, displayed); }
    void target(int count) { target = Math.max(displayed, count); }
    void advance(long now, boolean reducedMotion) {
        if (reducedMotion) { displayed = target; entering = -1; clearing = 0; return; }
        if (clearing > 0) { if (now - clearing < CLEAR) return; clearing = 0; }
        if (entering >= 0) {
            if (now - started < STAMP) return;
            displayed++; entering = -1;
            if (displayed % 10 == 0) { clearing = now; return; }
        }
        if (displayed < target) { entering = displayed; started = now; }
    }
    int displayed() { return displayed; }
    int page() { return clearing > 0 ? Math.max(0, displayed / 10 - 1) : displayed / 10; }
    int entering() { return entering; }
    int countOnPage() { return clearing > 0 ? 10 : displayed % 10; }
    float stamp(long now) { return entering < 0 ? 1 : Math.clamp((now - started) / (float) STAMP, 0, 1); }
    float clear(long now) { return clearing == 0 ? 0 : Math.clamp((now - clearing) / (float) CLEAR, 0, 1); }
}
