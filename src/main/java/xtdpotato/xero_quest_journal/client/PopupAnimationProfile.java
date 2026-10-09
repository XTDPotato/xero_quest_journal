package xtdpotato.xero_quest_journal.client;

import java.util.List;

/** Bounded normalized keyframes for popup scale, translation, rotation and opacity. */
public record PopupAnimationProfile(List<Keyframe> keyframes) {
    public static final int MAX_KEYFRAMES = 16;

    public record Keyframe(double time, double scale, double x, double y,
                           double rotation, double opacity, String easing) {
        public Keyframe {
            easing = easing == null ? "smooth" : easing;
            if (!Double.isFinite(time) || time < 0 || time > 1
                    || !Double.isFinite(scale) || scale < 0 || scale > 2.5
                    || !Double.isFinite(x) || Math.abs(x) > 256
                    || !Double.isFinite(y) || Math.abs(y) > 256
                    || !Double.isFinite(rotation) || Math.abs(rotation) > 360
                    || !Double.isFinite(opacity) || opacity < 0 || opacity > 1
                    || !List.of("linear", "smooth", "ease_in", "ease_out").contains(easing))
                throw new IllegalArgumentException("Invalid popup keyframe");
        }

        public Keyframe at(double value) { return new Keyframe(value, scale, x, y, rotation, opacity, easing); }
        public Keyframe scale(double value) { return new Keyframe(time, value, x, y, rotation, opacity, easing); }
        public Keyframe x(double value) { return new Keyframe(time, scale, value, y, rotation, opacity, easing); }
        public Keyframe y(double value) { return new Keyframe(time, scale, x, value, rotation, opacity, easing); }
        public Keyframe rotation(double value) { return new Keyframe(time, scale, x, y, value, opacity, easing); }
        public Keyframe opacity(double value) { return new Keyframe(time, scale, x, y, rotation, value, easing); }
        public Keyframe easing(String value) { return new Keyframe(time, scale, x, y, rotation, opacity, value); }
    }

    public record Transform(double scale, double x, double y, double rotation, double opacity) {}

    public PopupAnimationProfile {
        keyframes = keyframes == null ? List.of() : List.copyOf(keyframes);
        if (keyframes.size() < 2 || keyframes.size() > MAX_KEYFRAMES
                || keyframes.getFirst().time() != 0 || keyframes.getLast().time() != 1)
            throw new IllegalArgumentException("Popup profile requires 2-16 keyframes including both endpoints");
        for (int i = 1; i < keyframes.size(); i++)
            if (keyframes.get(i - 1).time() >= keyframes.get(i).time())
                throw new IllegalArgumentException("Popup keyframe times must be strictly increasing");
    }

    public static PopupAnimationProfile defaults() {
        return new PopupAnimationProfile(List.of(
                new Keyframe(0, .88, 0, 8, -2, 0, "smooth"),
                new Keyframe(1, 1, 0, 0, 0, 1, "smooth")));
    }

    public Transform sample(double progress) {
        double t = Math.clamp(progress, 0, 1);
        Keyframe first = keyframes.getFirst(), last = keyframes.getLast();
        if (t <= first.time()) return transform(first);
        if (t >= last.time()) return transform(last);
        for (int i = 1; i < keyframes.size(); i++) {
            Keyframe to = keyframes.get(i);
            if (t > to.time()) continue;
            Keyframe from = keyframes.get(i - 1);
            double part = (t - from.time()) / (to.time() - from.time());
            double eased = switch (to.easing()) {
                case "linear" -> part;
                case "ease_in" -> part * part;
                case "ease_out" -> 1 - (1 - part) * (1 - part);
                default -> part * part * (3 - 2 * part);
            };
            return new Transform(mix(from.scale(), to.scale(), eased), mix(from.x(), to.x(), eased),
                    mix(from.y(), to.y(), eased), mix(from.rotation(), to.rotation(), eased),
                    mix(from.opacity(), to.opacity(), eased));
        }
        return transform(last);
    }

    private static Transform transform(Keyframe value) {
        return new Transform(value.scale(), value.x(), value.y(), value.rotation(), value.opacity());
    }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
