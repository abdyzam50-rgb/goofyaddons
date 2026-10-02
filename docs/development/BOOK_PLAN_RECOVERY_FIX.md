# Book plan recovery correction (1.3.7)

The supplied diagnostics show startup checking an empty order menu repeatedly for four new plans, then stopping before any confirmation. Those unused plans were serialized as ownership and blocked every mode on restart.

Fresh plans no longer trigger missing-order refresh retries at startup. Book journal snapshots contain only observed holdings/orders or submission attempts. The recovery barrier is saved before the confirmation click; failed persistence prevents submission. Stopping releases unused plan allocations while retaining uncertain ownership. Existing legacy journals remain conservative: they lack submission evidence and are not automatically discarded.

Validation: regression tests cover empty restart state after unused plans and preservation of uncertain exposure without unrelated plans. Full test/build run required. Minecraft live replay is not available here.
