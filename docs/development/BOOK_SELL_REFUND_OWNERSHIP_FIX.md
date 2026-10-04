# Cancelled book sell offer ownership (1.3.44)

The 1.3.42 diagnostic session `23580ae2-a3dc-487f-b453-adccdba8797a`
returned one Chimera III after a sell-offer cancellation (events 3091–3093).
`REPLACE_SELL` excluded the output from physical reconciliation as though it
remained in escrow. Reconciliation consequently classified the refund as a new
extra (3104), stored it (3139), and then paused the original trade after four
orders visits could find neither its offer nor its inventory book (3274–3276).
This predates the 1.3.43 stale-book cleanup feature.

Both book sale paths now start a bounded cancellation observation before the
cancel click. Pending cancellation holds menu ownership and bypasses ordinary
loss/found reconciliation. It requires a different, loaded orders container,
1.5 seconds of stable contents, absence of the matching sell offer, an empty
cursor, all 36 inventory slots, and exactly one matching single-enchantment
output. The existing holding is bound to the observed inventory slot and its
original trade returns to SELL. No acquisition, profit, loss, or extra-book
entry is invented. A missing or ambiguous return retains ownership and pauses
without another cancellation click after the bounded observation fails.

Sale navigation also uses an already present output before scheduling additional
missing-order rechecks. Stop/start clears the temporary observer; saved ownership
continues through the existing startup reconciliation.

Regression coverage includes refund ownership and population reconciliation,
stale containers, remaining offers, delayed inventory packets, ambiguous copies,
missing-return timeout, and transaction reset. The Gradle build and calculator
integration check pass. Live Minecraft verification is still required.

Install the 1.3.44 JAR and restart Minecraft. Preserve configs, order journals,
and the existing 1.3.40 companion. Startup can reconcile the retained Chimera
from storage; do not clear the saved position to work around this pause.
