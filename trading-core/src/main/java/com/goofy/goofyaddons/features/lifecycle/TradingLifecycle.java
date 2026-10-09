package com.goofy.goofyaddons.features.lifecycle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The single owner of whether trading is started, paused or blocked.
 *
 * <p>Manual controls, the rest schedule, server transfers, scheduled reboots and engine
 * safety halts all used to flip the same flags directly, each with its own early returns.
 * They now submit a request here and act only when it is granted, so the precedence is
 * written once and every decision is recorded:
 *
 * <ul>
 *   <li>A stop of running trading always wins and clears everything.</li>
 *   <li>A safety block latches a pause. The first reason is kept; later ones are recorded
 *       beside it rather than replacing it, so the original cause stays visible.</li>
 *   <li>Only a person (or the remote control acting for one) may clear a safety block, by
 *       starting or resuming. Schedules, transfers and reboots never do.</li>
 *   <li>A transfer restart needs a travel pause with no safety block.</li>
 * </ul>
 *
 * <p>This class decides; it never touches an engine. The caller applies a granted request.
 */
public final class TradingLifecycle {
    public enum Source {
        MANUAL(true), REMOTE(true), SCHEDULE(false), TRANSFER(false), REBOOT(false), ENGINE(false);
        private final boolean person;
        Source(boolean person) { this.person = person; }
        /** Whether this source speaks for a person who has seen why trading stopped. */
        public boolean person() { return person; }
    }

    public enum Request { START, PAUSE, RESUME, RESTART_AFTER_TRANSFER, BLOCK, REFUSE, STOP }

    public enum Outcome { GRANTED, IGNORED, DENIED }

    public record Transition(long at, Source source, Request request, Outcome outcome, String detail) {}

    private static final int HISTORY = 20, LATER_REASONS = 5;

    private boolean started, paused;
    private String reason = "";
    private final List<String> laterReasons = new ArrayList<>();
    private final Deque<Transition> history = new ArrayDeque<>();

    public boolean started() { return started; }
    public boolean paused() { return paused; }
    public String reason() { return reason; }
    public boolean blocked() { return !reason.isBlank(); }

    /** Whether a start may proceed to the configuration and recovery checks. */
    public Outcome start(Source source, long now) {
        if (blocked() && !source.person()) return record(now, source, Request.START, Outcome.DENIED, reason);
        if (started && !paused) return record(now, source, Request.START, Outcome.IGNORED, "already running");
        return record(now, source, Request.START, Outcome.GRANTED, "");
    }

    /** The start passed its checks: trading is running with no reason outstanding. */
    public void running() { started = true; paused = false; clearReason(); }

    /** A start was refused before trading began, for example a rejected config file. */
    public void refuse(Source source, String why, long now) {
        reason = why == null ? "" : why;
        laterReasons.clear();
        record(now, source, Request.REFUSE, Outcome.GRANTED, reason);
    }

    public Outcome pause(Source source, long now) {
        if (!started || paused) return record(now, source, Request.PAUSE, Outcome.IGNORED, started ? "already paused" : "not started");
        paused = true;
        return record(now, source, Request.PAUSE, Outcome.GRANTED, "");
    }

    /** Latches a pause for safety. Always granted, even when stopped or already paused. */
    public Outcome block(Source source, String why, long now) {
        paused = true;
        String text = why == null || why.isBlank() ? "Trading blocked" : why;
        if (reason.isBlank()) reason = text;
        else if (!reason.equals(text) && !laterReasons.contains(text) && laterReasons.size() < LATER_REASONS) laterReasons.add(text);
        return record(now, source, Request.BLOCK, Outcome.GRANTED, text);
    }

    public Outcome resume(Source source, long now) {
        if (!started || !paused) return record(now, source, Request.RESUME, Outcome.IGNORED, started ? "not paused" : "not started");
        if (blocked() && !source.person()) return record(now, source, Request.RESUME, Outcome.DENIED, reason);
        paused = false;
        clearReason();
        return record(now, source, Request.RESUME, Outcome.GRANTED, "");
    }

    /** After a server transfer: only a plain travel pause may be restarted automatically. */
    public Outcome restartAfterTransfer(long now) {
        if (!started || !paused) return record(now, Source.TRANSFER, Request.RESTART_AFTER_TRANSFER, Outcome.IGNORED, "not paused for travel");
        if (blocked()) return record(now, Source.TRANSFER, Request.RESTART_AFTER_TRANSFER, Outcome.DENIED, reason);
        return record(now, Source.TRANSFER, Request.RESTART_AFTER_TRANSFER, Outcome.GRANTED, "");
    }

    /**
     * Stops trading and clears any block. When trading never started there is nothing to
     * stop, and a reason recorded meanwhile (such as a schedule that could not arm) is kept.
     */
    public Outcome stop(Source source, long now) {
        if (!started) return record(now, source, Request.STOP, Outcome.IGNORED, "not started");
        started = false; paused = false; clearReason();
        return record(now, source, Request.STOP, Outcome.GRANTED, "");
    }

    public List<Transition> history() { return List.copyOf(history); }

    public Map<String, Object> diagnosticState() {
        return Map.of("started", started, "paused", paused, "reason", reason,
                "laterReasons", List.copyOf(laterReasons),
                "recent", history.stream().map(t -> t.source() + " " + t.request() + " " + t.outcome()
                        + (t.detail().isBlank() ? "" : ": " + t.detail())).toList());
    }

    private void clearReason() { reason = ""; laterReasons.clear(); }

    private Outcome record(long now, Source source, Request request, Outcome outcome, String detail) {
        history.addLast(new Transition(now, source, request, outcome, detail == null ? "" : detail));
        while (history.size() > HISTORY) history.removeFirst();
        return outcome;
    }
}
