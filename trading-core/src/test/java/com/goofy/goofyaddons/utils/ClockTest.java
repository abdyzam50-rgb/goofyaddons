package com.goofy.goofyaddons.utils;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ClockTest {
    @Test void repeatedPollingDoesNotRestartAnActionDeadlineAndFireIsConsumedOnce() {
        var now=new AtomicLong(1000);var clock=new Clock(now::get);
        clock.start(100);now.set(1099);clock.start(100);
        assertFalse(clock.shouldFire());now.set(1100);
        assertTrue(clock.shouldFire());assertFalse(clock.shouldFire());
        clock.start(100);clock.stop();now.set(2000);assertFalse(clock.shouldFire());
    }
}
