package xtdpotato.xero_quest_journal.client;

/** Bounded frame-time scrolling with direct pointer tracking and a short inertial release. */
final class JourneyScroll {
    private double position, target, minimum, maximum, velocity;
    private boolean dragging;
    private long lastDrag;

    double position() { return position; }
    double target() { return target; }
    double maximum() { return maximum; }
    double minimum() { return minimum; }
    void limit(double value) {
        range(0, Math.max(0, value));
    }
    void range(double min, double max) {
        minimum = min;
        maximum = Math.max(min, max);
        position = Math.clamp(position, minimum - 72, maximum + 72);
        target = Math.clamp(target, minimum, maximum);
    }
    void jump(double value) {
        position = target = Math.clamp(value, minimum, maximum);
        velocity = 0;
        dragging = false;
    }
    void to(double value) { target = Math.clamp(value, minimum, maximum); velocity = 0; }
    void by(double value) { to(target + value); }
    void shift(double value) {
        position = Math.clamp(position + value, minimum, maximum);
        target = Math.clamp(target + value, minimum, maximum);
    }
    void begin() {
        dragging = true;
        target = position;
        velocity = 0;
        lastDrag = System.nanoTime();
    }
    void drag(double value) {
        long now = System.nanoTime();
        double next = value < minimum ? minimum - resistance(minimum - value)
                : value > maximum ? maximum + resistance(value - maximum) : value;
        double dt = Math.clamp((now - lastDrag) / 1e9, .008, .08);
        velocity = .35 * velocity + .65 * Math.clamp((next - position) / dt, -1800, 1800);
        position = target = next;
        lastDrag = now;
    }
    private double resistance(double excess) { return 72 * excess / (160 + excess); }
    void release(boolean moved) {
        dragging = false;
        if (!moved || System.nanoTime() - lastDrag > 100_000_000L) velocity = 0;
        target = Math.clamp(position, minimum, maximum);
        if (position < minimum || position > maximum) velocity = 0;
    }
    void advance(double seconds, boolean reducedMotion) {
        if (dragging) return;
        if (reducedMotion) { position = target; velocity = 0; return; }
        if (position < minimum || position > maximum) {
            target = Math.clamp(target, minimum, maximum);
            position += (target - position) * -Math.expm1(-16 * seconds);
            if (Math.abs(position - target) < .02) position = target;
            velocity = 0;
            return;
        }
        if (Math.abs(velocity) > 1) {
            double decay = Math.exp(-8 * seconds);
            double next = target + velocity * (1 - decay) / 8;
            target = Math.clamp(next, minimum, maximum);
            position = next < minimum ? minimum - resistance(minimum - next)
                    : next > maximum ? maximum + resistance(next - maximum) : target;
            velocity *= decay;
            if (position == minimum || position == maximum) velocity = 0;
        } else {
            velocity = 0;
            position += (target - position) * -Math.expm1(-16 * seconds);
            if (Math.abs(position - target) < .02) position = target;
        }
    }
}
