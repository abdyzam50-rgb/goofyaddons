package com.goofy.goofyaddons.diagnostics;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticQueueTest {
    @Test void saturatedProgressCannotConsumeCriticalReserveAndLossCountPersists() throws Exception {
        DiagnosticQueue queue=new DiagnosticQueue(4,2);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger critical=new AtomicInteger();
        try {
            assertTrue(queue.submit(false,()->{entered.countDown();try {release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}}));
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(queue.submit(false,()->{}));assertTrue(queue.submit(false,()->{}));
            assertFalse(queue.submit(false,()->fail("dropped progress ran")));
            assertTrue(queue.submit(true,critical::incrementAndGet));assertTrue(queue.submit(true,critical::incrementAndGet));
            // Once fully occupied, a critical event evicts ordinary queued work.
            assertTrue(queue.submit(true,critical::incrementAndGet));
            assertEquals(2,queue.totalDropped());assertEquals(2,queue.takePendingDropped());
            assertEquals(0,queue.takePendingDropped());assertEquals(2,queue.totalDropped());
            assertEquals(0,queue.criticalDropped());
        } finally {release.countDown();queue.shutdown();assertTrue(queue.awaitTermination(2,TimeUnit.SECONDS));}
        assertEquals(3,critical.get());
    }
    @Test void criticalOnlyOverflowIsExplicitlyCountedAndRejectedAfterShutdown() throws Exception {
        DiagnosticQueue queue=new DiagnosticQueue(2,1);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            queue.submit(true,()->{entered.countDown();try {release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(queue.submit(true,()->{}));assertTrue(queue.submit(true,()->{}));
            assertFalse(queue.submit(true,()->{}));assertEquals(1,queue.criticalDropped());
            queue.restorePendingDropped(queue.takePendingDropped());assertEquals(1,queue.takePendingDropped());
        } finally {release.countDown();queue.shutdown();assertTrue(queue.awaitTermination(2,TimeUnit.SECONDS));}
        assertFalse(queue.submit(true,()->{}));assertEquals(2,queue.criticalDropped());
    }
}
