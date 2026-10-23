# AI learning assistant run accounting

Each learning-plan preview is recorded with a UUID run ID, owner, assignment, execution mode, terminal status, duration, and tool-stage durations. The detail endpoint checks the authenticated student owner and returns only this operational metadata; prompts, answers, tool arguments, tool results, and generated plan text are not copied into the run ledger.

Token usage is additive across every model response in a run. When the provider returns both input and output token counts, `usage_source` is `REPORTED`. Missing or partial usage stays `UNKNOWN`; token counts and cost are not fabricated as zero. Cost is left null unless an explicit model pricing snapshot is supplied to the accounting service. When pricing is supplied, the run stores currency and pricing version with the calculated amount so later pricing changes do not silently rewrite history. Workflow mode makes no model calls, so its model call count is zero and its usage source remains `UNKNOWN`.

Micrometer metrics use only bounded stage, mode, and outcome tags. They deliberately omit user IDs, run IDs, assignment IDs, prompts, and model response content. Run database records provide per-run diagnosis without creating high-cardinality metrics.

At this stage, provider-reported token usage is recorded. Character-based estimates and a provider/model pricing catalog remain disabled until the target provider's usage semantics and approved price sheet are verified. This keeps the reported cost honest while preserving the schema for a versioned price snapshot.
