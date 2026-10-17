# Product hardening implementation plan

> REQUIRED SUB-SKILL: superpowers:executing-plans, inline execution with one fresh final reviewer. User already authorized the preceding review and implementation.

Goal: resolve the accepted review as runnable, tested product behavior.
Architecture: preserve single shared classroom and existing MVC/JPA/Vue monolith, add task snapshots and explicit request/resource ownership.
Tech: Java21, MySQL8.4, Vue3/TypeScript, JUnit, Node tests, Playwright.
Spec: docs/superpowers/specs/2026-10-18-product-hardening-design.md

Global constraints: no secrets or real business DB changes; legacy data retained; each major task committed; no model billing without user credentials/authorization. No microservices/Redis/MQ or multi-tenant scope invention.
Review focus: malicious AI HTML and links; stale task tabs replacing a newer task; retry vs deliberate reattempt; old answers after question-bank edits; database failure after generated content and during task replacement.

## Task 1 Security and shared rendering
Files: frontend/src/utils/markdown.ts, AI presentation components, account/router/auth modules, AppShell; related Node tests.
- [ ] RED tests raw HTML/event/script URL and valid math; credential cache and role-edit route contracts.
- [ ] Shared sanitized renderer; scrub/remove debug credentials/routes and self-role controls.
- [ ] npm test/build, commit.
Produces renderMarkdown(content), auth cleanup without secondary credential storage.

## Task 2 Task snapshots and atomic lifecycle
Files: TeacherEvent/TeacherEventQuestion, repositories/service/controller/DTO, SQL V3/fresh schema, task frontend API/views, integration tests.
- [ ] RED tests task snapshot survives bank edit, stale expected task ID rejects archive/replace, failed replacement leaves current task.
- [ ] Structured ordered questionIds request, immutable snapshots, transactional expected-ID replacement and archive, non-destructive legacy conversion.
- [ ] Task and MySQL tests/frontend build, commit.
Produces eventId-scoped question snapshot query and replacement/archive endpoints.

## Task 3 Scoped idempotent submissions and practice
Files: AnswerRecord/request/response/repository/service/controller, StudentQuestion/WrongBook APIs/types/views, schema V3/fresh, regression tests.
- [ ] RED cross-task progress, out-of-task/archived/invalid option submission, duplicate request, explicit reattempt and historical snapshot assertions.
- [ ] Lock active task, require event identity, user+requestId uniqueness, snapshot answers, task scoped progress/mastery and reattempt navigation.
- [ ] Answer/task tests plus frontend build, commit.
Produces eventId/requestId/attempt history; latest correct attempt resolves mastery.

## Task 4 Database aggregates and product flow
Files: answer stats DTO/repository/service, TeacherDashboard/Record/Event/Student pages, tests.
- [ ] RED current-task vs lifetime counts, latest-attempt mastery, archived task empty/reused questions.
- [ ] SQL aggregate API and task filters; dashboard uses summaries, archive details, clear single-class copy and state/empty/error UI.
- [ ] Targeted tests/frontend build, commit.

## Task 5 Authentication and operational boundaries
Files: User/JwtUtil/auth service/interceptor/config, login admission, schema V3/fresh, tests.
- [ ] RED bounded login throttle, old JWT after password/logout, exact health/doc paths, private metric endpoints.
- [ ] Persistent token version, centralized path policy and throttling, no client-controlled auth identity; sanitized production logs/errors.
- [ ] Auth/controller tests, commit.

## Task 6 AI state reliability and report quality
Files: SessionManager/history repository/report input/prompt service/metadata, capability API/UI, metrics, tests and evaluation fixtures.
- [ ] RED bounded restored rows with correct aggregate accounting, DB save failure observable, event-scoped student report, no names in prompt and exact computed statistics.
- [ ] Implement bounded restore, failure semantics, report preparation extraction, provenance/capabilities and resource/upstream metrics.
- [ ] AI/session/controller tests/frontend build; fixed eval input invariants, commit.

## Task 7 End-to-end and delivery evidence
Files: frontend/e2e, CI/Docker config, scripts/benchmark and backup restore, runbooks/ledger.
- [ ] Browser journey publish-answer-archive-reuse-practice plus security render smoke tests.
- [ ] Real MySQL fresh/V2+V3 upgrade + backup/restore; actual Docker image boot in CI; measured HTTP benchmark with fake data/AI off.
- [ ] Full backend/frontend/e2e, inspect evidence; independent whole-branch reviewer and one regression-tested fix pass.
- [ ] Update docs/ledger, commit/push and verify GitHub CI/remote HEAD.
