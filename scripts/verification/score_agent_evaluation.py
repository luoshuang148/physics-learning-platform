#!/usr/bin/env python3
"""Join human reviews to bounded learning-agent runs without treating partial plans as full success."""
import argparse
import json
from pathlib import Path

TERMINAL = {"success", "partial", "failed", "cancelled", "budget_stopped", "unmapped"}

def score_case(run, review):
    phase = run.get("phase", "unknown")
    expected = run.get("expectedOutcome")
    reviewed = bool(review and review.get("reviewer") and review.get("decision") in ("pass", "fail"))
    valid = phase in TERMINAL and run.get("toolValidity") is not False
    full_success = phase == "success" and expected == "complete" and valid
    task_success = None
    if reviewed:
        task_success = review["decision"] == "pass" and valid and phase not in ("failed", "cancelled", "budget_stopped", "unmapped")
    return {
        "caseId": run["caseId"], "mode": run.get("mode", "unknown"), "phase": phase,
        "expectedOutcome": expected, "fullSuccess": full_success,
        "taskSuccess": task_success, "toolValidity": valid,
        "steps": run.get("steps", []), "durationMs": run.get("durationMs"),
        "estimatedCost": run.get("estimatedCost"), "actualUsage": run.get("actualUsage"),
        "priceVersion": run.get("priceVersion"), "reviewer": review.get("reviewer") if reviewed else None,
        "reviewStatus": "reviewed" if reviewed else "pending",
    }

def score(runs, reviews):
    return [score_case(run, reviews.get(run["caseId"])) for run in runs]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs",type=Path,required=True);parser.add_argument("--reviews",type=Path,required=True);parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    runs=[json.loads(line) for line in args.runs.read_text(encoding="utf-8").splitlines() if line.strip()]
    reviews={row["caseId"]:row for row in (json.loads(line) for line in args.reviews.read_text(encoding="utf-8").splitlines() if line.strip())}
    with args.output.open("x",encoding="utf-8") as output:
        for row in score(runs,reviews): output.write(json.dumps(row,ensure_ascii=False)+"\n")

if __name__ == "__main__": main()
