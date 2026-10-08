package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.BazaarFlipper.State;
import com.goofy.goofyaddons.features.bookflipper.helper.CombineRecovery;
import com.goofy.goofyaddons.features.bookflipper.helper.Task;

/**
 * Combining a route's books at the anvil until one output at the sale level remains.
 *
 * <p>{@link com.goofy.goofyaddons.features.bookflipper.helper.BookCombiner} verifies each
 * merge by input consumption and output arrival. This operation decides what the engine
 * does with each result.
 */
final class BookCombine {
    private static final int COMBINE_ABSENT_TICKS = 10;
    private int inputsAbsent;

    void reset() { inputsAbsent = 0; }

    void tick(BookContext ctx) {
        var combiner = ctx.combiner();
        Task task = ctx.taskInState(Task.BookState.COMBINE);
        if (task == null) {
            combiner.reset();
            ctx.actions().closeMenu();
            ctx.state(State.IDLE);
            return;
        }
        if (!combiner.pending()) {
            String prerequisite = com.goofy.goofyaddons.features.access.RouteRequirements.book(task.getBook().id(), ctx.services().observedSkills());
            if (prerequisite != null) { ctx.skipBookRequirement(task, prerequisite); return; }
        }
        if (!ctx.screenOpen()) ctx.clock().start(ctx.delay());
        if (!ctx.screenOpen() && ctx.clock().shouldFire()) ctx.actions().command("anvil");
        if (!ctx.containerNameCheck("Anvil")) return;
        ctx.clock().start(ctx.delay());
        if (!ctx.scanner().isMenuLoaded(8) || !ctx.clock().shouldFire()) return;
        int previousRetries = combiner.actionRetries();
        combiner.observedSkills(ctx.services().observedSkills());
        var result = combiner.tick(task, ctx.menu(), ctx.actions(), ctx.now());
        if (combiner.actionRetries() > previousRetries) ctx.services().event("WARN", "books.anvil_action_retried",
                java.util.Map.of("phase", combiner.progress(), "attempt", combiner.actionRetries(),
                        "item", task.getBook().id(), "context", ctx.services().diagnosticContext()));
        switch (result) {
            case MERGED -> {
                inputsAbsent = 0;
                ctx.debug("[BazaarFlipper] COMBINE: verified input consumption and output arrival for " + task.getBook());
            }
            case NO_PAIR -> {
                inputsAbsent = 0;
                try {
                    task.finishCombining();
                } catch (IllegalStateException invalidModel) {
                    ctx.safetyHalt(invalidModel.getMessage() + "; ownership retained.");
                    return;
                }
                ctx.actions().closeMenu();
                ctx.state(State.IDLE);
            }
            case INPUTS_MISSING -> {
                var decision = CombineRecovery.decide(++inputsAbsent, COMBINE_ABSENT_TICKS, true);
                if (decision != CombineRecovery.Action.WAIT) {
                    inputsAbsent = 0;
                    ctx.safetyHalt("Book model and inventory disagree before combining; inspect books and live orders. No buy order cancelled or replaced.");
                }
            }
            case BLOCKED -> {
                if (combiner.failure() != null && combiner.failure().startsWith("Cannot combine: ")) {
                    String reason = combiner.failure().substring("Cannot combine: ".length()).replace("; input books retained for review", "");
                    com.goofy.goofyaddons.features.generalflipper.BazaarAccess.instance().deny(task.getBook().getLevel(task.getBook().sellLevel()), reason);
                    ctx.services().invalidateMarketReport();
                }
                if (combiner.canReconcilePhysicalTimeout(ctx.menu())) {
                    ctx.services().event("WARN", "books.combine_reconciliation", java.util.Map.of(
                            "combine", combiner.diagnosticState(), "memory", ctx.inventoryMemory().diagnosticState()));
                    combiner.reset();
                    ctx.startLocationReconciliation();
                    ctx.state(State.IDLE); ctx.actions().closeMenu(); ctx.clock().stop();
                } else ctx.safetyHalt(combiner.failure());
            }
            case WAITING -> { }
        }
    }
}
