#!/usr/bin/env python3
"""启用平台题库 AI 补练并触发索引（演示/评测种子数据）。

- 选择已有文字题且解析完整的题，显式开启 ai_practice_enabled
- 幂等：已启用的题跳过
- 输出 fixtureKey→questionId 映射与新增/复用/失败计数，不输出 token

用法：
  python3 scripts/seed_platform_rag.py --base-url http://127.0.0.1:8080/api/v1 \
      --manifest scripts/fixtures/platform-rag/questions.jsonl
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]


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
    data = body.get("data") or {}
    return data.get("token") or data.get("accessToken")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080/api/v1")
    parser.add_argument("--manifest", default="scripts/fixtures/platform-rag/questions.jsonl")
    parser.add_argument("--username", default=os.environ.get("SEED_TEACHER_USERNAME", "teacher"))
    parser.add_argument("--password", default=os.environ.get("SEED_TEACHER_PASSWORD", "Teacher@2026"))
    args = parser.parse_args()

    token = login(args.base_url, args.username, args.password)
    if not token:
        print("登录失败：检查 SEED_TEACHER_USERNAME/SEED_TEACHER_PASSWORD 与服务地址", file=sys.stderr)
        return 1

    manifest_path = REPO_ROOT / args.manifest
    if not manifest_path.is_file():
        print(f"manifest 不存在: {manifest_path}", file=sys.stderr)
        return 1

    created = reused = failed = 0
    mapping: dict[str, int] = {}

    for line_no, line in enumerate(manifest_path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        try:
            fixture = json.loads(line)
        except json.JSONDecodeError:
            failed += 1
            print(f"L{line_no}: JSON 无效，跳过", file=sys.stderr)
            continue
        fixture_key = fixture.get("fixtureKey") or f"L{line_no}"
        body = {
            "content": fixture.get("content", ""),
            "options": json.dumps(fixture.get("options", {}), ensure_ascii=False),
            "correctOption": fixture.get("correctOption", "A"),
            "explanation": fixture.get("explanation", ""),
            "difficulty": int(fixture.get("difficulty", 2)),
            "knowledgePointCodes": fixture.get("knowledgePointCodes", []),
            "aiPracticeEnabled": True,
        }
        if not body["content"].strip() or not body["explanation"].strip():
            failed += 1
            print(f"{fixture_key}: 题干或解析为空，拒绝启用 AI 补练", file=sys.stderr)
            continue

        status, resp = req("POST", "/questions/teacher/create", token=token, body=body, base_url=args.base_url)
        if status in (200, 201):
            question_id = (resp.get("data") or {}).get("id")
            if question_id:
                mapping[fixture_key] = question_id
                created += 1
                continue
        # 内容重复 → 尝试查找并更新
        from urllib.parse import quote
        search = quote(body["content"][:12])
        status, resp = req("GET", f"/questions/teacher/page?page=1&size=5&search={search}",
                           token=token, base_url=args.base_url)
        rows = ((resp.get("data") or {}).get("records") or (resp.get("data") or {}).get("list") or [])
        if rows:
            question_id = rows[0].get("id")
            update = dict(body)
            update["id"] = question_id
            status, resp = req("PUT", "/questions/teacher/update", token=token, body=update, base_url=args.base_url)
            if status == 200:
                mapping[fixture_key] = question_id
                reused += 1
                continue
        failed += 1
        print(f"{fixture_key}: 导入失败 status={status} msg={resp.get('message')}", file=sys.stderr)

    out_path = REPO_ROOT / "scripts/fixtures/platform-rag/fixture-map.json"
    existing: dict[str, int] = {}
    if out_path.is_file():
        try:
            existing = {k: int(v) for k, v in json.loads(out_path.read_text(encoding="utf-8")).items()}
        except Exception:
            existing = {}
    existing.update({k: int(v) for k, v in mapping.items()})
    out_path.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({
        "created": created,
        "reused": reused,
        "failed": failed,
        "fixtureToQuestionId": mapping,
        "fixtureMapPath": str(out_path),
    }, ensure_ascii=False, indent=2))
    return 0 if failed == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
