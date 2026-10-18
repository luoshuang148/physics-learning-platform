# Backend simplification implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan in this session.

**Goal:** Reduce duplicate business and request flows while preserving the existing learning lifecycle.

**Architecture:** Keep Spring MVC/JPA and current API contracts. Share equivalent record queries and error responses; replace per-student detail downloads with one database aggregation. Remove demonstrably unused code and misleading comments rather than introducing generic frameworks.

**Tech Stack:** Java 21, Spring Boot/JPA, Vue 3, TypeScript.

**Spec:** The simplification direction approved in this chat: preserve behavior first; retain idempotency, snapshots, authorization, archival, and SSE cancellation.

## Global constraints

- Preserve existing HTTP statuses, error codes, messages and reasons.
- Preserve original/practice scoring, immutable snapshots and user/task isolation.
- Keep existing API routes for compatibility; remove only code with no callers.
- No new dependencies, schema changes, model requests, or production data mutations.
- Work on `codex/simplify-backend`; leave a reviewable local diff without pushing.

## Review focus

- Exception subclass precedence, including token calculation versus quota and session operation versus management.
- Students with no attempts, inactive students and repeated/practice attempts retain the existing aggregate meaning.
- Aggregate statistics must not load entities or image-bearing snapshots.
- Student/anonymous callers cannot access teacher statistics.
- Late responses after selecting another student cannot overwrite that student's details.

## Task 1: Student statistics and requests

**Files:** `AnswerRecordRepository`, `AnswerRecordService` and implementation, `AnswerRecordController`, new `StudentAnswerStats`; frontend answer-record API and `TeacherStudentAnalysisView`; controller and frontend regressions.

**Interface:** `GET /answer-records/teacher/student-stats` returns `{userId,totalCount,wrongCount}[]` for users with attempts. The existing user list supplies zero-attempt students; no per-student list requests for statistics.

- [x] Add a failing controller regression for grouped counts, permissions and no entity loads.
- [x] Add the database projection and protected endpoint; verify the regression.
- [x] Use one stats request and one user-list request in the view; load selected details once and derive wrong records. Guard stale selection responses; verify frontend regressions/build.

## Task 2: Record query convergence

**Files:** `AnswerRecordServiceImpl`, interface/repository comments and unused declarations, existing service/controller tests.

- [x] Verify existing ID/name, empty-history and permission cases before changing shared paths.
- [x] Make ID/name latest-session entry points share one core query, remove duplicate empty-result branches and consolidate identical access checks where behavior is preserved.
- [x] Remove unused code after a whole-repository reference search; retain public routes and snapshot/transaction rules.
- [x] Run record service/controller/repository regression tests.

## Task 3: Exception response convergence

**Files:** `GlobalExceptionHandler`, new HTTP response contract test.

- [x] Pin existing HTTP status/code/message/reason contracts, including subclass precedence and generic safe messages, against the original handler.
- [x] Replace per-exception boilerplate with explicit business/technical dispatch and one response builder; retain parameter-validation and database handling.
- [x] Run the contract test and controller suite; trim obsolete boilerplate comments in touched code.

## Task 4: Delivery

- [x] Run the full default backend suite, frontend tests and production build.
- [x] Review the diff independently, fix important regressions and reverify affected paths.
- [x] Report the actual reduction and remaining functional boundaries; leave local changes for review.

## Progress and decisions

- Baseline: clean `9ea09bd`; default backend 1060/1060 and frontend 9/9/build passed during the preceding review.
- Execution: native in this session, using the approved scope; no further plan approval is needed.
- Existing public APIs and historical compatibility remain: removing them requires a separate product decision and could break existing callers.
- Task 1: grouped stats 3/3 passed; browser regressions first failed on absent aggregation/stale responses, then 3/3 passed. Frontend 9/9 and build passed.
- Task 2: cold student-session query regression first measured 2 JPA queries, then 1; record service 30/30 passed. Corrected two mock fixtures that returned the wrong user ID and an obsolete assertion requiring duplicate reads.
- Task 3: 41 HTTP exception contracts passed before and after consolidation. Preserved existing messages, including the legacy repeated statistics-error prefix; changed log labels only.
- Cleanup decision: removed database startup diagnostics (including the user-table/password-hash dump), the unreferenced SSE builder and an uncalled private environment helper. JPA schema validation, connection-pool validation and Actuator health remain responsible for normal database checks.
- Readability decision: retained repository methods exercised by existing tests and all public routes; removed SQL-repeating Javadocs rather than deleting supported behavior. Remaining ID/name access checks have different legacy messages, so they were not forced into a generic abstraction.
- Final review: independent read-only reviewer found no remaining actionable defects. The nullable-correctness finding was reproduced by a browser test (expected one wrong record, received two), fixed with a strict `correct === false` filter, and all three browser regressions passed afterward.
- Final verification: `./mvnw -B clean test` with JDK 21: 1105 passed, zero failures/errors/skips; `npm test`: 9 passed; frontend production build passed; controlled-network browser tests: 3 passed; `git diff --check` clean.
- Preserved boundary: the legacy latest-session lookup still sorts session IDs rather than answer timestamps. This pre-existing behavior was deliberately not redefined by a behavior-preserving simplification.
- Delivery: local changes on `codex/simplify-backend`, no push or merge. Existing frontend build warning about a bundle over 500 kB remains unchanged.
