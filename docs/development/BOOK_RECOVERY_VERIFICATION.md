# Automatic stale-book recovery verification — 1.3.25-BETA

**Updated in 1.3.29:** verified supported positions now resume automatically.
See [Book session continuation](BOOK_SESSION_RESUME.md). The description below
records the original read-only verification implementation.

The startup barrier treated every non-empty legacy book journal as proof of
outstanding ownership and never checked the server. Stop also latched
`recoveryRequired` before its checkpoint could remove the last exposure; an empty
journal could therefore block a same-process restart.

`restoreBudget` now rereads stopped recovery state, removes reservations absent
from a valid journal, and clears that stale latch when the file is empty. A valid
non-empty file becomes a verification task instead of a blanket startup refusal.
`FeatureManager` gives that task exclusive menu access before starting either
normal trader, reports RECOVERING, and lets J retry a paused verification.

`BookRecoveryCheck` uses the existing menu observation/action seams. It inspects
closed inventory including equipment/offhand, both configured storage pages, and
one recognized orders menu. Complete layouts must remain unchanged for 1.5 seconds;
loaded markers and all 36 main inventory slots are required. It only opens and
closes menus; no item/order clicks, quantity signs, claims or submissions occur.
Saved routes are checked independently of current config and profit eligibility.
It detects input/intermediate/output books and matching BUY/SELL orders, including
completed sell offers. Co-op creators are checked; product icons are not physical
holdings. Paginated/unreadable menus, occupied cursors and timeouts cannot establish
absence. Other unconfigured storage is outside this legacy journal's coverage.

After complete verification, `BookJournal.reconcileVerified` rereads expected
records, copies the original to a unique backup, then writes only records still
present. Reservations are released only after that write succeeds. An all-stale
journal continues normal startup automatically; genuine positions remain paused
with identified enchantments. The coarse journal cannot restore exact quantities,
trade ids, costs/proceeds or submission/receipt history, so this change does not
implement replay of real outstanding book trades or fabricate realized profit.

Tests cover complete absence, physical storage/inventory/offhand books, filled
sell orders, co-op ownership/icons, pagination, changing menus, carried items,
timeouts, preservation of actual records, backups and changed-file protection.
The full build and existing Java-to-companion integration pass. Live Minecraft
menu timing and labels still require verification.
