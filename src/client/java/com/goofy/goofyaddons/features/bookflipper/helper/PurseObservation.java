package com.goofy.goofyaddons.features.bookflipper.helper;

/** Unknown scoreboard data delays a purchase; it is never an available balance. */
public final class PurseObservation {
    public enum Result { READY, WAITING, TIMED_OUT }
    private Long missingSince;

    public Result observe(double purse, long now) {
        if (Double.isFinite(purse) && purse >= 0) {
            reset();
            return Result.READY;
        }
        if (missingSince == null) missingSince = now;
        return now - missingSince >= 10_000 ? Result.TIMED_OUT : Result.WAITING;
    }

    public void reset() { missingSince = null; }
}
