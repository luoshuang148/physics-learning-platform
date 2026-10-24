#!/usr/bin/env python3
"""平台题库检索评测（corpus-v2）。

对 development/holdout 调用 POST /ai/student/platform-search，计算：
- Hit@5（题目级去重，只统计有相关题的分母）
- 无结果处理：expectNoResult 样例不得返回任何题号
- 引用有效性：返回 sourceId 必须来自本次结果

用法：
  python3 scripts/verification/evaluate_platform_v2.py \
      --base-url http://127.0.0.1:8080/api/v1 \
      --split development \
      --username stu01 --password Student@2026 \
      --out docs/ai-evaluation/v2-results.md
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
FIXTURE_INDEX: dict[int, str] = {}  # questionId -> fixtureKey


def load_fixture_index() -> dict[int, str]:
    mapping: dict[int, str] = {}
    mapping_file = REPO_ROOT / "scripts/fixtures/platform-rag/fixture-map.json"
    if mapping_file.is_file():
        data = json.loads(mapping_file.read_text(encoding="utf-8"))
        for key, qid in data.items():
            mapping[int(qid)] = key
    return mapping


def req(method: str, path: str, token: str | None = None, body=None, base_url: str = ""):
    url = base_url.rstrip("/") + path
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(request, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode() or "{}")
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.loads(error.read().decode() or "{}")
        except Exception:
            return error.code, {}
    except Exception as error:  # noqa: BLE001
        return 0, {"message": str(error)}


def login(base_url: str, username: str, password: str) -> str | None:
    status, body = req("POST", "/auth/login", base_url=base_url,
                       body={"username": username, "password": password})
    if status != 200:
        return None
    return (body.get("data") or {}).get("token")


def evaluate(split: str, base_url: str, token: str, fixture_map: dict[int, str]) -> dict:
    corpus_path = REPO_ROOT / f"docs/ai-evaluation/corpus-v2/{split}.jsonl"
    cases = [json.loads(line) for line in corpus_path.read_text(encoding="utf-8").splitlines()
             if line.strip()]

    hits = 0
    with_expected = 0
    no_result_ok = 0
    no_result_total = 0
    citation_valid = 0
    citation_total = 0
    failures = []
    latencies = []

    for case in cases:
        body = {"query": case["question"], "limit": 5, "excludeAttempted": False}
        start = time.time()
        status, resp = req("POST", "/ai/student/platform-search", token=token, body=body,
                           base_url=base_url)
        elapsed = int((time.time() - start) * 1000)
        latencies.append(elapsed)
        data = resp.get("data") or {}
        questions = data.get("questions") or []
        sources = data.get("sources") or []

        returned_fixtures = set()
        for q in questions:
            key = fixture_map.get(int(q.get("questionId", -1)))
            if key:
                returned_fixtures.add(key)
            # fallback: match by content prefix against expected is too loose; use id map

        # citation validity: sourceIds must appear in sources
        source_ids = {s.get("sourceId") for s in sources}
        case_citations_ok = True
        for q in questions:
            for sid in q.get("sourceIds") or []:
                citation_total += 1
                if sid in source_ids or any(sid == s.get("sourceId") for s in sources):
                    citation_valid += 1
                else:
                    case_citations_ok = False

        expected = set(case.get("expectedFixtureKeys") or [])
        if case.get("expectNoResult") or not expected:
            no_result_total += 1
            if not questions:
                no_result_ok += 1
            else:
                failures.append({
                    "caseId": case["id"],
                    "reason": "无相关题却返回题目",
                    "returned": [q.get("questionId") for q in questions],
                })
            continue

        with_expected += 1
        if returned_fixtures & expected:
            hits += 1
        else:
            failures.append({
                "caseId": case["id"],
                "query": case["question"],
                "expected": sorted(expected),
                "returned": sorted(returned_fixtures),
                "status": data.get("status"),
                "actualMode": data.get("actualMode"),
            })

    latencies.sort()
    def pct(p):
        if not latencies:
            return 0
        return latencies[min(len(latencies) - 1, int(len(latencies) * p))]

    return {
        "split": split,
        "cases": len(cases),
        "hitAt5": hits / with_expected if with_expected else None,
        "hits": hits,
        "withExpected": with_expected,
        "noResultCorrect": no_result_ok,
        "noResultTotal": no_result_total,
        "citationValidRate": citation_valid / citation_total if citation_total else None,
        "citationValid": citation_valid,
        "citationTotal": citation_total,
        "latencyMs": {"p50": pct(0.5), "p95": pct(0.95), "n": len(latencies)},
        "failures": failures[:10],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api/v1")
    parser.add_argument("--split", default="development", choices=["development", "holdout", "both"])
    parser.add_argument("--username", default=os.environ.get("SEED_STUDENT_USERNAME", "stu01"))
    parser.add_argument("--password", default=os.environ.get("SEED_STUDENT_PASSWORD", "Student@2026"))
    parser.add_argument("--out", default="docs/ai-evaluation/v2-results.md")
    args = parser.parse_args()

    token = login(args.base_url, args.username, args.password)
    if not token:
        print("登录失败", file=sys.stderr)
        return 1

    fixture_map = load_fixture_index()
    if not fixture_map:
        print("警告：缺 scripts/fixtures/platform-rag/fixture-map.json，Hit 将无法按 fixture 匹配", file=sys.stderr)

    splits = ["development", "holdout"] if args.split == "both" else [args.split]
    results = [evaluate(s, args.base_url, token, fixture_map) for s in splits]

    out_path = REPO_ROOT / args.out
    lines = [
        "# 平台题库 RAG v2 评测结果",
        "",
        f"生成时间：{time.strftime('%Y-%m-%d %H:%M:%S')}",
        f"语料版本：platform-rag-v2",
        f"执行方式：HTTP 调用本地 API（`{args.base_url}`）",
        "",
        "## 诚实声明",
        "",
        "- 本表为**实测**数字，不是目标值；目标见 corpus-v2/manifest.json。",
        "- Hit@5 分母只含「存在相关题」的样例；空检索任务单独统计。",
        "- 引用有效性只校验来源 ID 集合归属，**不等同**语义支持正确。",
        "- 无物理专业复核时，评测为开发者核对。",
        "",
    ]
    for r in results:
        hit = "n/a" if r["hitAt5"] is None else f"{r['hitAt5']*100:.1f}%"
        cit = "n/a" if r["citationValidRate"] is None else f"{r['citationValidRate']*100:.1f}%"
        lines += [
            f"## split = {r['split']}",
            "",
            "| 指标 | 结果 |",
            "|---|---|",
            f"| 样例数 | {r['cases']} |",
            f"| Hit@5 | {hit}（{r['hits']}/{r['withExpected']}） |",
            f"| 无结果处理 | {r['noResultCorrect']}/{r['noResultTotal']} |",
            f"| 引用结构有效 | {cit}（{r['citationValid']}/{r['citationTotal']}） |",
            f"| 延迟 p50/p95 | {r['latencyMs']['p50']}ms / {r['latencyMs']['p95']}ms |",
            "",
        ]
        if r["failures"]:
            lines += ["失败样例（截断至 10 条）：", ""]
            for f in r["failures"]:
                lines.append(f"- `{f.get('caseId')}` {json.dumps(f, ensure_ascii=False)}")
            lines.append("")
    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps([{k: v for k, v in r.items() if k != 'failures'} for r in results],
                     ensure_ascii=False, indent=2))
    print(f"\n结果已写入 {out_path}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
