package astar.movement.exec;

import astar.pathing.Easing;
import astar.pathing.Tuning;
import java.util.SplittableRandom;

/**
 * Turns the camera toward a target yaw the way a steady hand on a mouse would: a critically
 * damped turn, capped in rate, moved in whole mouse counts.
 *
 * <p>It runs once per rendered frame. Each frame it works out the turn it wants, clamps the
 * turn rate, and converts the turn into mouse counts at the game's sensitivity, carrying the
 * fraction of a count left over to the next frame, as a real mouse would. Given how fast the
 * target itself is turning (the follower's curve), it leads the target instead of trailing
 * it by the damping lag.
 *
 * <p>A big turn all at once (the target jumping by more than {@link Settings#flick}) is made
 * the way people move a hand to a target: a sweep shaped by an easing curve ({@link
 * Settings#ease}, easeInOutSine unless told, which eases in and out; a very big turn, past
 * {@link Settings#snap}, is a flick shaped by {@link Settings#snapEase}, easeOutSine, which
 * starts at speed and slows into the target); the time grows with the turn by Fitts's law;
 * and it falls a little short, leaving the rest to the steady turn after it. A sweep started
 * mid-turn carries that turn on and lets it die away smoothly, a target that drifts a little
 * stretches the sweep's reach, and one that jumps starts a new sweep from the turn under way;
 * the steady turn speeds up at most {@link Settings#maxAccel}, so a target that jumps a little
 * doesn't jerk the camera either.
 */
public final class AimController {

    /**
     * Which hand moves the mouse. A hand sweeps outward (to the right with the right hand) a
     * little quicker and further than across the body, and a sweep isn't quite level: the
     * wrist and elbow swing the mouse on an arc, so an outward sweep tips the view up a little
     * and an inward one down, and the pitch then comes back.
     */
    public enum Hand {
        RIGHT(1), LEFT(-1);

        /** +1 when turning right is outward. */
        final int outward;

        Hand(int outward) {
            this.outward = outward;
        }

        /** The hand named, or null. */
        public static Hand named(String name) {
            for (Hand h : values()) {
                if (h.name().equalsIgnoreCase(name.strip())) {
                    return h;
                }
            }
            return null;
        }
    }

    /**
     * How the aim behaves.
     *
     * @param omega how stiffly it closes on the target, in radians per second; the turn
     *     settles in about {@code 4 / omega} seconds
     * @param maxRate the fastest it turns, in degrees per second
     * @param degreesPerCount how far one mouse count turns the camera: the game's {@code 0.15
     *     * 8 * (0.6 * sensitivity + 0.2)^3}, 0.15 at the default sensitivity of 0.5; 0 turns
     *     smoothly with no counts
     * @param flick turns bigger than this, in degrees, are swept
     * @param flickBase a sweep's shortest time, in seconds
     * @param flickPerBit how much longer per doubling of the turn (Fitts's law: {@code
     *     base + perBit * log2(1 + turn / FLICK_WIDTH)})
     * @param undershoot how much of the turn the sweep makes
     * @param maxAccel how quickly the steady turn can speed up or slow down, in degrees per
     *     second per second, so a target that jumps doesn't jerk the camera
     * @param ease the shape of a sweep (see <a href="https://easings.net/">easings.net</a>)
     * @param snap sweeps bigger than this, in degrees, are flicks
     * @param snapEase the shape of a flick
     */
    public record Settings(double omega, double maxRate, double degreesPerCount,
            double flick, double flickBase, double flickPerBit, double undershoot,
            double maxAccel, Easing ease, double snap, Easing snapEase) {

        public static final Settings DEFAULT = new Settings(25, 540, degreesPerCount(0.5),
                30, 0.1, 0.07, 0.95, 4000, Easing.EASE_IN_OUT_SINE, 75, Easing.EASE_OUT_SINE);

        /** No big-turn sweeps: always the steady turn. */
        public Settings(double omega, double maxRate, double degreesPerCount) {
            this(omega, maxRate, degreesPerCount, Double.POSITIVE_INFINITY, 0, 0, 1,
                    Double.POSITIVE_INFINITY);
        }

        /** Steady turns as fast to change as the spring asks. */
        public Settings(double omega, double maxRate, double degreesPerCount, double flick,
                double flickBase, double flickPerBit, double undershoot) {
            this(omega, maxRate, degreesPerCount, flick, flickBase, flickPerBit, undershoot,
                    Double.POSITIVE_INFINITY);
        }

        /** Sweeps and flicks shaped as before these settings had curves. */
        public Settings(double omega, double maxRate, double degreesPerCount, double flick,
                double flickBase, double flickPerBit, double undershoot, double maxAccel) {
            this(omega, maxRate, degreesPerCount, flick, flickBase, flickPerBit, undershoot,
                    maxAccel, Easing.MINIMUM_JERK, Double.POSITIVE_INFINITY,
                    Easing.MINIMUM_JERK);
        }

        /** The same, with sweeps shaped by {@code ease} and flicks by {@code snapEase}. */
        public Settings withEasing(Easing ease, Easing snapEase) {
            return new Settings(omega, maxRate, degreesPerCount, flick, flickBase, flickPerBit,
                    undershoot, maxAccel, ease, snap, snapEase);
        }

        /** Degrees per mouse count at a sensitivity setting (0 to 1, default 0.5). */
        public static double degreesPerCount(double sensitivity) {
            double f = sensitivity * 0.6 + 0.2;
            return f * f * f * 8.0 * 0.15;
        }
    }

    /** The target's size in Fitts's law, in degrees: how close a sweep has to land. */
    private static final double FLICK_WIDTH = 5;
    /**
     * The fastest a sweep turns, in degrees a second: a target that jumps late in a sweep
     * stretches the sweep rather than being closed in what's left of its time.
     */
    private static final double SWEEP_RATE = 600;
    /** The same for a flick, which peaks at its start. */
    private static final double SNAP_RATE = 1200;
    /**
     * A target that moves further than this, in degrees, from where a sweep was headed starts
     * a new sweep from the turn under way; less, and the sweep reaches a little further.
     */
    private static final double RETARGET = 10;
    /** The shortest sweep, in seconds. */
    private static final double MIN_SWEEP = 0.1;
    /** How many times quicker the steady turn can slow than speed up. */
    private static final double BRAKE = 3;

    // The share of big turns a hand carries past the target and then brings back (outward
    // sweeps more often than those across the body) is the player's: Tuning.OVERSHOOT_OUT and
    // OVERSHOOT_IN.
    /** How much quicker an outward sweep is, and slower an inward one. */
    private static final double OUTWARD_QUICKER = 0.08;
    /** How far a sweep tips the pitch, in degrees per degree turned (up outward). */
    private static final double ARC_TILT = 0.04;
    /** How quickly the pitch comes back after a sweep's tip, in seconds. */
    private static final double TILT_BACK = 0.3;
    /** How much slower it lets a paused hand's turn die away, in seconds. */
    private static final double PAUSE_EASE = 0.04;
    /**
     * A held hand comes to rest once this close to the target, in degrees, and turning slower
     * than {@link #REST_RATE} degrees a second.
     */
    private static final double REST_ERROR = 0.75;
    private static final double REST_RATE = 15;

    private final Settings settings;
    /** Where a hand's unevenness comes from; null for the same turn every time. */
    private SplittableRandom hand;
    /** Whether a big turn may wait a moment before it starts, as a person takes to react. */
    private boolean mayPause;
    /** How long the hand still waits before the sweep it's about to make, in seconds. */
    private double pause;
    private Hand handed = Hand.RIGHT;
    /** How far the sweeps have tipped the pitch, in degrees (positive looks down). */
    private double tilt;
    private double rate;
    /** How fast the rate itself is changing, in degrees per second per second. */
    private double accel;
    private double leftover;
    /** The sweep in progress: how far short of the target it stops, how long, how far in. */
    private boolean sweeping;
    private double sweepShort;
    private double sweepTime;
    private double sweepAt;
    /** Its shape, the turn it set out to make, and how far it has turned so far, in degrees. */
    private Easing sweepEase = Easing.MINIMUM_JERK;
    private double sweepTo;
    private double swept;
    /** The turn it started with, per sweep time: speed, and half the acceleration. */
    private double sweepV;
    private double sweepA;
    /** How far the target may stray before a resting hand moves, in degrees; 0 never rests. */
    private double hold;
    /** Whether the hand is resting: the camera still until the target strays past {@link #hold}. */
    private boolean resting;
    /** See {@link #gentle}. */
    private double gentle;

    public AimController(Settings settings) {
        this.settings = settings;
    }

    /**
     * Makes its turns uneven the way a hand's are, drawing from {@code hand}: each big turn's
     * time and reach differ, some go a little past and come back, and (when {@link
     * #mayPause}) it may wait a moment before starting one. Null makes every turn the same.
     */
    public void hand(SplittableRandom hand) {
        this.hand = hand;
    }

    /** Sets which hand moves the mouse (the right one unless told). */
    public void handed(Hand hand) {
        handed = hand;
    }

    /**
     * How far the sweeps tip the view off level, in degrees (positive looks down), to add to
     * the pitch wanted; it dies away after a sweep. Always 0 with no {@link #hand}.
     */
    public double tilt() {
        return tilt;
    }

    /** Whether the next big turn may wait a moment first: only when a late start costs nothing. */
    public void mayPause(boolean may) {
        mayPause = may;
        if (!may) {
            pause = 0;
        }
    }

    /**
     * Lets the hand rest: once it has caught up with the target it stays still, as a hand
     * on a mouse does, until the target strays more than {@code degrees} from where it
     * points, and then it corrects in one sweep instead of following every small drift. 0
     * follows the target all the time.
     */
    public void hold(double degrees) {
        hold = degrees;
        if (degrees <= 0) {
            resting = false;
        }
    }

    /**
     * Caps how quickly the steady turn speeds up, in degrees a second squared, below the
     * settings' own cap (0 or less: the settings' cap alone), so a target that steps doesn't
     * snap the camera round where a sharp turn isn't needed.
     */
    public void gentle(double maxAccel) {
        gentle = maxAccel;
    }

    /** Whether the hand is resting now (see {@link #hold}). */
    public boolean resting() {
        return resting;
    }

    /** The current turn rate, in degrees per second. */
    public double rate() {
        return rate;
    }

    /** Stops any turn in progress, as when the player takes over. */
    public void reset() {
        rate = 0;
        accel = 0;
        leftover = 0;
        sweeping = false;
        pause = 0;
        tilt = 0;
        resting = false;
    }

    /** Carries on the turn another controller was making, as when a new plan takes over. */
    public void carry(AimController from) {
        rate = from.rate;
        accel = from.accel;
        leftover = from.leftover;
        sweeping = from.sweeping;
        sweepShort = from.sweepShort;
        sweepTime = from.sweepTime;
        sweepAt = from.sweepAt;
        sweepEase = from.sweepEase;
        sweepTo = from.sweepTo;
        swept = from.swept;
        sweepV = from.sweepV;
        sweepA = from.sweepA;
        pause = from.pause;
        tilt = from.tilt;
        hold = from.hold;
        resting = from.resting;
        handed = from.handed;
        if (from.hand != null) {
            hand = from.hand;
        }
    }

    /** Whether a big turn is being swept now. */
    public boolean sweeping() {
        return sweeping;
    }

    /**
     * One frame: returns the camera yaw after it.
     *
     * @param yaw the camera's yaw now
     * @param target the yaw wanted (any turn: it goes the short way round)
     * @param targetRate how fast the target is turning, in degrees per second
     * @param dt the frame's length in seconds
     */
    public float frame(float yaw, double target, double targetRate, double dt) {
        double error = wrap(target - yaw);
        if (sweeping && sweepTime - sweepAt <= dt / 2) {
            // Its time is up: from here, at the rate it's turning, it sweeps again if the
            // target has moved far meanwhile, or turns steadily the rest of the way.
            sweeping = false;
        }
        if (sweeping && sweepAt > 0 && pause <= 0
                && Math.abs(error + swept - sweepShort - sweepTo) > RETARGET) {
            // The target jumped: a new sweep from here, carrying on the turn under way.
            sweeping = false;
            if (Math.abs(error) > settings.flick()) {
                sweeping = true;
                plan(error, false);
            }
        }
        if (hold > 0 && !sweeping) {
            if (resting && Math.abs(error) <= hold) {
                // Still: the target hasn't strayed far enough to move for.
                return finish(yaw, 0, error, targetRate, dt);
            }
            if (resting) {
                // Strayed too far: one correcting sweep, as a hand moves in a single go.
                resting = false;
                sweeping = true;
                plan(error, true);
            } else if (Math.abs(error) < REST_ERROR && Math.abs(rate) < REST_RATE
                    && Math.abs(targetRate) < REST_RATE) {
                resting = true;
                rate = 0;
                accel = 0;
                return finish(yaw, 0, error, targetRate, dt);
            }
        }
        if (!sweeping && Math.abs(error) > settings.flick()) {
            sweeping = true;
            plan(error, true);
        }
        double turn;
        if (sweeping && pause > 0) {
            // Taking it in before moving: whatever turn there was dies away.
            pause -= dt;
            rate *= Math.exp(-dt / PAUSE_EASE);
            accel = 0;
            turn = rate * dt;
        } else if (sweeping) {
            if (sweepAt == 0) {
                // Starting off: time it so the carried turn and the curve's fastest point
                // stay in reach of a hand, then set off from the turn under way.
                double to = error - sweepShort;
                double left = sweepTime;
                if (rate * to > 0) {
                    // Already turning that way: soon enough that the carried speed doesn't
                    // swing it past (it doesn't while speed x time is under 2.5 x turn).
                    left = Math.max(MIN_SWEEP, Math.min(left, 2.5 * Math.abs(to / rate)));
                }
                double cap = sweepEase == settings.snapEase() && sweepEase != settings.ease()
                        ? SNAP_RATE : SWEEP_RATE;
                sweepTime = Math.max(left, sweepEase.peak * Math.abs(to) / cap);
                sweepTo = to;
                swept = 0;
                sweepV = rate * sweepTime;
                sweepA = accel * sweepTime * sweepTime / 2;
            }
            // Small drifts of the target widen or narrow the turn's reach as it goes.
            sweepTo = error + swept - sweepShort;
            double t = Math.min(1, (sweepAt + dt) / sweepTime);
            double[] at = sweepEase.carried(sweepTo, sweepV, sweepA, t);
            turn = at[0] - swept;
            swept = at[0];
            rate = at[1] / sweepTime;
            accel = at[2] / (sweepTime * sweepTime);
            sweepAt += dt;
        } else {
            double w = settings.omega();
            double was = rate;
            double want = w * w * error + 2 * w * (targetRate - rate);
            // Slowing down may be quicker than speeding up: a hand stops sooner than it starts.
            double most = gentle > 0 ? Math.min(gentle, settings.maxAccel()) : settings.maxAccel();
            double cap = most * (want * rate < 0 ? BRAKE : 1);
            rate += Math.max(-cap, Math.min(cap, want)) * dt;
            rate = Math.max(-settings.maxRate(), Math.min(settings.maxRate(), rate));
            accel = (rate - was) / dt;
            turn = rate * dt;
        }
        return finish(yaw, turn, error, targetRate, dt);
    }

    /**
     * Sets up a sweep through {@code error} degrees: its shape, time and reach, and (on a
     * fresh start, {@code fresh}) maybe a moment's wait first.
     */
    private void plan(double error, boolean fresh) {
        sweepShort = error * (1 - settings.undershoot());
        sweepAt = 0;
        sweepEase = Math.abs(error) > settings.snap() ? settings.snapEase() : settings.ease();
        sweepTime = settings.flickBase() + settings.flickPerBit()
                * Math.log(1 + Math.abs(error) / FLICK_WIDTH) / Math.log(2);
        if (hand != null) {
            // No two alike: a little quicker or slower, mostly short of the target, now
            // and then a few degrees past it, which the steady turn then brings back.
            boolean out = error * handed.outward > 0;
            double over = out ? Tuning.OVERSHOOT_OUT.get() : Tuning.OVERSHOOT_IN.get();
            double reach = hand.nextDouble() < over
                    ? 1.02 + 0.05 * hand.nextDouble()
                    : 0.86 + 0.11 * hand.nextDouble();
            sweepShort = error * (1 - reach);
            sweepTime *= (0.85 + 0.35 * hand.nextDouble())
                    * (out ? 1 - OUTWARD_QUICKER : 1 + OUTWARD_QUICKER);
            if (fresh && mayPause && Math.abs(rate) < 30) {
                pause = 0.04 + 0.12 * hand.nextDouble();
            }
        }
    }

    /** Tips the pitch, keeps the turn from overshooting, and moves in whole mouse counts. */
    private float finish(float yaw, double turn, double error, double targetRate, double dt) {
        if (hand != null) {
            // The arc the mouse moves on: up a little while sweeping outward, down while
            // sweeping across the body; back to level after.
            if (sweeping) {
                tilt -= ARC_TILT * turn * handed.outward;
            }
            tilt *= Math.exp(-dt / TILT_BACK);
        }
        // Never turn past the target in one frame: that's where a stiff spring would wobble.
        if (!sweeping && Math.abs(turn) > Math.abs(error)
                && Math.signum(turn) == Math.signum(error) && Math.abs(targetRate) < 1e-9) {
            turn = error;
            rate = 0;
            accel = 0;
        }
        double step = settings.degreesPerCount();
        if (step <= 0) {
            return (float) (yaw + turn);
        }
        double wanted = turn + leftover;
        long counts = Math.round(wanted / step);
        leftover = wanted - counts * step;
        // The game adds each mouse move to the float yaw.
        return (float) (yaw + counts * step);
    }

    /** An angle in degrees brought into [-180, 180). */
    public static double wrap(double degrees) {
        double d = (degrees + 180) % 360;
        if (d < 0) {
            d += 360;
        }
        return d - 180;
    }
}
