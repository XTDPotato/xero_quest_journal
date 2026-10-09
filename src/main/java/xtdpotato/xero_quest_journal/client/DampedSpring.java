package xtdpotato.xero_quest_journal.client;

/** Exact critically damped integration for a position settling at a moving boundary. */
final class DampedSpring {
    private DampedSpring() {}

    static State advance(double position, double velocity, double target, double seconds, double frequency) {
        if (seconds <= 0) return new State(position, velocity);
        double elapsed = Math.min(seconds, 0.05);
        double offset = position - target;
        double omega = Math.max(0.01, frequency);
        double coefficient = velocity + omega * offset;
        double decay = Math.exp(-omega * elapsed);
        double nextOffset = (offset + coefficient * elapsed) * decay;
        double nextVelocity = (velocity - omega * coefficient * elapsed) * decay;
        if (Math.abs(nextOffset) < 0.005 && Math.abs(nextVelocity) < 0.05) {
            return new State(target, 0);
        }
        return new State(target + nextOffset, nextVelocity);
    }

    record State(double position, double velocity) {}
}
