package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.features.production.ProductionLoop.Outcome;
import com.goofy.goofyaddons.features.production.ProductionLoop.Plan;
import com.goofy.goofyaddons.features.production.ProductionLoop.Stage;
import com.goofy.goofyaddons.features.production.ProductionLoop.Step;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProductionLoopTest {
    /** Scripted ports: each stage answers from its own queue, then repeats its last answer. */
    static class Script implements ProductionLoop.Ports {
        final Map<Stage, Deque<Outcome>> answers = new EnumMap<>(Stage.class);
        final List<String> calls = new ArrayList<>();
        final List<Stage> persisted = new ArrayList<>();
        int failPersists;

        Script answer(Stage stage, Outcome... outcomes) {
            answers.computeIfAbsent(stage, s -> new ArrayDeque<>()).addAll(List.of(outcomes));
            return this;
        }
        private Outcome next(Stage stage) {
            calls.add(stage.name());
            var queue = answers.get(stage);
            if (queue == null || queue.isEmpty()) return Outcome.DONE;
            return queue.size() == 1 ? queue.peek() : queue.poll();
        }
        public Outcome procure(long now) { return next(Stage.PROCURE); }
        public Outcome process(long now) { return next(Stage.PROCESS); }
        public Outcome await(long now) { return next(Stage.AWAIT); }
        public Outcome claim(long now) { return next(Stage.CLAIM); }
        public Outcome sell(long now) { return next(Stage.SELL); }
        public void persist(Stage stage, String reason) throws Exception {
            if (failPersists > 0) { failPersists--; throw new java.io.IOException("disk full"); }
            persisted.add(stage);
        }
    }

    private static Step run(ProductionLoop loop, int ticks) {
        Step last = null;
        for (int i = 0; i < ticks && !loop.finished(); i++) last = loop.tick(i);
        return last;
    }

    @Test
    void craftRunVisitsOnlyItsStagesInOrderAndPersistsEachBoundary() {
        var script = new Script();
        var loop = new ProductionLoop(new Plan(true, false, true), script);
        assertEquals(Step.DONE, run(loop, 10));
        assertEquals(List.of("PROCURE", "PROCESS", "SELL"), script.calls);
        assertEquals(List.of(Stage.PROCESS, Stage.SELL, Stage.DONE), script.persisted);
    }

    @Test
    void timedRunWaitsThenClaims() {
        var script = new Script().answer(Stage.AWAIT, Outcome.pending("Forge timer running"), Outcome.pending("Forge timer running"), Outcome.DONE);
        var loop = new ProductionLoop(new Plan(false, true, false), script);
        assertEquals(Step.PENDING, loop.tick(0));
        assertEquals(Stage.AWAIT, loop.stage());
        assertEquals(Step.PENDING, loop.tick(1));
        assertEquals("Forge timer running", loop.reason());
        run(loop, 10);
        assertEquals(List.of("PROCESS", "AWAIT", "AWAIT", "AWAIT", "CLAIM"), script.calls);
        assertEquals(Stage.DONE, loop.stage());
    }

    @Test
    void uncertainStageGoesToReviewAndIsNeverRetried() {
        var script = new Script().answer(Stage.PROCESS, Outcome.uncertain("Submission not acknowledged"));
        var loop = new ProductionLoop(new Plan(false, true, true), script);
        assertEquals(Step.UNCERTAIN, loop.tick(0));
        assertEquals(Stage.REVIEW, loop.stage());
        assertEquals("Submission not acknowledged", loop.reason());
        assertEquals(Step.UNCERTAIN, loop.tick(1));
        assertEquals(List.of("PROCESS"), script.calls);
        assertEquals(List.of(Stage.REVIEW), script.persisted);
    }

    @Test
    void blockedStageKeepsItsPlaceAndContinuesOnceFixed() {
        var script = new Script().answer(Stage.PROCURE, Outcome.blocked("Missing INPUT; automatic buying is off"), Outcome.DONE);
        var loop = new ProductionLoop(new Plan(true, false, false), script);
        assertEquals(Step.BLOCKED, loop.tick(0));
        assertEquals(Stage.PROCURE, loop.stage());
        assertTrue(script.persisted.isEmpty());
        assertEquals(Step.DONE, run(loop, 5));
    }

    @Test
    void completedStageIsNotRepeatedWhileItsBoundaryCannotBeSaved() {
        var script = new Script();
        script.failPersists = 2;
        var loop = new ProductionLoop(new Plan(false, false, true), script);
        assertEquals(Step.BLOCKED, loop.tick(0));
        assertEquals(Step.BLOCKED, loop.tick(1));
        assertEquals(Stage.PROCESS, loop.stage());
        assertEquals(List.of("PROCESS"), script.calls);
        assertEquals(Step.PENDING, loop.tick(2));
        assertEquals(Stage.SELL, loop.stage());
    }

    @Test
    void portExceptionsAreUncertainNotRetried() {
        var loop = new ProductionLoop(new Plan(false, false, false), new Script() {
            @Override public Outcome process(long now) { throw new IllegalStateException("menu vanished"); }
        });
        assertEquals(Step.UNCERTAIN, loop.tick(0));
        assertTrue(loop.reason().contains("menu vanished"));
    }

    @Test
    void resumesAtASavedStageAndRejectsOneOutsideThePlan() {
        var script = new Script();
        var resumed = new ProductionLoop(new Plan(true, true, true), Stage.CLAIM, script);
        run(resumed, 5);
        assertEquals(List.of("CLAIM", "SELL"), script.calls);
        var mismatched = new ProductionLoop(new Plan(false, false, true), Stage.AWAIT, new Script());
        assertEquals(Stage.REVIEW, mismatched.stage());
        assertEquals(Step.UNCERTAIN, mismatched.tick(0));
    }
}
