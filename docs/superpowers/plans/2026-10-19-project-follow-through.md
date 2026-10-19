# Project follow-through implementation plan

> **For agentic workers:** Execute inline with superpowers:executing-plans, in the user-approved order. Preserve existing changes; no commits/push without user request.

**Goal:** Resolve the eight findings approved in the preceding project review, keeping the single-classroom application simple.
**Architecture:** Repair shared policy and formatting at their source; keep per-question persistence and task snapshots. Add bounded list queries, report reuse/status, and durable historical enrollment using existing Spring/JPA/Vue patterns.
**Tech Stack:** Java 21, Spring Boot/JPA, MySQL, Vue 3/TypeScript, JUnit, Playwright.
**Spec:** User-approved numbered findings in this chat (2026-10-19).

## Global constraints
- Single shared classroom, single JVM; no microservice/distributed expansion.
- Preserve historical answers, original/practice distinction, frozen retry requests, and all existing working-tree changes.
- Verify with isolated test data; never run destructive E2E against the user's database.
- Real teacher scoring cannot be invented; provide evaluation tooling and explicitly retain human-review requirements.

## Review focus
- Scoped/unscoped practice and another archived task must not disclose upcoming/current assignment answers.
- Decimal numbers, formulas, URLs and arbitrary SSE splits must preserve their content.
- Explicit publication rejects missing/null/empty question lists; empty drafts and documented legacy implicit callers remain compatible.
- Late network responses, page/search changes, and cache revisions cannot overwrite another scope.
- Historical participation remains stable after registration/deactivation; last teacher safeguards withstand concurrent updates.

## Tasks (in order)
1. [x] Answer access: add scheduled/cross-task bypass regressions in AnswerAccessPolicyTest; protect all unattempted not-ended published questions through shared policy; run focused tests.
2. [x] Numeric formatting: add decimal/formula/split tests in StreamContentFixerUnitTest/StreamContentBufferUnitTest; constrain list repair to actual line-start markers; verify formatting suite.
3. [x] Published task invariant: test explicit missing/null/empty lists, draft compatibility; validate after legacy parsing in TaskQuestionService; verify lifecycle suite.
4. [x] E2E: update recovery selectors, add explicit isolated-write opt-in and owned fixtures (no blanket task deletion); run full browser journey against isolated MySQL only.
5. [x] Question list: introduce paginated text-only searchable teacher question rows plus existing detail endpoint; update bank management/assignment selection with stable selected-question state; test query payload and browser pagination.
6. [x] AI: distinguish configured from recent upstream outcome without paid probes on page views; reuse persisted task reports only with matching answer revision; expose evaluation capture tooling, never fabricate teacher scores; test stale reports/status transitions.
7. [x] Stable history/accounts: add durable task recipient membership with migration, enroll eligible new students only in not-ended tasks, preserve ended-task statistics; prevent removal/demotion/deactivation of last active teacher transactionally; test roster/account changes.
8. [x] Simplification/docs: remove unused report prompt helpers/reflection-only tests; align resume/start/deployment/evaluation docs and migrations with actual behavior; run full backend/frontend/browser checks and final independent read-only review.

## Execution ledger
- Initial: user explicitly requested sequential execution of all eight findings; proceed without repeated confirmation. Existing dirty checkout is authoritative and will be preserved.

- Task 1: RED scheduled and cross-task practice returned 201 instead of 409; GREEN AnswerAccessPolicyTest + TaskSnapshotLifecycleTest passed. No real database touched.

- Task 2: RED reproduced numeric and inline-list corruption; GREEN formatting/buffer/wire tests passed, including all two-part splits of decimal/equation/URL examples.

- Task 3: RED explicit PUBLISHED without ids succeeded; GREEN lifecycle/submission contracts passed. Empty drafts and existing implicit legacy creation remain compatible.

- Task 4: isolated-write guard test passed; no blanket cleanup remains. Fresh isolated MySQL 8.0.46 port 13309 and backend 18081: full Playwright 27/27 passed, real mutations and mock regressions both included. Production-path interception fixed to exclude documents/assets.

- Task 5: RED catalog endpoint 404 and no paged UI rows; GREEN projection/query tests and two browser pagination/edit/selection cases passed; Vue type check/build passed. Search is escaped, bounded and server-side; late responses ignored. Editing a draft loads its explicit snapshot once rather than per-id requests.

- Task 6: metrics/status, saved-report invalidation and provenance contracts passed; 9 browser report cases and production build passed. Evaluation preparation unit test passed and 10 inputs captured without model calls. Real model scoring remains explicitly pending human review.
- Ruling for Task 7: retain assigned students even after deactivation; enrollment at publish and registration joins only not-ended published tasks. Serialize roster-changing operations and last-teacher decisions using existing active teacher row locks; per-question submissions keep their existing concurrency model. Legacy roster backfill is marked MIGRATED, not claimed as reconstructed historical truth.

- Ruling: use native locking reads on existing active teacher rows as the classroom guard, avoiding an additional singleton table. Roster capture/enrollment also use locking reads for MySQL repeatable-read visibility. Legacy source=MIGRATED is shown explicitly in teacher details.

- Task 7: corrected RED tests showed closed totals 0 instead of 1, ended task total 2 instead of 1, last-teacher change permitted. GREEN 16 relevant H2 contracts (including concurrent self-deactivation) and MySQL 4/4 including real concurrent deactivation; V1→V7 migration passed. The task summary endpoint now uses the same durable recipients as task details/overview; global unscoped statistics retain their active-population semantics.

- Final independent review: found repeatable-read stale recipient lookup, reversed replacement lock order, and migration excluding known participants promoted to teacher. RED reproduced duplicate key on real MySQL, lock-order contract failure, and failed upgrade fixture; fixes use current recipient locking reads, guard-first replacement, and evidence-inclusive migration. Upgrade regression now passed.
- Final default backend verification: 961 tests, 0 failures/errors/skips. Fixed a missing real service import in the SQL regression slice; no assertion weakened. Frontend 10/10 and production type check/build passed; evaluation Python test passed.
- Real model capture: all 10 synthetic cases returned from glm-4-flash-250414, saved with input hashes/model/time/output in docs/ai-evaluation/runs/2026-10-19-glm-4-flash-250414.jsonl. All human reviews remain pending; no quality/accuracy result claimed.

- Final delivery: fresh and V1→V7-upgraded MySQL 8.0.46 each passed 6 tests, including overlapping replacement/submission (existing locking correctly waits then returns the archive conflict). Full Playwright 30/30, isolated HTTP benchmark 400/400 with explicit expected aggregate checks, backup/restore row counts and content checksums matched across 9 tables. Updated stale benchmark lifecycle/recipient fixtures and CI step label.
- Local service updated: private pre-V7 SQL backup created before migration; original users/tasks/answers row counts preserved; backend port18080 health UP. Vite now accepts VITE_DEV_PROXY_TARGET; local port5173 restarted against18080 and proxy health UP. Unrelated port8080 untouched.
- Final frontend rerun: 10/10, type check/build passed. Local Docker/remote CI not run; this limitation remains explicit in delivery docs. Human model grading remains pending, with no fabricated quality conclusion. No commits or pushes performed.
