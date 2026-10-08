package astar.movement.exec;

import astar.movement.Keys;
import astar.movement.plan.ExecutionPlan;
import astar.movement.plan.ExecutionPlan.Kind;
import astar.movement.plan.ExecutionPlan.Segment;
import astar.pathing.Easing;
import astar.pathing.Tuning;
import java.util.SplittableRandom;

/**
 * Walks a player along an {@link ExecutionPlan}, one game tick at a time.
 *
 * <p>Each tick it reads the player, lets the {@link Follower} pick a yaw and keys, turns the
 * camera toward that yaw over the tick's frames with the {@link AimController}, and plays the
 * tick. It walks, jumps up and drops; it stops short of the first ladder or water, which come
 * in a later milestone, and reports {@link Status#UNSUPPORTED} there.
 */
public final class Executor {

    public enum Status {
        /** Still going. */
        RUNNING,
        /** Within {@link Follower#GOAL_RADIUS} of the end and all but stopped. */
        ARRIVED,
        /** Stopped at the start of a move this executor can't do yet. */
        UNSUPPORTED,
        /** Further from the route than the room beside it allows. */
        OFF_COURSE,
        /** No progress for {@link #STUCK_TICKS} ticks. */
        STUCK
    }

    /**
     * How it runs.
     *
     * @param framesPerTick rendered frames per game tick (3 at 60 frames a second)
     * @param pitch the camera pitch it holds, in degrees (positive looks down), when it
     *     doesn't look ahead
     * @param lookAhead whether the camera looks down the route at the {@link #gaze} point,
     *     further ahead the faster it goes, and moves unevenly the way a hand does
     * @param hand which hand moves the mouse, which shapes its big turns
     */
    public record Settings(Follower.Settings follower, AimController.Settings aim,
            int framesPerTick, float pitch, boolean lookAhead, AimController.Hand hand) {

        public static final Settings DEFAULT = new Settings(Follower.Settings.DEFAULT,
                AimController.Settings.DEFAULT, 3, 10, true);

        /** Moved with the right hand. */
        public Settings(Follower.Settings follower, AimController.Settings aim,
                int framesPerTick, float pitch, boolean lookAhead) {
            this(follower, aim, framesPerTick, pitch, lookAhead, AimController.Hand.RIGHT);
        }

        /** The same, moved with this hand. */
        public Settings withHand(AimController.Hand hand) {
            return new Settings(follower, aim, framesPerTick, pitch, lookAhead, hand);
        }

        /** The same, with sweeps shaped by {@code ease} and flicks by {@code snapEase}. */
        public Settings withEasing(Easing ease, Easing snapEase) {
            return new Settings(follower, aim.withEasing(ease, snapEase), framesPerTick, pitch,
                    lookAhead, hand);
        }

        /** The same, with the camera turned by {@code aim}. */
        public Settings withAim(AimController.Settings aim) {
            return new Settings(follower, aim, framesPerTick, pitch, lookAhead, hand);
        }

        /** A fixed pitch, no drift. */
        public Settings(Follower.Settings follower, AimController.Settings aim,
                int framesPerTick, float pitch) {
            this(follower, aim, framesPerTick, pitch, false);
        }
    }

    // How far ahead the camera looks, how high it aims, its pitch limits, how far it leads
    // while strafing, how still the hand rests and how far it drifts are the player's to set:
    // Tuning's camera settings.

    /**
     * Whether the keys steer the body through turns while the camera looks further ahead
     * ({@link Tuning#STRAFE}); {@code -Dastar.nostrafe=true} turns it off to compare.
     */
    static boolean strafing() {
        return Tuning.STRAFE.get() && !NO_STRAFE;
    }

    private static final boolean NO_STRAFE = Boolean.getBoolean("astar.nostrafe");
    /** How far off the camera a forward-and-side push goes, in degrees. */
    static final double STRAFE = 45;
    /** Strafing only this close to the route, in blocks. */
    static final double STRAFE_OFFSET = 0.6;
    /** Headings closer to the camera than this, in degrees, get no side key. */
    static final double STRAFE_DEAD = 10;
    /** How softly the camera follows its target while strafing, in seconds. */
    static final double SOFT_TAU = 0.3;
    /** The furthest the camera may be off the heading while strafing, in degrees. */
    static final double SOFT_REACH = 60;
    /** How long the camera takes to ease into looking further ahead, in seconds. */
    static final double LOOK_EASE = 0.25;
    /**
     * Room between edges on both sides, in blocks, below which the route is narrow (a ridge,
     * like the top of a wall a block wide): there the camera holds
     * the way the route goes overall and the keys (A or D alone too) carry the player round
     * each turn, so it turns the camera less and steers with the keys.
     */
    static final double NARROW_ROOM = 1.6;
    /** How far ahead the camera looks on a narrow route, in blocks along it. */
    static final double NARROW_LOOK = 7;
    /** The places along the route whose ways are averaged for the line it looks down. */
    static final double[] NARROW_LOOKS = {6, 10, 14, 18};
    /** How far it may lead the heading there, in degrees. */
    static final double NARROW_LEAD = 90;
    /** How softly it follows there, in seconds. */
    static final double NARROW_TAU = 0.6;
    /** How long the camera takes to come back round to the heading on a ridge, in seconds. */
    static final double NARROW_EASE_OUT = 0.35;
    /** How softly it follows while coming back, in seconds. */
    static final double EASE_OUT_TAU = 0.1;
    /** How far, in degrees, the way wanted may drift from the keys' before they change. */
    static final double KEY_HOLD = 28;
    /** At the end, a heading further round than this, in degrees, is reached without turning. */
    static final double END_TURN = 100;
    /** Closer to the end than this, in blocks, braking goes the camera's way again. */
    static final double NEAR_END = 1.5;
    /** How quickly the gaze point glides to where the route puts it, in seconds. */
    static final double GAZE_TAU = 0.12;
    /** Further than this from where it was, the gaze point jumps there (a new stretch). */
    static final double GAZE_JUMP = 3;
    /** How long the gaze takes to come in or go out between those, in seconds. */
    static final double GAZE_REACH_TAU = 0.4;
    /** The eyes' height over the feet, in blocks. */
    static final double EYE_HEIGHT = 1.62;
    /** The same for the pitch, in degrees, and how close it comes before resting again. */
    static final double PITCH_HOLD = 4;
    static final double PITCH_REST = 0.5;
    /** The game's double tap window for sprinting, in ticks. */
    static final int DOUBLE_TAP = 7;
    /**
     * How stiffly the pitch follows where it looks, in radians a second: a calm, critically
     * damped follow that settles in about {@code 4 / PITCH_OMEGA} seconds.
     */
    static final double PITCH_OMEGA = 5;
    /** How quickly the pitch's turn can speed up or slow down, in degrees a second squared. */
    static final double PITCH_ACCEL = 400;
    /** How long a drift lasts before it wanders elsewhere, in seconds. */
    static final double DRIFT_HOLD = 1.5;
    /** How smoothly the drift moves, in seconds: no jitter from tick to tick. */
    static final double DRIFT_SMOOTH = 0.25;
    /** Below this speed across the ground, in blocks a tick, a big turn may start late. */
    static final double PAUSE_SPEED = 0.06;

    static final int STUCK_TICKS = 60;
    private static final double TICK = 0.05;

    private final PlayerController player;
    private final ExecutionPlan plan;
    private final Settings settings;
    private final Follower follower;
    private final AimController aim;
    private final int end;
    private final Kind blockedBy;
    private Status status = Status.RUNNING;
    private double lastTarget = Double.NaN;
    private double best;
    private int sinceProgress;
    private int ticks;
    private Follower.Intent last;
    private double[] gaze;
    /** Where the player was when the gaze point was last placed. */
    private double[] gazeFrom;
    /** How far ahead the gaze point is put, eased between the ridge's and the usual. */
    private double gazeReach = Double.NaN;
    /** The keys held on the last tick. */
    private Keys lastKeys = Keys.NONE;
    /** The eighths of a turn off the camera the keys pushed last tick (see turnedKeys). */
    private int lastTurn;
    /** Whether the camera is holding still for the last of the way (see END_TURN). */
    private boolean holdingEnd;
    /** Whether the player left the ground by walking off a drop, not by jumping. */
    private boolean leftByDrop;
    /** The camera's target while strafing, followed softly. */
    private double softTarget = Double.NaN;
    /** How much the camera looks further ahead than the heading, 0 to 1, eased. */
    private double lookWeight;
    /** How far the side-key pushes fell short of the heading wanted, carried to the next tick. */
    private double strafeCarry;
    /** How fast the pitch is turning, in degrees a second. */
    private double pitchRate;
    /** Ticks left in which pressing forward again would start a sprint, and the last press. */
    private int tapLeft;
    private boolean wasForward;
    /** Whether the pitch is resting (see {@link #PITCH_HOLD}). */
    private boolean pitchResting;
    /** Where the camera's unevenness comes from, carried over from executor to executor. */
    private SplittableRandom hand;
    /** The drift's wander, yaw and pitch, each spread 1, and those smoothed. */
    private double wanderYaw;
    private double wanderPitch;
    private double driftYaw;
    private double driftPitch;
    /** How much of the drift is on: none in the air or on a jump, back over half a second. */
    private double drift;

    /** Follows the plan from its first node. */
    public Executor(PlayerController player, ExecutionPlan plan, Settings settings) {
        this(player, plan, 0, settings);
    }

    /** Follows the plan from node {@code start}, as far as it can go. */
    public Executor(PlayerController player, ExecutionPlan plan, int start, Settings settings) {
        this.player = player;
        this.plan = plan;
        this.settings = settings;
        int stop = plan.nodes().size() - 1;
        Kind kind = null;
        for (Segment s : plan.segments()) {
            if (s.to() > start && !supported(plan, s)) {
                stop = Math.max(start, s.from());
                kind = s.kind();
                break;
            }
        }
        end = stop;
        blockedBy = kind;
        follower = new Follower(plan, start, end, settings.follower());
        aim = new AimController(settings.aim());
        if (settings.lookAhead()) {
            // Different from trip to trip, the same every time for the same trip.
            ExecutionPlan.Node from = plan.nodes().get(Math.min(start, plan.nodes().size() - 1));
            hand = new SplittableRandom(31L * (31L * Double.hashCode(from.x())
                    + Double.hashCode(from.y())) + Double.hashCode(from.z()));
            aim.hand(hand);
        }
        aim.handed(settings.hand());
    }

    /**
     * Whether the executor does a segment: walks, jumps up and drops, from a floor to a floor.
     * Climbing and swimming come later, and so do getting on and off a ladder and into and out
     * of water, which start or end where there's no floor.
     */
    static boolean supported(ExecutionPlan plan, Segment s) {
        return s.kind() != Kind.CLIMB && s.kind() != Kind.SWIM
                && plan.nodes().get(s.from()).floor() && plan.nodes().get(s.to()).floor();
    }

    public Status status() {
        return status;
    }

    /** The last node this executor goes to: the plan's end, or where an unsupported move starts. */
    public int end() {
        return end;
    }

    /** The kind of move it stopped in front of, or null if it goes to the plan's end. */
    public Kind blockedBy() {
        return blockedBy;
    }

    public Follower follower() {
        return follower;
    }

    /** What the follower wanted on the last tick, or null before the first. */
    public Follower.Intent lastIntent() {
        return last;
    }

    public int ticks() {
        return ticks;
    }

    /**
     * Where the camera looks, {x, y, z} on the route with y on its floor (it looks a hair
     * above, at the middle of the lead marker), set each tick when
     * it looks ahead; null before the first tick or when it holds a fixed pitch.
     */
    public double[] gaze() {
        return gaze;
    }

    /**
     * Takes over the camera's motion from the executor this one replaces, turn and drift and
     * all, so a new plan partway (a recovery, the next stretch) doesn't jolt the view.
     */
    public void carryCamera(Executor from) {
        aim.carry(from.aim);
        pitchRate = from.pitchRate;
        pitchResting = from.pitchResting;
        tapLeft = from.tapLeft;
        wasForward = from.wasForward;
        lookWeight = from.lookWeight;
        softTarget = from.softTarget;
        strafeCarry = from.strafeCarry;
        drift = from.drift;
        if (from.hand != null) {
            hand = from.hand;
            aim.hand(hand);
        }
        wanderYaw = from.wanderYaw;
        wanderPitch = from.wanderPitch;
        driftYaw = from.driftYaw;
        driftPitch = from.driftPitch;
        gaze = from.gaze;
        gazeFrom = from.gazeFrom;
        gazeReach = from.gazeReach;
        lastTarget = from.lastTarget;
    }

    /** The keys held on the last tick. */
    public Keys lastKeys() {
        return lastKeys;
    }

    /**
     * Plays one tick, unless it has already finished.
     *
     * @return the status after the tick
     */
    public Status tick() {
        if (status != Status.RUNNING) {
            return status;
        }
        PlayerController.Observation p = player.observe();
        follower.locate(p);
        double room = follower.narrowest(follower.progress(), follower.progress());
        if (follower.offset() > room + 1.0) {
            return status = Status.OFF_COURSE;
        }
        // Slow enough to be sure of stopping within a few hundredths of a block.
        if (follower.toEnd(p) <= Follower.GOAL_RADIUS && p.groundSpeed() < 0.02
                && follower.length() - follower.progress() <= 1) {
            return status = done();
        }
        // On the ground with no jump or drop close ahead, the camera needn't point where the
        // player goes: forward with a side key pushes 45 degrees off it.
        // It eases into it (LOOK_EASE), so the camera doesn't jump from where the player goes
        // to further ahead, and stops at once: getting back after a push, the keys go where
        // the player goes, and the camera's own sweep keeps the turn back smooth.
        // On a narrow route it keeps strafing down drops too: walking off an edge and falling
        // go where the keys push, whichever way the camera points. Not up jumps: a sprint jump
        // is boosted the way the camera points.
        boolean narrowAhead = follower.ridge(follower.progress(),
                follower.progress() + NARROW_LOOK, NARROW_ROOM);
        Follower.AirMove next = follower.upcoming(p);
        boolean free = settings.lookAhead() && strafing() && follower.offset() < STRAFE_OFFSET
                && (p.onGround() ? next == null || (narrowAhead && !next.up())
                        : narrowAhead && leftByDrop && follower.dropping());
        if (p.onGround()) {
            leftByDrop = next != null && !next.up();
        }
        // On a ridge it eases back too, ahead of a jump up, so the camera comes round to the
        // jump over a few ticks instead of all at once (an up jump is seen 3 blocks ahead).
        lookWeight = free ? Math.min(1, lookWeight + TICK / LOOK_EASE)
                : narrowAhead ? Math.max(0, lookWeight - TICK / NARROW_EASE_OUT) : 0;
        boolean easingOut = !free && lookWeight > 0;
        boolean strafe = lookWeight > 0;
        boolean narrow = strafe && narrowAhead;
        boolean nearEnd = settings.lookAhead() && strafing() && p.onGround()
                && follower.length() - follower.progress() < NEAR_END;
        follower.strafe(strafe, narrow, nearEnd && holdingEnd);
        Follower.Intent intent = follower.decide(p);
        boolean endHold = nearEnd
                && (holdingEnd || Math.abs(AimController.wrap(intent.yaw() - p.yaw())) > END_TURN);
        holdingEnd = endHold;

        last = intent;
        double heading = p.yaw() + AimController.wrap(intent.yaw() - p.yaw());
        double target = heading;
        // On a narrow route braking turns with the keys too (see sideKeys), so the camera
        // needn't swing back to the heading to brake.
        // Not at the end, though, where the heading swings about as it stops on the spot.
        boolean brakeTurns = narrow && follower.length() - follower.progress() > NEAR_END;
        strafe &= (intent.keys().forward() || brakeTurns) && !intent.keys().jump()
                && (brakeTurns || !intent.keys().back());
        if (strafe) {
            // The camera looks further down the route, so it turns early and gently through
            // a tight turn while the keys carry the body round it, as a player strafes.
            double move = p.groundSpeed() / Follower.SLIDE;
            double[] ahead = narrow ? lineAhead(p) : follower.placeAhead(
                    Tuning.STRAFE_LOOK.get() + Tuning.STRAFE_LOOK_PER_SPEED.get() * move);
            if (Math.hypot(ahead[0] - p.x(), ahead[2] - p.z()) > 1) {
                double lead = AimController.wrap(Follower.yawToward(p.x(), p.z(), ahead[0],
                        ahead[2]) - heading);
                double most = narrow ? NARROW_LEAD : Tuning.STRAFE_LEAD.get();
                target = heading + lookWeight * Math.max(-most, Math.min(most, lead));
            }
        }
        // While the keys steer, the camera can follow its target softly: it only has to stay
        // within reach of the heading.
        if (strafe && !Double.isNaN(softTarget)) {
            double tau = easingOut ? EASE_OUT_TAU : narrow ? NARROW_TAU : SOFT_TAU;
            softTarget += AimController.wrap(target - softTarget) * (1 - Math.exp(-TICK / tau));
            double off = AimController.wrap(softTarget - heading);
            double reach = easingOut ? lookWeight * NARROW_LEAD : narrow ? NARROW_LEAD : SOFT_REACH;
            softTarget = heading + Math.max(-reach, Math.min(reach, off));
            target = softTarget;
        } else {
            softTarget = target;
        }
        // Up a jump on a ridge, the camera holds still in the air where it took off: the keys
        // (turned as on the ground) steer what little the air allows, and the camera turns on
        // only once the feet are down, rather than swinging about mid-jump.
        // At the very end, stopping on the spot, the camera holds too and the keys go
        // whichever way is left to go (back as well), rather than turning round for the last
        // fraction of a block it overshot.
        if (endHold) {
            target = Double.isNaN(lastTarget) ? p.yaw() : lastTarget;
            softTarget = target;
        }
        if (intent.keys().jump() && p.onGround() && !Double.isNaN(lastTarget)) {
            // Taking off: the camera stays where it was when the jump was judged lined up,
            // rather than starting a new turn on the take-off tick itself.
            target = lastTarget;
            softTarget = target;
        }
        boolean airHold = narrowAhead && !p.onGround() && !leftByDrop
                && settings.lookAhead() && strafing();
        if (airHold) {
            target = Double.isNaN(lastTarget) ? p.yaw() : lastTarget;
            softTarget = target;
        }
        double targetRate = Double.isNaN(lastTarget) ? 0
                : AimController.wrap(target - lastTarget) / TICK;
        // A target that jumps (a new lookahead segment) isn't a turn to lead.
        if (Math.abs(targetRate) > settings.aim().maxRate()) {
            targetRate = 0;
        }
        lastTarget = target;
        Keys keys = intent.keys();
        float pitch = settings.pitch();
        if (settings.lookAhead()) {
            boolean calm = p.onGround() && !keys.jump();
            drift = calm ? Math.min(1, drift + TICK / 0.5) : 0;
            wander();
            target += drift * Tuning.DRIFT.get() * driftYaw;
            pitch = (float) lookPitch(p,
                    drift * Tuning.DRIFT_PITCH.get() * driftPitch + aim.tilt(), narrowAhead);
            aim.gentle(p.onGround() ? 0 : Tuning.AIR_TURN_ACCEL.get());
            aim.hold(strafe || airHold || endHold ? Tuning.HOLD_STRAFING.get()
                    : Tuning.HOLD.get());
            // A big turn may start a moment late, as a person takes to react, but only when
            // nearly still on the ground, where starting late costs nothing.
            aim.mayPause(p.onGround() && p.groundSpeed() < PAUSE_SPEED && !keys.jump());
        }
        float yaw = p.yaw();
        double dt = TICK / settings.framesPerTick();
        for (int f = 0; f < settings.framesPerTick(); f++) {
            yaw = aim.frame(yaw, target + targetRate * dt * f, targetRate, dt);
        }
        if (endHold) {
            strafeCarry = 0; // no alternating pushes so close to the spot
            if (keys.back() && !keys.forward() && p.groundSpeed() > 1e-3) {
                // Braking pushes against the way the player is going, which this close to the
                // end needn't be the heading: forward, turned to point back along the velocity.
                double against = Follower.yawToward(p.x(), p.z(), p.x() - p.vx(), p.z() - p.vz());
                keys = turnedKeys(new Keys(true, false, false, false, keys.jump(), keys.sneak(),
                        false), against - yaw, 4);
            } else {
                keys = turnedKeys(keys, heading - yaw, 4);
            }
        } else if (airHold) {
            keys = sideKeys(keys, heading - yaw);
        } else if (strafe) {
            keys = narrow ? sideKeys(keys, heading - yaw) : strafeKeys(keys, heading - yaw);
        } else {
            strafeCarry = 0;
        }
        if (keys.jump()) {
            leftByDrop = false; // jumped: the sprint jump's boost goes the way the camera points
        }
        keys = letGo(keys);
        player.tick(keys, yaw, pitch);
        lastKeys = keys;
        ticks++;
        double progress = follower.progress();
        if (progress > best + 0.05) {
            best = progress;
            sinceProgress = 0;
        } else if (++sinceProgress > STUCK_TICKS) {
            status = Status.STUCK;
        }
        return status;
    }

    /**
     * The keys that push the player {@code off} degrees from where the camera points, on
     * average: forward alone, or with A (45 to the left) or D (45 to the right), whichever is
     * nearest what's wanted plus what earlier ticks fell short by, so that over a few ticks the
     * pushes add up to the heading wanted and the player's momentum smooths them out.
     */
    private Keys strafeKeys(Keys keys, double off) {
        if (Math.abs(AimController.wrap(off)) < STRAFE_DEAD) {
            // Near enough: the camera's own turn closes it, with no side key to jostle.
            strafeCarry = 0;
            return keys;
        }
        double want = AimController.wrap(off) + strafeCarry;
        double side = want < -STRAFE / 2 ? -STRAFE : want > STRAFE / 2 ? STRAFE : 0;
        strafeCarry = Math.max(-STRAFE, Math.min(STRAFE, want - side));
        return new Keys(keys.forward(), side < 0, keys.back(), side > 0, keys.jump(),
                keys.sneak(), keys.sprint());
    }

    /**
     * On a narrow route: forward's push turned to the nearest of straight on, 45 degrees
     * either side (W with A or D) or 90 (A or D alone, which drops a sprint), with what earlier
     * ticks fell short by carried on as in {@link #strafeKeys}. Back (braking) turns with it, so
     * it still pushes against the way the player goes.
     */
    /**
     * On a ridge, a point straight down the general line of the route: the average of the
     * ways to points {@link #NARROW_LOOKS} blocks on along it, so the camera looks past the
     * zigzags instead of at each leg in turn. As {@code {x, y, z}}.
     */
    private double[] lineAhead(PlayerController.Observation p) {
        double ux = 0;
        double uz = 0;
        for (double d : NARROW_LOOKS) {
            double[] a = follower.placeAhead(d);
            double dx = a[0] - p.x();
            double dz = a[2] - p.z();
            double len = Math.hypot(dx, dz);
            if (len > 1) {
                ux += dx / len;
                uz += dz / len;
            }
        }
        double len = Math.hypot(ux, uz);
        if (len < 1e-6) {
            return follower.placeAhead(NARROW_LOOK);
        }
        return new double[] {p.x() + NARROW_LOOK * ux / len, p.y(), p.z() + NARROW_LOOK * uz / len};
    }

    private Keys sideKeys(Keys keys, double off) {
        return turnedKeys(keys, off, 2);
    }

    /**
     * Forward's push (and back's) turned to the nearest of the eight directions the keys give,
     * up to {@code most} eighths of a turn either side of the camera, with the shortfall
     * carried on to the next tick. Only straight on, or 45 degrees off it, can sprint.
     */
    private Keys turnedKeys(Keys keys, double off, int most) {
        // The nearest of the eight, but kept on the last one until the way wanted is well past
        // halfway to the next (KEY_HOLD), so the keys don't flick between two from tick to
        // tick: the route-following steers the small difference out.
        // What earlier ticks fell short by is carried on, so on average the pushes still add
        // up to the way wanted.
        double want = AimController.wrap(off) + strafeCarry;
        double limit = most * STRAFE;
        double clamped = Math.max(-limit, Math.min(limit, want));
        int k = (int) Math.round(clamped / STRAFE);
        if (Math.abs(lastTurn) <= most && Math.abs(clamped - lastTurn * STRAFE) < KEY_HOLD) {
            k = lastTurn;
        }
        lastTurn = k;
        strafeCarry = Math.max(-STRAFE, Math.min(STRAFE, want - k * STRAFE));
        if (Math.abs(AimController.wrap(off)) < STRAFE_DEAD && k == 0) {
            strafeCarry = 0;
        }
        boolean f = keys.forward() && !keys.back();
        boolean b = keys.back() && !keys.forward();
        if (!f && !b) {
            return keys; // coasting (W and S together) or nothing: no side key
        }
        if (b) {
            k = k > 0 ? k - 4 : k + 4; // push the opposite way
        }
        // k eighths of a turn clockwise (to the right) from the camera: 0 W, 1 W+D, 2 D,
        // 3 S+D, 4 or -4 S, -3 S+A, -2 A, -1 W+A.
        int a = Math.abs(k);
        boolean fw = a <= 1;
        boolean bk = a >= 3;
        boolean side = a != 0 && a != 4;
        boolean right = k > 0;
        return new Keys(fw, side && !right, bk, side && right, keys.jump(), keys.sneak(),
                keys.sprint() && fw && f);
    }

    /**
     * Coasting the way a player does: letting go of every key rather than holding forward and
     * back together, which cancel to the same slide but nobody presses. Only once forward has
     * been held long enough that pressing it again can't count as a double tap and start a
     * sprint (back held as well clears the double tap; letting go doesn't). Keeps count of
     * the double tap as the game does.
     */
    private Keys letGo(Keys keys) {
        if (tapLeft > 0) {
            tapLeft--;
        }
        if (keys.forward() && keys.back() && !keys.left() && !keys.right() && !keys.jump()
                && tapLeft == 0) {
            keys = new Keys(false, false, false, false, false, keys.sneak(), false);
        }
        boolean forward = keys.forward() && !keys.back();
        if (keys.back()) {
            tapLeft = 0;
        }
        if (forward && !wasForward) {
            tapLeft = DOUBLE_TAP;
        }
        wasForward = forward;
        return keys;
    }

    /**
     * Moves the drift on a tick: a wander that holds for a while and then goes elsewhere, at
     * random (spread 1), smoothed so the camera doesn't jitter.
     */
    private void wander() {
        if (hand == null) {
            return;
        }
        double keep = Math.exp(-TICK / DRIFT_HOLD);
        double kick = Math.sqrt(1 - keep * keep);
        wanderYaw = keep * wanderYaw + kick * gaussian();
        wanderPitch = keep * wanderPitch + kick * gaussian();
        double k = 1 - Math.exp(-TICK / DRIFT_SMOOTH);
        driftYaw += k * (wanderYaw - driftYaw);
        driftPitch += k * (wanderPitch - driftPitch);
    }

    /** A normally spread number, spread 1. */
    private double gaussian() {
        double u = Math.max(1e-12, hand.nextDouble());
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * hand.nextDouble());
    }

    /**
     * The pitch to hold this tick: looking at the point {@link Tuning#VIEW_HEIGHT} over the gaze
     * point down the route (so the view tips down a drop or up a climb by the floor's rise),
     * followed like a steady hand, critically damped with its speed and how fast that changes
     * both capped, so slopes and stairs move the view calmly instead of snapping it.
     */
    private double lookPitch(PlayerController.Observation p, double wobble, boolean ridge) {
        double move = p.groundSpeed() / Follower.SLIDE;
        double reach = ridge
                ? Tuning.RIDGE_LOOK.get() + Tuning.RIDGE_LOOK_PER_SPEED.get() * move
                : Tuning.LOOK_AHEAD.get() + Tuning.LOOK_AHEAD_PER_SPEED.get() * move;
        gazeReach = Double.isNaN(gazeReach) ? reach
                : gazeReach + (reach - gazeReach) * (1 - Math.exp(-TICK / GAZE_REACH_TAU));
        double[] ahead = follower.placeAhead(gazeReach);
        // Glide there rather than snap: where the route bends down a drop, the place ahead
        // hops as the player passes a node, and the view and the lead marker shouldn't.
        if (gaze == null || Math.hypot(Math.hypot(ahead[0] - gaze[0], ahead[1] - gaze[1]),
                ahead[2] - gaze[2]) > GAZE_JUMP) {
            gaze = ahead;
        } else {
            // Carried along with the player first, so it doesn't trail behind on a straight.
            double k = 1 - Math.exp(-TICK / GAZE_TAU);
            for (int i = 0; i < 3; i++) {
                double carried = gaze[i] + (i == 0 ? p.x() : i == 1 ? p.y() : p.z()) - gazeFrom[i];
                gaze[i] = carried + k * (ahead[i] - carried);
            }
        }
        gazeFrom = new double[] {p.x(), p.y(), p.z()};
        double d = Math.hypot(gaze[0] - p.x(), gaze[2] - p.z());
        double want = Math.toDegrees(Math.atan2(
                EYE_HEIGHT - Tuning.VIEW_HEIGHT.get() + p.y() - gaze[1], Math.max(d, 1)));
        double lo = Tuning.PITCH_MIN.get(), hi = Tuning.PITCH_MAX.get();
        want = Math.max(Math.min(lo, hi), Math.min(Math.max(lo, hi), want)) + wobble;
        if (Float.isNaN(p.pitch())) {
            return want;
        }
        double pitch = p.pitch();
        // A still hand: the pitch rests until where it should look strays a few degrees.
        if (pitchResting && Math.abs(want - pitch) <= PITCH_HOLD) {
            pitchRate = 0;
            return pitch;
        }
        pitchResting = false;
        int steps = settings.framesPerTick();
        double dt = TICK / steps;
        double most = Tuning.PITCH_SPEED.get();
        for (int i = 0; i < steps; i++) {
            double a = PITCH_OMEGA * PITCH_OMEGA * (want - pitch) - 2 * PITCH_OMEGA * pitchRate;
            pitchRate += Math.max(-PITCH_ACCEL, Math.min(PITCH_ACCEL, a)) * dt;
            pitchRate = Math.max(-most, Math.min(most, pitchRate));
            pitch += pitchRate * dt;
        }
        if (Math.abs(want - pitch) < PITCH_REST && Math.abs(pitchRate) < 5) {
            pitchResting = true;
        }
        return pitch;
    }

    private Status done() {
        return blockedBy == null ? Status.ARRIVED : Status.UNSUPPORTED;
    }

    /** Ticks until it finishes or {@code maxTicks} pass. */
    public Status run(int maxTicks) {
        for (int i = 0; i < maxTicks && status == Status.RUNNING; i++) {
            tick();
        }
        return status;
    }
}
