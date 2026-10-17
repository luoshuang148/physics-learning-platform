# Backend internship readiness implementation plan

> Execute inline with superpowers:executing-plans. Each task has a RED/GREEN test cycle, verification and its own commit.

Goal: deliver the accepted design as reviewable, tested improvements.
Architecture: preserve Spring MVC and JPA; enforce ownership at service and persistence boundaries, isolate request resources, use bounded admission and SQL pagination.
Tech stack: Java 21, Spring Boot 3.3.8, MySQL 8.4, H2, Vue 3.
Spec: docs/superpowers/specs/2026-10-18-backend-readiness-design.md

Global constraints: baseline pushed before modifications; private physics-learning-platform repository; no secrets; retain license; commit each major change; default tests do not call external AI services.
Review focus: forged registration role; shared session IDs across users; disconnect before binding/upstream headers; simultaneous publish/archive and record writes; empty/out-of-range/filtered page correctness.

## Task 1 Registration
Files: AuthServiceImpl, RegisterRequest, RegisterView, AuthServiceUnitTest, AuthControllerIntegrationTest.
- [ ] Add direct-registration TEACHER rejection regression, run and observe failure.
- [ ] Enforce STUDENT-only registration before persistence; remove frontend secret/teacher selection and document promotion.
- [ ] Run auth unit/controller tests, frontend build; commit.

## Task 2 Session ownership and lifecycle
Files: SessionManager, ConversationHistoryRepository, ZhiPuAIAssistantServiceImpl, SessionManagerUnitTest and new ownership integration test.
- [ ] Tests: identical session IDs for different users never share context/history; expired sessions reclaimed without timestamps changing; cleared history belongs only to requesting user; context recovery survives a fresh manager.
- [ ] Run failing tests; implement user-scoped keys/queries, recovery, bounded cleanup; update obsolete implementation-mirroring tests.
- [ ] Run session and AI tests; commit.

## Task 3 Streaming resources
Files: StreamUtils, AIStreamCallback, vendor clients, streaming services, AIClientConfig, regression tests.
- [ ] Test competing emitters and cancellation before binding, cancellation while upstream stalls, saturated executor rejection and emitter release.
- [ ] Run failing tests; implement request cancellation hooks, single-flight ownership, per-user admission and explicit bounded rejection cleanup.
- [ ] Run streaming unit/integration tests; commit.

## Task 4 Paged query
Files: answer-record DTO/repository/service/controller, TeacherRecordManageView, frontend API/types; repository integration test.
- [ ] Test stable pages, wrong/name filters, authorization, bounded page size and constant SQL count with distinct questions.
- [ ] Run failing tests; implement projection-based page query and batch legacy conversion, migrate teacher UI.
- [ ] Run answer record tests/frontend build; record real SQL counts and commit.

## Task 5 Task archive
Files: TeacherEvent entity/repository/service/controller/UI, eventId fields in answers/conversations/reports, schema/upgrade script and tests.
- [ ] Test archive preserves records/history/reports; new writes retain eventId; duplicate active slots rejected; history readable after archive.
- [ ] Run failing tests; implement nullable singleton slot and archival, capture event association on creation; update destructive old expectations.
- [ ] Run event/repository/AI tests; commit.

## Task 6 Reproducible delivery
Files: Maven wrapper, pom, application-dev.yml, .github/workflows, MySQL integration test and benchmark guide.
- [ ] Add repeatable MySQL smoke test for schema, uniqueness and projection, isolated from H2 tests.
- [ ] Generate wrapper, configure bounded test forks, dev profile and CI. Verify wrapper/full suite/frontend build; check MySQL CI.
- [ ] Commit.

## Task 7 Documentation and final verification
Files: README and focused operational/resume documentation.
- [ ] Correct unsupported claims, document current features, setup, teacher provisioning, migrations, query measurements and limits.
- [ ] Run full backend suite, frontend build, inspect git diff and obtain fresh independent review.
- [ ] Fix material review findings with regression tests; commit and push; verify remote HEAD.
