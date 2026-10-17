# Implementation ledger

Baseline: ccde476 uploaded before any change. Default test run: 993 tests, 1 failure (wall-clock performance threshold), 75 errors (fixture contamination and transaction timeouts). Frontend production build passed. Isolated auth/video rerun: 46 passed.

Ruling: use the newly initialized clean checkout and a feature branch in place; no extra worktree needed for existing changes.
Ruling: fix test isolation first, before product changes, because existing failures prevent trustworthy regression testing.
Design builder check: nothing important left. User already authorized the preceding review recommendations; implementation proceeds inline.

Task 0: complete — isolated integration contexts after each class, Java 21 compiler release, one configurable test fork, 120-second test transaction budget, Maven Wrapper 3.9.9. Default pre-product suite: 993/993 passed (mvn -B test); wrapper -v verified Java 21/Maven 3.9.9. New task-specific regression sources remain outside this commit until their own RED/GREEN cycles.

Task 1: complete — direct TEACHER registration reproduced as HTTP 201 before the fix, now rejected with 403; public UI provisions students only. Existing protected teacher user-management flow provisions new teachers. Auth controller/service: 31 passed; frontend production build passed. The duplicate-username unit fixture now supplies its required STUDENT role. Session persistence regressions were intentionally run separately in the same command and are still RED; one recovery fixture contained an invalid hyphenated username and is corrected before its RED rerun.

Task 2: complete — user-scoped cache keys and persistent history reads/deletes; bounded cache admission; history, sequence and token recovery on a fresh manager. Expiry cleanup retains two phases but removes already inactive entries before marking old entries, so repeated real sweeps reclaim memory. Removed the test's artificial timestamp reset. RED: isolation/reclamation and persistence regressions failed on original behavior; GREEN: 38 session/AI sync tests passed. Old mocks/helper keys were aligned with user-scoped contracts.

Task 3: complete — request-owned cancellation hooks cancel actual OkHttp calls during stalled reads; same-conversation active emitters are mutually exclusive; per-user admission is bounded; executor rejection sends an SSE error and releases resources; unexpected task completion also cleans up. Both stalled upstream providers failed the corrected deterministic regression before implementation and pass afterward. Streaming/AI async suite: 68 passed. Ruling: the old partial-response mock expected cleanup never to occur; it now requires final resource cleanup while still asserting partial history persistence.
