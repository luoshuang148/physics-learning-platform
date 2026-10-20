# Independent final review and one fix pass

Reviewer: fresh gpt-6-astra agent, read-only, base e32fefa through cd71569. Default suite1048, frontend7/build, local MySQL fresh/upgraded3 each, browser lifecycle and backup checksum passed before review. Initial branch CI39099579014 passed all four jobs, including production Docker/MySQL8.4 delivery.

Accepted Important findings (fixed in one regression-driven pass):
1. Stale User entity login save can restore pre-logout tokenVersion; use conditional lastLogin update, fail zero updated rows, prevent unrelated user saves overwriting security fields.
2. Practice and assignment share cache and cached doneMap overrides server truth; scope owner/event/intent and only recover drafts.
3. Lost successful response loses the frozen submission payload with same requestId; persist the complete payload before dispatch and reconcile server success.
4. Concurrent report find-then-insert permits duplicate scope rows; preserve generations safely and tolerate existing duplicates.
5. New UTC LocalDateTime lacks offset, displays early in Shanghai; offset-bearing wire format with explicit legacy-time handling.
6. Duplicate old QIDS causes V3 partial migration; deterministic dedup preserves first position and raw description, test missing/malformed data.
7. AI report limits apply after full image-bearing load; reject oversized scope before entity materialization, bounded text-only projection and explicit prompt byte budget across all report paths.
8. Wrong book merges identical content across task scopes; preserve event/question identity.
9. Teacher single-question analysis uses Aliyun but client gates Zhipu; explicit operation-to-provider capabilities.

Re-grade: reviewer P3 latest unscoped report after regeneration is Important because the teacher sees an obsolete generated report; fix together with append-only report generations and deterministic newest selection, not deferred.

Additional acceptance regression: inject database failure after old-task archive flush during replacement and confirm full transaction rollback. Invalid preparation alone did not exercise that phase.

Declined-to-judge rulings: no live model accuracy/injection certification without authorized evaluation; remote Docker/MySQL8.4 outcome will be checked by author; local synthetic throughput is not production capacity; distributed/multi-classroom expansion is outside approved scope; missing legacy originals cannot be reconstructed and remain explicitly marked; sanitizer paths tested but not a comprehensive security certification; unrelated cosmetic/dead-helper cleanup is not a blocker. These do not waive any identified functional or security finding.

## Verification of the fix pass

- 1: `AuthRevocationConcurrencyTest` observed stale login restore 1→0; unrelated profile path also failed with DynamicUpdate removed. Conditional version-aware lastLogin update rejects stale authentication, and dynamic dirty writes protect unchanged security fields. Both regressions now pass.
- 2–3: `submission-recovery.spec.ts` observed a missing frozen payload and practice overwriting original wrong state. Full persisted payload, scoped drafts and server reconciliation now pass both browser regressions.
- 4: `ReportGenerationConcurrencyTest` reproduced duplicate-scope lookup failure; immutable generation rows pass concurrent saves and third regeneration without overwriting evidence.
- 5: `LearningTimestampContractTest` first failed missing UTC offset; now UTC has Z and legacy local values remain explicit. Fresh/V3 schemas and projections carry the distinction.
- 6: real isolated MySQL upgrade first failed duplicate-key 1062 with duplicate QIDS. The same duplicate/missing/malformed fixture now upgrades; raw descriptions and attempts remain intact.
- 7: `BoundedReportInputTest` first failed early rejection and text projection contracts. Four initial tests passed: pre-load row rejection, image-heavy snapshots without entity loads, per-field and total byte budgets. Real MySQL contract also reads the text projection successfully.
- 8–9: Node product boundary tests failed content-based deduplication and wrong provider gating, then passed with task identity retained and explicit operation capability mapping.
- 10: repository integration test first selected stale createdAt over newer updatedAt, then passed with deterministic updatedAt/id ordering. Existing duplicates are tolerated.
- Additional acceptance: `TaskReplacementRollbackTest` injects failure after the old task archive flush and verifies the original task remains active; passed.

Follow-up to the same immutable-input finding: a new regression demonstrated both report and teacher-list projections filling a null original explanation from an edited bank. Both projections now choose the saved version as a whole, preserving null values; the same boundary is included in the real MySQL contract.

Final default backend command `./mvnw -B test`: **1060/1060** passed after the null-snapshot regression failed then passed. Frontend `npm test`: **9/9** passed; `npm run build` passed. Isolated MySQL8.0.46 fresh and V1→V2→V3 service contracts: **3/3 each**, including the bounded report text projection. Final browser 3/3 passed against the newly packaged production-profile Jar and final frontend. Final HTTP reads 400/400 and nine-table backup/restore checksums passed. Remote Docker/MySQL8.4 delivery is the mandatory four-job GitHub Actions gate for the final commit; its result is checked after push and reported with the exact commit.

Final: Ruling: report generations are append-only rather than protected by a new scope uniqueness constraint — preserves concurrent results and existing duplicates, and latest reads are deterministic — cost if wrong: storage growth requiring an explicit retention policy.
Final: Ruling: unscoped teacher reports select the current active task rather than mixed latest sessions — matches the dashboard task semantics and avoids blending assignments — cost if wrong: cross-task comparisons need a separate explicit report feature.
Final: Ruling: new times are UTC with offsets; legacy times retain their unknown local interpretation — avoids silently shifting historical data — cost if wrong: old records may need a separately verified timezone repair.
Final: Ruling: large or incomplete report inputs reject explicitly instead of silently truncating historical evidence or loading images — keeps conclusions auditable and bounded — cost if wrong: teachers must select a smaller scope or repair missing text snapshots.
Final: Ruling: real model accuracy/prompt-injection resistance remain unmeasured without authorized model evaluation; deterministic safety/data contracts pass — cost if wrong: generated educational guidance can still be incorrect and requires teacher review.
Final: Ruling: remote production image/MySQL8.4 behavior is judged by the actual CI delivery job rather than inferred from local Jar execution — cost if wrong: release must wait for a failed job to be repaired.
Final: Ruling: local synthetic benchmark measures only two HTTP reads; it is not production capacity — cost if wrong: production sizing requires a representative workload.
Final: Ruling: distributed/multi-classroom behavior remains outside the authorized single-classroom/single-JVM model — cost if wrong: horizontal scaling or classroom isolation requires a new design.
Final: Ruling: missing historical originals remain MIGRATED/MISSING or explicit legacy fallback, never fabricated — cost if wrong: original historical content cannot be recovered from this database.
Final: Ruling: sanitizer regression coverage supports the tested render paths, not a comprehensive security certification — cost if wrong: untested browser payloads still warrant security review.
Final: Ruling: unrelated cosmetic/dead-helper cleanup is not a release blocker — cost if wrong: remaining maintenance clutter, with no accepted functional finding deferred.
Final: minor (deferred): none. Latest-report finding was regraded Important and fixed.
