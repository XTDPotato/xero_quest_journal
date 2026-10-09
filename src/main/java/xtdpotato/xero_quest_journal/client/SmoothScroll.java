package xtdpotato.xero_quest_journal.client;

/** Pixel-based scroll state shared by menus and authoring lists. */
final class SmoothScroll {
    private double position, target, maximum;
    private long frame;
    double position() { return position; }
    double maximum() { return maximum; }
    double target() { return target; }
    int pixels() { return (int) Math.round(position); }
    void limit(double maximum) {
        this.maximum = Math.max(0, maximum);
        position = Math.clamp(position, 0, this.maximum);
        target = Math.clamp(target, 0, this.maximum);
    }
    void jump(double value) { position = target = Math.clamp(value, 0, maximum); }
    void to(double value) { target = Math.clamp(value, 0, maximum); }
    void wheel(double delta) { target = Math.clamp(target - delta, 0, maximum); }
    void freeze() { target = position; }
    void advance() {
        long now = System.nanoTime();
        double dt = frame == 0 ? 1. / 60 : Math.min(.1, (now - frame) / 1e9);
        frame = now;
        advance(dt);
    }
    void advance(double seconds) {
        double dt = Math.clamp(seconds, 0, .1);
        position += (target - position) * (1 - Math.exp(-18 * dt));
        if (Math.abs(position - target) < .05) position = target;
    }
}
