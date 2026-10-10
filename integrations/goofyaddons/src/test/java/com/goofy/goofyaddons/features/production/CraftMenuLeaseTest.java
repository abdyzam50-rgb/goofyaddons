package com.goofy.goofyaddons.features.production;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftMenuLeaseTest {
    @Test void waitingOrderYieldsToAnotherCraftAndReadyJobsCannotInterruptAnOpenTransaction() {
        var lease=new CraftMenuLease<String>();var ready=new HashSet<>(Set.of("first"));
        var jobs=List.of("first","second","third");
        assertEquals("first",lease.select(jobs,ready::contains,false));
        ready.add("second");assertEquals("first",lease.select(jobs,ready::contains,false));
        ready.remove("first");assertEquals("second",lease.select(jobs,ready::contains,false));
        ready.add("first");assertEquals("second",lease.select(jobs,ready::contains,false));
        ready.remove("second");assertEquals("first",lease.select(jobs,ready::contains,false));
    }
    @Test void delegatedCraftOrListingHasExclusivePriorityAndRemovedJobsLoseTheirLease() {
        var lease=new CraftMenuLease<String>();var ready=new HashSet<>(Set.of("first","second"));
        assertEquals("first",lease.select(List.of("first","second"),ready::contains,false));
        assertNull(lease.select(List.of("first","second"),ready::contains,true));
        assertEquals("first",lease.select(List.of("first","second"),ready::contains,false));
        assertEquals("second",lease.select(List.of("second"),ready::contains,false));
        ready.clear();assertNull(lease.select(List.of("second"),ready::contains,false));
        lease.clear();assertNull(lease.select(List.of(),ready::contains,false));
    }
}
