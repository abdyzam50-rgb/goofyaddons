package astar.pathing;

import java.util.Locale;

/**
 * The shape of a camera turn: the share of the turn made a fraction {@code t} of the way
 * through its time, 0 at the start and 1 at the end. The curves are the ones on
 * <a href="https://easings.net/">easings.net</a>, plus the minimum-jerk move the turns used
 * before.
 *
 * <p>An "in-out" curve starts and ends slowly, the way a calm hand turns. An "out" curve
 * starts at speed and slows into the target, the way a quick flick does.
 */
public enum Easing {
    /** {@code 10t³ - 15t⁴ + 6t⁵}: the smoothest start and stop; how turns were shaped before. */
    MINIMUM_JERK("minimumJerk") {
        @Override
        double curve(double t) {
            return t * t * t * (10 - 15 * t + 6 * t * t);
        }
    },
    EASE_IN_OUT_SINE("easeInOutSine") {
        @Override
        double curve(double t) {
            return -(Math.cos(Math.PI * t) - 1) / 2;
        }
    },
    EASE_IN_OUT_QUAD("easeInOutQuad") {
        @Override
        double curve(double t) {
            return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        }
    },
    EASE_IN_OUT_CUBIC("easeInOutCubic") {
        @Override
        double curve(double t) {
            return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
        }
    },
    EASE_IN_OUT_QUART("easeInOutQuart") {
        @Override
        double curve(double t) {
            return t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2;
        }
    },
    EASE_OUT_SINE("easeOutSine") {
        @Override
        double curve(double t) {
            return Math.sin(t * Math.PI / 2);
        }
    },
    EASE_OUT_QUAD("easeOutQuad") {
        @Override
        double curve(double t) {
            return 1 - (1 - t) * (1 - t);
        }
    },
    EASE_OUT_CUBIC("easeOutCubic") {
        @Override
        double curve(double t) {
            return 1 - Math.pow(1 - t, 3);
        }
    },
    EASE_OUT_QUART("easeOutQuart") {
        @Override
        double curve(double t) {
            return 1 - Math.pow(1 - t, 4);
        }
    },
    EASE_OUT_EXPO("easeOutExpo") {
        @Override
        double curve(double t) {
            return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
        }
    };

    /** The name easings.net gives it. */
    public final String label;
    /** The steepest the curve gets: its fastest speed, as a multiple of the average speed. */
    public final double peak;

    Easing(String label) {
        this.label = label;
        double steepest = 0;
        int samples = 2000;
        for (int i = 0; i < samples; i++) {
            steepest = Math.max(steepest,
                    (curve((i + 1.0) / samples) - curve((double) i / samples)) * samples);
        }
        peak = steepest;
    }

    abstract double curve(double t);

    /** The share of the turn made at {@code t} (clamped to 0..1). */
    public double at(double t) {
        return curve(Math.max(0, Math.min(1, t)));
    }

    /** How fast the share grows at {@code t}, per whole turn time. */
    public double slope(double t) {
        double h = 1e-4;
        double lo = Math.max(0, t - h);
        double hi = Math.min(1, t + h);
        return (curve(hi) - curve(lo)) / (hi - lo);
    }

    /** How fast the slope changes at {@code t}, per whole turn time squared. */
    public double bend(double t) {
        double h = 1e-3;
        double mid = Math.max(h, Math.min(1 - h, t));
        return (curve(mid + h) - 2 * curve(mid) + curve(mid - h)) / (h * h);
    }

    /** The curve called {@code name} (easings.net's name, any case), or null. */
    public static Easing named(String name) {
        String n = name.strip().toLowerCase(Locale.ROOT);
        for (Easing e : values()) {
            if (e.label.toLowerCase(Locale.ROOT).equals(n)
                    || e.name().toLowerCase(Locale.ROOT).equals(n)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Where a turn {@code total} long is a fraction {@code t} of the way through, when it began
     * turning at {@code v} (turn per whole turn time) and speeding up by {@code a} (half the
     * acceleration, per whole turn time squared): it carries that motion on and lets it die
     * away smoothly by the end, and follows this curve for the rest of the turn. With {@code v}
     * and {@code a} 0 it's just {@code total * at(t)}. Element 0 is the position, 1 the speed
     * and 2 the acceleration, all per whole turn time.
     */
    public double[] carried(double total, double v, double a, double t) {
        t = Math.max(0, Math.min(1, t));
        // The quintic that starts with the carried speed and acceleration and ends still,
        // having turned v + a; the curve makes up the rest.
        double sl = -v - 2 * a;
        double q = -2 * a;
        double h3 = -4 * sl + q / 2;
        double h4 = 7 * sl - q;
        double h5 = -3 * sl + q / 2;
        double t2 = t * t;
        double t3 = t2 * t;
        double rest = total - v - a;
        return new double[] {
            v * t + a * t2 + h3 * t3 + h4 * t3 * t + h5 * t3 * t2 + rest * at(t),
            v + 2 * a * t + 3 * h3 * t2 + 4 * h4 * t3 + 5 * h5 * t3 * t + rest * slope(t),
            2 * a + 6 * h3 * t + 12 * h4 * t2 + 20 * h5 * t3 + rest * bend(t),
        };
    }
}
