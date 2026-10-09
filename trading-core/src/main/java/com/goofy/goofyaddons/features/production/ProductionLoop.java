package com.goofy.goofyaddons.features.production;

import java.util.Objects;

/**
 * One production run as a sequence of stages: procure the inputs, process them (craft,
 * Forge or Kat), wait for and claim a timed result, then sell the output.
 *
 * <p>The loop owns only the order of stages and the rules between them. Each stage is
 * performed by a port that carries its own evidence checks and journals its own click
 * intent. The loop adds three guarantees on top:
 * <ul>
 *   <li>A stage boundary is persisted before the next stage starts, so a restart resumes
 *       at the last verified boundary.</li>
 *   <li>An uncertain stage (a click whose effect could not be proven) ends the run in
 *       {@link Stage#REVIEW}. Nothing is ever replayed automatically.</li>
 *   <li>A blocked stage (missing ingredients, a menu the player must open, a failed
 *       journal write) keeps its place, so the run can continue once the cause is fixed.</li>
 * </ul>
 */
public final class ProductionLoop {
    public enum Stage { PROCURE, PROCESS, AWAIT, CLAIM, SELL, DONE, REVIEW }
    public enum Step { PENDING, DONE, UNCERTAIN, BLOCKED }

    public record Outcome(Step step, String reason) {
        public static final Outcome PENDING = new Outcome(Step.PENDING, null);
        public static final Outcome DONE = new Outcome(Step.DONE, null);
        public static Outcome pending(String reason) { return new Outcome(Step.PENDING, reason); }
        public static Outcome blocked(String reason) { return new Outcome(Step.BLOCKED, Objects.requireNonNull(reason)); }
        public static Outcome uncertain(String reason) { return new Outcome(Step.UNCERTAIN, Objects.requireNonNull(reason)); }
    }

    /** Which stages this run has. Processing is always present. */
    public record Plan(boolean procure, boolean timed, boolean sell) {
        public Stage first() { return procure ? Stage.PROCURE : Stage.PROCESS; }
        public boolean includes(Stage stage) {
            return switch (stage) {
                case PROCURE -> procure;
                case AWAIT, CLAIM -> timed;
                case SELL -> sell;
                case PROCESS, DONE, REVIEW -> true;
            };
        }
        public Stage after(Stage stage) {
            return switch (stage) {
                case PROCURE -> Stage.PROCESS;
                case PROCESS -> timed ? Stage.AWAIT : sell ? Stage.SELL : Stage.DONE;
                case AWAIT -> Stage.CLAIM;
                case CLAIM -> sell ? Stage.SELL : Stage.DONE;
                case SELL, DONE -> Stage.DONE;
                case REVIEW -> Stage.REVIEW;
            };
        }
    }

    public interface Ports {
        Outcome procure(long now);
        Outcome process(long now);
        /** Done once a timed result is ready to claim. */
        Outcome await(long now);
        Outcome claim(long now);
        Outcome sell(long now);
        /** Durably records that the run has reached {@code stage}. Throws if it could not. */
        void persist(Stage stage, String reason) throws Exception;
    }

    private final Plan plan;
    private final Ports ports;
    private Stage stage;
    private String reason;
    /** The current stage's port reported done but its boundary is not yet saved; never call it again. */
    private boolean advancePending;

    /** Starts a new run at its first stage. The caller has already persisted the plan. */
    public ProductionLoop(Plan plan, Ports ports) { this(plan, plan.first(), ports); }

    /** Resumes a run at a persisted stage. A stage the plan does not contain is sent to review. */
    public ProductionLoop(Plan plan, Stage restored, Ports ports) {
        this.plan = Objects.requireNonNull(plan);
        this.ports = Objects.requireNonNull(ports);
        if (restored == null || !plan.includes(restored)) {
            this.stage = Stage.REVIEW;
            this.reason = "Saved production stage does not match its plan";
        } else {
            this.stage = restored;
        }
    }

    public Stage stage() { return stage; }
    public Plan plan() { return plan; }
    /** Why the run is blocked, waiting or in review; null while it simply runs. */
    public String reason() { return reason; }
    public boolean finished() { return stage == Stage.DONE || stage == Stage.REVIEW; }

    public Step tick(long now) {
        if (stage == Stage.DONE) return Step.DONE;
        if (stage == Stage.REVIEW) return Step.UNCERTAIN;
        if (advancePending) return advance();
        Outcome outcome;
        try {
            outcome = switch (stage) {
                case PROCURE -> ports.procure(now);
                case PROCESS -> ports.process(now);
                case AWAIT -> ports.await(now);
                case CLAIM -> ports.claim(now);
                case SELL -> ports.sell(now);
                case DONE, REVIEW -> throw new IllegalStateException();
            };
        } catch (RuntimeException failure) {
            // A port that cannot tell what happened is treated like an unproven click.
            outcome = Outcome.uncertain("Production " + stage.name().toLowerCase(java.util.Locale.ROOT) + " failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
        if (outcome == null) outcome = Outcome.PENDING;
        switch (outcome.step()) {
            case PENDING -> { reason = outcome.reason(); return Step.PENDING; }
            case BLOCKED -> { reason = outcome.reason(); return Step.BLOCKED; }
            case UNCERTAIN -> {
                reason = outcome.reason();
                try { ports.persist(Stage.REVIEW, reason); } catch (Exception ignored) { /* Review stays in memory; nothing replays. */ }
                stage = Stage.REVIEW;
                return Step.UNCERTAIN;
            }
            case DONE -> { advancePending = true; return advance(); }
        }
        throw new IllegalStateException();
    }

    private Step advance() {
        Stage next = plan.after(stage);
        try {
            ports.persist(next, null);
        } catch (Exception failure) {
            reason = "Production journal could not record the next stage; no further action taken";
            return Step.BLOCKED;
        }
        advancePending = false;
        stage = next;
        reason = null;
        return next == Stage.DONE ? Step.DONE : Step.PENDING;
    }
}
