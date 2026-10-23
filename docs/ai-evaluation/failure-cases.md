# Failure replay register

This register gives deterministic replay conditions and expected handling. It does not claim live provider outcomes. Attach run IDs and observed results when isolated fixtures are available.

| Failure class | Trigger | Expected handling | Current evidence / remaining check |
|---|---|---|---|
| Formula or extraction damage | A scanned formula loses a sign, exponent, or symbol during ingestion | Mark the source unusable or low confidence; do not answer from a damaged equation | Extraction contract tests exist; requires a teacher-reviewed scan fixture |
| Retrieval miss | Ask about a supported concept absent from top retrieved passages | Return insufficient evidence or ask a narrower question; preserve actual source IDs | Retrieval unit tests exist; development Recall@5 comparison remains pending |
| Unsupported citation | Output includes a source marker absent from retrieved sources | Fail citation validation and keep the case in the denominator | Scorer rejects unknown markers; run a live generated answer after fixture setup |
| Tool misuse | Model requests an unknown tool, fabricated question ID, or out-of-scope assignment | Reject at the tool/plan validation boundary and record a failed step without storing arguments | Tool authorization and plan validator tests pass; no paid agent replay yet |
| Expired source data | Assignment answers or knowledge revision change after preview | Reject stale plan activation and require a new preview | Plan lifecycle revision tests pass; UI recovery should be exercised against MySQL |
| Provider unavailable or timeout | Tool-call endpoint times out, returns malformed JSON, or is unavailable | Bound retries/rounds, keep status failed/cancelled, and never report zero cost as known | Adapter wire contract tests pass; target provider smoke is pending |

For every replay, store the trigger, observable status, expected and actual result, code/corpus version, and whether a human checked the physics. Keep prompt text, student answers, and tool arguments out of operational logs.
