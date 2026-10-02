# Reliability repairs

1. Recover from failed Bazaar requests, impose request timeouts, and apply async
   results only on the Minecraft client thread. Ignore results from stopped runs.
2. Implement pause/resume and reset recovery on stop or disconnect. Retry empty
   results, and monitor only genuinely better competing prices.
3. Account for sale tax and configurable minimum net profit; reserve candidate
   purchase budgets and prevent multiple tasks from owning the same enchantment.
4. Validate configuration without overwriting malformed files, use configured
   key defaults, and guard inventory access and book assignment.
5. Add regression coverage for these failure modes and run the build. Document
   any checks blocked by the environment and remaining unfinished features.

The settings GUI, general item flipping, full recipe discovery, and a realized
profit dashboard remain separate work. These changes do not establish an hourly
profit guarantee.

## Completion

Implemented the repair batch. All 16 JUnit regression tests passed and the
Java 25 Gradle `test build` succeeded. Minecraft client and main sources compiled.
`git diff --check` passed. Maven Central rate limiting was resolved with a
session-only init script redirecting central repository and artifact URLs to
Google's Maven Central mirror; repository dependency versions are unchanged.

In-game menu, anvil, and travel checks remain unverified. See REPAIR_NOTES.md for
remaining feature work and transaction-confirmation limitations.
