# Unified Study Assistant Implementation Plan

> **For agentic workers:** Implement inline with superpowers:executing-plans. User explicitly requested “直接改”; proceed without further design or execution approval.

**Goal:** Replace the four student AI modes with one conversation that routes questions to explanations, platform RAG, wrong-answer tutoring, or learning plans.

**Architecture:** Keep Vue and existing Spring Boot services. Resolve intent on the server using explicit requests and bounded previous-turn context; reuse authorized retrieval and tutoring endpoints. Render sources, missing-context choices, and plan previews inside the conversation.

**Tech Stack:** Vue 3, TypeScript, Spring Boot 3 / Java 21, JUnit, Playwright.

**Spec:** The approved design in this chat: one composer, a continuous conversation, automatic routing, contextual clarification, source citations, and plan confirmation cards.

## Global Constraints

- No new dependencies or manual mode selector.
- Platform questions must use actual authorized retrieval; distinguish unavailable retrieval from no match.
- Preserve Markdown and LaTeX rendering, stop generation, plan save/activate/practice, and original answer-record context.
- Keep user authorization and source access checks on existing server services.
- Implement on `codex/unified-study-assistant` in the supplied checkout. The user subsequently requested committing the workspace, merging into `main`, and pushing to GitHub.

## Review Focus

- Follow-up retrieval retains the subject; a new concept question exits retrieval.
- Negated search requests do not trigger platform retrieval.
- Missing assignment or wrong-answer context prompts inline instead of asking for IDs.
- Stops, HTTP errors, and source permission failures cannot look like successful answers.
- Mobile layouts, keyboard submission, and reloading saved plans remain usable.

### Task 1: Server intent resolution

**Files:** Create `AssistantIntentRequest.java`, `AssistantIntentService.java`, `AssistantIntentServiceTest.java`; modify `StudyAssistantController.java`.
**Interfaces:** `POST /ai/student/assistant/resolve` returns `{intent, query, days, excludeAttempted}` for a question and optional previous question/intent.

- [x] Write and run failing tests for general/platform/wrong/plan, contextual retrieval, new topics, negated retrieval, and Chinese day counts.
- [x] Implement the bounded resolver and validated endpoint; run the targeted JUnit tests.

### Task 2: Reliable assistant responses

**Files:** Modify `PlatformQaService.java`, `AssistantModelGateway.java`, and `frontend/src/api/study-assistant.ts`; add gateway regression tests.
**Interfaces:** Existing assistant SSE supports streamed `content`, cancellation, explicit errors, and optional retrieval filters.

- [x] Add regression tests for immediate cancellation and provider errors, run RED.
- [x] Correct gateway cancellation/error propagation, stream answer fragments, support follow-up retrieval queries, and fix authentication/SSE framing in the frontend client.
- [x] Run assistant and gateway tests.

### Task 3: Single conversation UI

**Files:** Replace `StudentAIView.vue`; create `AssistantLearningPlan.vue`; refine `PlatformSources.vue`; update `learning-assistant.spec.ts` and add `unified-assistant.spec.ts`.
**Interfaces:** Main view dispatches server-resolved intents; plan component owns its existing lifecycle and emits busy changes. Sources expand after renewed authorization.

- [x] Write browser tests for one composer, automatic RAG, contextual follow-up, ordinary explanation, missing wrong-answer context, plan lifecycle, and errors; run RED.
- [x] Implement a restrained single-column conversation, inline source cards, wrong-answer picker, plan card, keyboard handling, and responsive composer.
- [x] Run frontend tests/build and isolated browser tests with mocked network boundaries.

### Task 4: Verification and review

- [x] Run the backend unit suite, frontend unit suite, production build, and relevant browser suite.
- [x] Inspect desktop and mobile screenshots and fix material layout failures.
- [x] Request one independent review, address important findings, and report actual validation and limitations.


## Execution notes

- The user opted for direct implementation, so no additional planning approval was requested.
- Implementation stayed in the supplied checkout on `codex/unified-study-assistant`, with no new dependencies.
- Intent resolution uses explicit language rules plus previous-turn context to avoid a separate model call. General responses include up to eight prior conversation turns; source-derived answers are reauthorized before reuse.
- Independent review findings were addressed: conversational answer context, explicit similar-question search priority, new-topic explanations, wrong-answer session isolation, plan teardown, and cancelled-run persistence. The provider's blocking I/O is supervised on the existing bounded AI executor so cancellation can reach an upstream call.
- Automated browser verification uses isolated mocked API boundaries. A separate live browser check with the local demo student confirmed model responses, platform retrieval, and opening authorized question sources.
- `npm test --prefix frontend`: 15 passed.
- `npm run build --prefix frontend`: passed.
- Assistant Playwright suites: 11 passed, including desktop/mobile, retrieval follow-ups, revoked sources, cancellation, wrong-question session reset, plan save/activate/reload/practice, and leaving during task loading.
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Punit-test test`: 647 passed, zero failures/errors, including cancellation persistence and blocked-upstream cancellation regressions.
- Full backend verification with Colima: 1085 tests passed, zero failures/errors/skips. The test process uses the Colima socket through `DOCKER_HOST` and maps Ryuk to `/var/run/docker.sock` through `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`.
- Integration fixes align Hibernate's AI field defaults with the existing MySQL migration, batch-load question knowledge points (25 questions now require 3 SQL statements instead of 27), and disable environment-driven bootstrap teacher creation in the test profile. Regression tests cover defaults and returned knowledge point codes.
- The existing local backend needed restarting to load `/ai/student/assistant/resolve`; the live development service was restarted and verified after the change.
- The saved-plan entry and panel title are both labeled “我的复习计划”.
