package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PurseObservationTest {
    @Test void loggedUnknownReadingWaitsUntilTheBalanceReturns() {
        var observation = new PurseObservation();
        assertEquals(PurseObservation.Result.READY, observation.observe(62_546_627, 1000));
        assertEquals(PurseObservation.Result.WAITING, observation.observe(-1, 2000));
        assertEquals(PurseObservation.Result.WAITING, observation.observe(-1, 2400));
        assertEquals(PurseObservation.Result.READY, observation.observe(62_546_632, 2600));
    }

    @Test void repeatedUnknownReadingsCannotResetTheDeadline() {
        var observation = new PurseObservation();
        assertEquals(PurseObservation.Result.WAITING, observation.observe(-1, 0));
        assertEquals(PurseObservation.Result.WAITING, observation.observe(Double.NaN, 9999));
        assertEquals(PurseObservation.Result.TIMED_OUT, observation.observe(Double.POSITIVE_INFINITY, 10000));
        assertEquals(PurseObservation.Result.TIMED_OUT, observation.observe(-1, 15000));
    }

    @Test void validZeroIsReadyForTheAffordabilityCheckAndClearsTheMissingDeadline() {
        var observation = new PurseObservation();
        observation.observe(-1, 0);
        assertEquals(PurseObservation.Result.READY, observation.observe(0, 9999));
        assertEquals(PurseObservation.Result.WAITING, observation.observe(-1, 10000));
        assertEquals(PurseObservation.Result.TIMED_OUT, observation.observe(-1, 20000));
    }

    @Test void newRunDoesNotInheritAnUnreadableDeadline() {
        var observation = new PurseObservation();
        observation.observe(-1, 0);
        observation.reset();
        assertEquals(PurseObservation.Result.WAITING, observation.observe(-1, 50000));
    }
}
