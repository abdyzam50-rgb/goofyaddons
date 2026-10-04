# Ignored GUI input recovery (1.3.45)

The general trader advanced PRODUCT → QUANTITY, QUANTITY → SIGN and
PRICE → CONFIRM immediately after a click. An ignored click left it waiting for
a screen which never opened, until its 30-second transaction timeout. The book
trader's menu-driven navigation could repeatedly click a still-visible control
at the normal action delay, compounding slowdown messages.

A shared observer now holds these reversible navigation clicks until a different
container/title or the quantity sign appears. A replay needs the exact original
control, unchanged container, empty cursor, and 750 ms of stable observed
contents. It waits at least three seconds between clicks, permits three retries,
and stops after 15 seconds. Slowdown messages extend the quiet window. Both
engines report retries and pending navigation in diagnostics.

The book engine now sends at most one such navigation click per tick. Search,
create-buy, custom amount, sale-menu and price controls use the observer. The
general engine uses it for search/product, custom amount and price navigation.
Final confirmations, cancellation and claim actions keep their separate
transaction-verification logic and are never routed through this observer.

If writing a quantity sign succeeds locally but no price screen arrives within
eight seconds, pre-submission navigation can restart from the product menu up
to twice. It reuses the retained trade and passes normal quantity, price, capital
and confirmation checks. It does not resubmit an already confirmed order.
The book flow uses the same delay/budget when its quantity sign disappears
without the expected next screen. Exhausted attempts retain ownership and pause.
Stop/start and finished work discard the temporary observer.

Tests simulate ignored search/product/quantity/price clicks through two complete
general buy/claim/sell/settlement cycles, and a locally dismissed quantity sign
whose server transition never arrives. Each cycle still submits exactly one
buy and one sell. Helper tests cover stable windows, stale/changed controls,
cursor contents, closed screens, slowdown, retry limits and reset. The build and
calculator integration check pass; live Minecraft timing still needs validation.

Install 1.3.45 and restart Minecraft, preserving existing configuration,
order journals and the unchanged 1.3.40 companion.
