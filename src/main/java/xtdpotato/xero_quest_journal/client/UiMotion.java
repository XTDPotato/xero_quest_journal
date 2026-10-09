package xtdpotato.xero_quest_journal.client;

/** Reversible, monotonic-time motion. Changing direction preserves the current position. */
public final class UiMotion {
    private double position;
    private boolean visible;
    private long lastNanos;

    public UiMotion(boolean visible, long now) {
        this.visible = visible;
        position = visible ? 1 : 0;
        lastNanos = now;
    }

    public double advance(long now, int durationMillis) {
        double step = durationMillis <= 0 ? 1 : Math.max(0, now - lastNanos) / (durationMillis * 1_000_000.0);
        lastNanos = now;
        position = visible ? Math.min(1, position + step) : Math.max(0, position - step);
        return position;
    }

    public void target(boolean value, long now, int durationMillis) {
        advance(now, durationMillis);
        visible = value;
        if (durationMillis <= 0) position = value ? 1 : 0;
    }

    public double position() { return position; }
    public boolean visible() { return visible; }
    public boolean settled() { return position == (visible ? 1 : 0); }
    public float eased() { return (float) (position * position * (3 - 2 * position)); }
}
