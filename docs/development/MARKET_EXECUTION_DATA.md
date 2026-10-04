# Market collection and observed execution

Release 1.3.32 adds four daily public market observation windows, optional Windows
login startup of the continuous local companion, and locally persisted confirmed
whole-position execution samples. See tools/bazaar-calc/README.md for installation,
coverage, retention and calibration requirements.

ExecutionLedger tracks only orders whose buy placement is verified in the live GUI.
The GeneralFlipper Services seam forwards placements only in its live constructor;
test worlds have no global telemetry side effects. Book starts require the complete
input quantity of a single cycle. ProfitTracker forwards deduplicated confirmed
sale/loss receipts after cost accounting, with failures isolated from trade execution.
Active pauses and long tick gaps censor open timers. Open timers are intentionally
not restored after restart. Samples persist separately from profit accounting.

The local server ingests a bounded allowlisted outcome projection from opt-in account
snapshots, deduplicates by receipt identity, retains seven days/2,000 outcomes, and
keeps corrupt files intact. Calibration requires ten exact route/batch matches from
24 hours with uninterrupted timing and known proceeds/cost. It applies an observed
P75 timing floor before recommendations are ranked and sliced. Current market pricing
and profitability are unchanged; execution authority remains false.

The scheduled workflow uploads only explicit public collector files, with no commits
of generated market data. Collection state resumes without claiming continuous
observation across workflow gaps. Continuous PC history should remain the primary
feed when available. Discord notifications now require explicit manual workflow opt-in;
default-branch release builds include the clean companion package.
