# M2 learning workflow and agent evaluation

Status: evaluation harness ready; live workflow/agent comparison is pending isolated database fixtures, provider verification, and manual review. No model quality score is reported here.

## Deterministic checks

- Python verification contracts: 16 tests passed on 2026-10-23.
- Backend unit suite: 628 tests passed on 2026-10-23.
- Agent evaluation scorer keeps partial, failed, cancelled, unmapped, and unreviewed rows visible. A partial plan cannot count as full success.
- Usage accounting retains a pricing snapshot version and leaves missing usage/cost null.

## Not measured yet

The 20 `agent-tasks.jsonl` cases still have no isolated `questionId`/`recordId` fixtures. The Docker/Colima connection dropped before a fresh MySQL V16 migration could be run. The target provider's tool-call usage and current approved price sheet have not been verified. No paid requests or teacher reviews were performed. The evaluator must not be used to claim that dynamic tool selection outperforms the fixed workflow.

When the isolated fixture and provider are ready, run both modes against the same task IDs with a bounded request count and cost estimate, then write immutable JSONL outputs under `.local/ai-evaluation/`. Record code SHA, corpus version, mode, provider/model, price version, actual usage when available, status, tools, duration, and reviewer status. Do not tune on holdout cases.
