# Assignment Review Implementation Plan

**Approved scope:** Retain AI report generation inside the assignment workflow; remove its primary navigation entry. Completed answering links to the selected assignment's results/review. The assignment list offers results for previously answered tasks, including closed/archived tasks. Review displays deterministic first-attempt statistics before optional AI advice.

**Implementation:** Add authenticated own-task progress/results queries without changing the existing available-task API or answer authorization. Reuse the report route with a mandatory eventId and shared task highlighting. Retain streaming/cancellation/persistence, guard asynchronous results across task changes, and scope frontend cache by user/task and answer-data revision.

- [x] Test task progress/history, original-vs-practice statistics and user isolation.
- [x] Add task progress list/result DTO and queries; keep draft tasks private.
- [x] Update four-entry student navigation, task list, answer completion and home links.
- [x] Replace generic report page with selected assignment results and optional AI review; handle missing task, empty answers, disabled AI and task changes safely.
- [x] Verify frontend tests/build, relevant backend tests, browser flows and desktop/mobile screenshots; request a read-only review.

No unrelated configuration changes, dependency installation, automatic commits or live AI requests.

Review identified an optional AI-capability request delaying deterministic facts. A regression reproduced the delay; loading was decoupled and the regression passed. Completed generation alone populates cache; stopped/partial output is not cached. Cache identity now uses user/task/answer revision, including practice changes. AI prompt wording explicitly requests a task review with concrete next steps and no invented weaknesses.

Verification: 13 mocked browser regressions, 9 frontend unit tests and production build passed; desktop/mobile screenshots and 320–1440 px responsive checks passed. Backend full run exercised 910 tests and exposed two assertions tied to the old “学习报告” prompt wording; those assertions were updated to verify the approved task-review wording and exact original-answer counts. The targeted rerun passed all 30 tests covering those assertions, report-input contracts, student results and task lifecycle/aggregate behavior. No real AI request was made. The running backend is an older jar and needs a normal service restart/rebuild to expose the new endpoints; it was left running to preserve its runtime data.
