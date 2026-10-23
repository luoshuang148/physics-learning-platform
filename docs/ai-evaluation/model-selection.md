# Model and execution-mode selection

There is no winning model or execution mode yet because corpus-v1 has not received teacher reviews and the 20 workflow/agent cases are not mapped to isolated platform records. Do not infer quality from a successful HTTP response, tool count, or a polished demo.

The current engineering choice is to keep fixed workflow and dynamic tool-calling Agent as separate modes behind the same bounded tools and validator. The workflow is the default because its path is predictable and the Agent remains disabled by default until a target-provider smoke test confirms tool-call round trips, cancellation, reported usage, and an explicit cost ceiling.

Selection criteria for the next measured comparison:

- reviewed task success and tool validity, with partial completion reported separately;
- unsupported-citation rate and physics correctness reviewed by a subject-matter reviewer;
- p50/p95 run and stage latency, including failed and cancelled runs;
- input/output tokens and cost under a versioned provider price snapshot;
- authorization failures, stale-source handling, and tool-call limits.

For each candidate, freeze the same task list, corpus, prompt, and budget. Use development cases for up to two targeted changes; reserve holdout cases until the configuration is frozen. Keep a candidate only when it improves a reviewed quality metric without violating permissions, cost, or latency limits. Otherwise retain the fixed workflow.
