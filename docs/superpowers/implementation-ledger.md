# Implementation ledger

Baseline: ccde476 uploaded before any change. Default test run: 993 tests, 1 failure (wall-clock performance threshold), 75 errors (fixture contamination and transaction timeouts). Frontend production build passed. Isolated auth/video rerun: 46 passed.

Ruling: use the newly initialized clean checkout and a feature branch in place; no extra worktree needed for existing changes.
Ruling: fix test isolation first, before product changes, because existing failures prevent trustworthy regression testing.
Design builder check: nothing important left. User already authorized the preceding review recommendations; implementation proceeds inline.

Task 0: complete — isolated integration contexts after each class, Java 21 compiler release, one configurable test fork, 120-second test transaction budget, Maven Wrapper 3.9.9. Default pre-product suite: 993/993 passed (mvn -B test); wrapper -v verified Java 21/Maven 3.9.9. New task-specific regression sources remain outside this commit until their own RED/GREEN cycles.
