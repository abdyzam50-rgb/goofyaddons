package astar.client;

import astar.pathing.Easing;
import astar.pathing.HandTurn;
import astar.pathing.Tuning;
import java.util.Random;

/**
 * Turns the view onto a cast a tick at a time like a hand on a mouse, ported from the old
 * pathfinder's HumanRotation: every turn lasts as long as Fitts's law says ({@link
 * HandTurn#turnTicks}), a little different each time, quicker mid-air, and follows an easing
 * curve ({@link AstarConfig}): on the ground one that eases in and out (easeInOutSine unless
 * chosen), mid-air a flick that starts at speed and slows into the target (easeOutSine). A
 * turn begun while the view is still moving carries that motion on, a target that shifts a
 * little stretches the turn's reach, and one that jumps starts a new, properly timed turn.
 * Once the curve has run out, a target that keeps moving is followed by a minimum-jerk move
 * planned again each tick from the turn's speed and acceleration.
 */
final class HandAim {
    /** A target that moves further than this from the one being turned toward starts a new turn. */
    private static final float RETARGET_DEG = 10F;
    /** A target moving less than this a tick counts as still: the turn runs out its time. */
    private static final float STILL_TARGET_DEG = 0.5F;
    /** Following a moving target, never plan to arrive sooner than this (ticks). */
    private static final double MIN_TRACK_TICKS = 3.0;
    private static final double FAST_MIN_TRACK_TICKS = 2.0;
    private static final Random RANDOM = new Random();

    private final Axis yaw = new Axis(true);
    private final Axis pitch = new Axis(false);
    private float left;

    /** The view one tick on from (nowYaw, nowPitch) toward (wantYaw, wantPitch). */
    float[] step(float nowYaw, float nowPitch, float wantYaw, float wantPitch, boolean fast) {
        float y = nowYaw + yaw.step(wrap(wantYaw - nowYaw), wantYaw, fast);
        float want = Math.max(-90F, Math.min(90F, wantPitch));
        float p = Math.max(-90F, Math.min(90F, nowPitch + pitch.step(want - nowPitch, want, fast)));
        left = Math.max(Math.abs(wrap(wantYaw - y)), Math.abs(want - p));
        return new float[] {y, p};
    }

    /** How far the view was still off the target after the last step, in degrees. */
    float left() {
        return left;
    }

    /** Forgets the turn in progress. */
    void reset() {
        yaw.reset();
        pitch.reset();
    }

    private static final class Axis {
        private final boolean wraps;
        private double velocity;
        private double acceleration;
        private double ticksLeft;
        private float target = Float.NaN;
        /** The eased turn in progress: its curve, length and ticks done (null once over). */
        private Easing ease;
        private double easeTicks;
        private int easeAt;
        /** How far it has turned, and the motion it started with, per turn time. */
        private double turned;
        private double startV;
        private double startA;

        Axis(boolean wraps) {
            this.wraps = wraps;
        }

        void reset() {
            velocity = 0;
            acceleration = 0;
            ticksLeft = 0;
            target = Float.NaN;
            ease = null;
        }

        /** The turn to make this tick toward a target {@code error} degrees away. */
        float step(float error, float newTarget, boolean fast) {
            float moved = Float.isNaN(target) ? Float.MAX_VALUE
                    : Math.abs(wraps ? wrap(newTarget - target) : newTarget - target);
            target = newTarget;
            double distance = Math.abs(error);
            if (moved > RETARGET_DEG || ticksLeft <= 1.0 && distance > 1.0) {
                // Each new turn's time is varied by up to Tuning.AIM_VARIATION either way; on
                // the ground it takes Tuning.AIM_TIME times as long (mid-air turns are planned
                // with their own times, which the casts depend on).
                double variation = 1
                        + Tuning.AIM_VARIATION.get() * (2 * RANDOM.nextDouble() - 1);
                double slower = fast ? 1 : Tuning.AIM_TIME.get();
                ticksLeft = HandTurn.turnTicks(distance, fast) * slower * variation;
                if (ticksLeft > 1.0) {
                    ease = fast ? AstarConfig.flicks() : AstarConfig.turns();
                    easeTicks = ticksLeft;
                    easeAt = 0;
                    turned = 0;
                    startV = velocity * easeTicks;
                    startA = acceleration * easeTicks * easeTicks / 2;
                }
            }
            if (ease != null) {
                // Along the curve, toward wherever the target is now.
                easeAt++;
                double t = Math.min(1, easeAt / easeTicks);
                double[] at = ease.carried(error + turned, startV, startA, t);
                double now = at[0] - turned;
                turned = at[0];
                velocity = at[1] / easeTicks;
                acceleration = at[2] / (easeTicks * easeTicks);
                ticksLeft = easeTicks - easeAt;
                if (t >= 1) {
                    ease = null;
                    velocity = 0;
                    acceleration = 0;
                    ticksLeft = 0;
                }
                return (float) now;
            }
            if (ticksLeft <= 1.0) {
                // Arriving: close the last fraction of a degree.
                velocity = 0;
                acceleration = 0;
                ticksLeft = 0;
                return error;
            }
            // The minimum-jerk (quintic) move from the current angle, speed and acceleration to
            // the target at rest, over the time left; its first tick.
            double t = ticksLeft, t2 = t * t, t3 = t2 * t, t4 = t3 * t, t5 = t4 * t;
            double v0 = velocity, a0 = acceleration;
            double c3 = (20 * error - 12 * v0 * t - 3 * a0 * t2) / (2 * t3);
            double c4 = (-30 * error + 16 * v0 * t + 3 * a0 * t2) / (2 * t4);
            double c5 = (12 * error - 6 * v0 * t - a0 * t2) / (2 * t5);
            double now = v0 + a0 / 2 + c3 + c4 + c5;
            velocity = v0 + a0 + 3 * c3 + 4 * c4 + 5 * c5;
            acceleration = a0 + 6 * c3 + 12 * c4 + 20 * c5;
            double least = fast ? FAST_MIN_TRACK_TICKS : MIN_TRACK_TICKS;
            ticksLeft = moved < STILL_TARGET_DEG ? ticksLeft - 1 : Math.max(least, ticksLeft - 1);
            return (float) now;
        }
    }

    private static float wrap(float degrees) {
        float d = degrees % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
