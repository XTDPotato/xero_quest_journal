package xtdpotato.xero_quest_journal.client;

/** Horizontal or vertical pointer scroll with bounded overshoot and a damped release. */
final class ElasticScroll {
    private double position, maximum, velocity, target;
    private boolean dragging;
    private long lastDrag, frame;

    double position() { return position; }
    int pixels() { return (int) Math.round(position); }
    double maximum() { return maximum; }
    void limit(double value) {
        maximum = Math.max(0, value);
        target = Math.clamp(target, 0, maximum);
        if (!dragging && position > maximum + 70) position = maximum + 70;
    }
    void jump(double value) {
        position = target = Math.clamp(value, 0, maximum);
        velocity = 0;
        dragging = false;
    }
    void by(double distance) {
        target = Math.clamp(target + distance, 0, maximum);
        velocity = 0;
    }
    void to(double value) { target = Math.clamp(value, 0, maximum); velocity = 0; }
    void wheel(double delta) { by(-delta); }
    void freeze() {
        dragging = false;
        target = Math.clamp(position, 0, maximum);
        velocity = 0;
    }
    void begin() {
        dragging = true;
        velocity = 0;
        lastDrag = System.nanoTime();
    }
    void drag(double value) {
        long now = System.nanoTime();
        double next = value < 0 ? -Math.min(55, -value * .32)
                : value > maximum ? maximum + Math.min(55, (value - maximum) * .32) : value;
        double dt = Math.clamp((now - lastDrag) / 1e9, .008, .08);
        velocity = .25 * velocity + .75 * Math.clamp((next - position) / dt, -2200, 2200);
        position = next;
        target = Math.clamp(next, 0, maximum);
        lastDrag = now;
    }
    void release(boolean moved) {
        dragging = false;
        if (!moved || System.nanoTime() - lastDrag > 120_000_000L) velocity = 0;
    }
    void advance() {
        long now = System.nanoTime();
        double dt = frame == 0 ? 1. / 60 : (now - frame) / 1e9;
        frame = now;
        advance(dt, false);
    }
    void advance(double dt, boolean reducedMotion) {
        if (dragging || dt <= 0) return;
        if (reducedMotion) { position = target = Math.clamp(target, 0, maximum); velocity = 0; return; }
        dt = Math.min(dt, .05);
        double edge = Math.clamp(position, 0, maximum);
        if (position != edge) {
            // Critically damped enough for one small bounce without lingering at the edge.
            velocity += (-125 * (position - edge) - 18 * velocity) * dt;
            position += velocity * dt;
            if (Math.abs(position - edge) < .15 && Math.abs(velocity) < 5) {
                position = edge;
                velocity = 0;
            }
        } else if (Math.abs(velocity) > 1) {
            double decay = Math.exp(-7 * dt);
            position += velocity * (1 - decay) / 7;
            velocity *= decay;
            if (Math.abs(velocity) <= 1) {
                velocity = 0;
                target = Math.clamp(position, 0, maximum);
            }
        } else {
            velocity = 0;
            position += (target - position) * -Math.expm1(-16 * dt);
            if (Math.abs(position - target) < .05) position = target;
        }
        if (position < 0 || position > maximum) target = Math.clamp(position, 0, maximum);
    }
}
