package com.goofy.goofyaddons.diagnostics;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded queue: ordinary work cannot occupy capacity reserved for critical evidence. */
public final class DiagnosticQueue {
    private record Work(boolean critical, Runnable action) implements Runnable {
        public void run() { action.run(); }
    }
    private final ThreadPoolExecutor worker;
    private final int ordinaryLimit;
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong pendingDropped = new AtomicLong();
    private final AtomicLong criticalDropped = new AtomicLong();
    public DiagnosticQueue(int capacity, int criticalReserve) {
        if (capacity < 2 || criticalReserve < 1 || criticalReserve >= capacity) throw new IllegalArgumentException("Invalid queue capacity");
        ordinaryLimit = capacity - criticalReserve;
        worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(capacity),r -> {
            Thread thread = new Thread(r,"Goofy diagnostics"); thread.setDaemon(true); return thread;
        });
    }
    public synchronized boolean submit(boolean critical, Runnable action) {
        if (!critical && worker.getQueue().size() >= ordinaryLimit) { dropped(false); return false; }
        Work work = new Work(critical,action);
        try { worker.execute(work); return true; }
        catch (RejectedExecutionException full) {
            if (critical && !worker.isShutdown()) {
                for (Runnable queued : worker.getQueue()) {
                    if (queued instanceof Work old && !old.critical() && worker.getQueue().remove(old)) {
                        dropped(false);
                        try { worker.execute(work); return true; } catch (RejectedExecutionException ignored) { break; }
                    }
                }
            }
            dropped(critical); return false;
        }
    }
    private void dropped(boolean critical) {
        totalDropped.incrementAndGet(); pendingDropped.incrementAndGet();
        if (critical) criticalDropped.incrementAndGet();
    }
    public long totalDropped() { return totalDropped.get(); }
    public long criticalDropped() { return criticalDropped.get(); }
    public long takePendingDropped() { return pendingDropped.getAndSet(0); }
    public void restorePendingDropped(long count) { pendingDropped.addAndGet(count); }
    public void shutdown() { worker.shutdown(); }
    public boolean awaitTermination(long time, TimeUnit unit) throws InterruptedException { return worker.awaitTermination(time,unit); }
}
