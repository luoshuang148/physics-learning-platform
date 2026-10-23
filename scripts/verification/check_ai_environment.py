#!/usr/bin/env python3
"""AI 学习助手环境基线检查（只读）。

检查 DB / Redis / Qdrant 连通性、已配置开关、Java 版本与索引状态。
不输出密钥，不自动调用付费模型。

「已配置」和「实际模型调用成功」是两个状态：
  - 已配置：环境变量 / YAML 占位已就绪
  - 已验证：最近一次真实调用成功（本脚本只读不调用，故模型侧一律标「未验证」）
"""
from __future__ import annotations

import json
import os
import re
import shutil
import socket
import subprocess
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


@dataclass
class CheckResult:
    name: str
    configured: bool
    verified: bool
    detail: str = ""
    level: str = "info"  # info | warn | error


@dataclass
class Report:
    results: list[CheckResult] = field(default_factory=list)

    def add(self, r: CheckResult) -> None:
        self.results.append(r)

    def to_dict(self) -> dict:
        return {
            "checks": [r.__dict__ for r in self.results],
            "summary": {
                "configured": sum(1 for r in self.results if r.configured),
                "verified": sum(1 for r in self.results if r.verified),
                "warn": sum(1 for r in self.results if r.level == "warn"),
                "error": sum(1 for r in self.results if r.level == "error"),
            },
        }


def _tcp_reachable(host: str, port: int, timeout: float = 1.5) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def _http_reachable(url: str, timeout: float = 2.0) -> tuple[bool, str]:
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return True, f"HTTP {resp.status}"
    except urllib.error.HTTPError as e:
        # 服务在，只是路径/鉴权不同 —— 仍算连通
        return True, f"HTTP {e.code}"
    except Exception as e:  # noqa: BLE001 — 只读检查，吞掉并报告
        return False, str(e)[:120]


def _load_env_file(path: Path) -> dict[str, str]:
    """解析 .env 形式（KEY=VALUE），不覆盖已有真实环境变量。"""
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, val = line.partition("=")
        values[key.strip()] = val.strip().strip('"').strip("'")
    return values


def _get(env: dict[str, str], key: str, default: str = "") -> str:
    return os.environ.get(key) or env.get(key) or default


def check_java() -> CheckResult:
    java = shutil.which("java")
    if not java:
        return CheckResult("Java", False, False, "未找到 java 可执行文件", "error")
    try:
        out = subprocess.check_output(
            [java, "-version"], stderr=subprocess.STDOUT, text=True, timeout=10
        )
        first = out.splitlines()[0] if out else ""
        m = re.search(r'version "(\d+)', first)
        major = int(m.group(1)) if m else 0
        ok = major >= 21
        return CheckResult(
            "Java 21+",
            True,
            ok,
            first,
            "info" if ok else "error",
        )
    except Exception as e:  # noqa: BLE001
        return CheckResult("Java 21+", False, False, str(e)[:120], "error")


def check_mysql(env: dict[str, str]) -> CheckResult:
    host = _get(env, "DB_HOST", "127.0.0.1")
    port = int(_get(env, "DB_PORT", "3306"))
    name = _get(env, "DB_NAME", "zhixuewuli")
    ok = _tcp_reachable(host, port)
    return CheckResult(
        "MySQL",
        True,
        ok,
        f"{host}:{port}/{name} — " + ("TCP 可达（未执行 SQL）" if ok else "TCP 不可达"),
        "info" if ok else "warn",
    )


def check_redis(env: dict[str, str]) -> CheckResult:
    host = _get(env, "REDIS_HOST", "127.0.0.1")
    port = int(_get(env, "REDIS_PORT", "6379"))
    ok = _tcp_reachable(host, port)
    return CheckResult(
        "Redis",
        True,
        ok,
        f"{host}:{port} — " + ("TCP 可达" if ok else "TCP 不可达"),
        "info" if ok else "warn",
    )


def check_qdrant(env: dict[str, str]) -> CheckResult:
    url = _get(env, "APP_KNOWLEDGE_QDRANT_URL", "http://localhost:6333").rstrip("/")
    ok, detail = _http_reachable(url + "/readyz")
    return CheckResult(
        "Qdrant",
        True,
        ok,
        f"{url} — {detail}",
        "info" if ok else "warn",
    )


def check_switches(env: dict[str, str]) -> list[CheckResult]:
    specs = [
        ("AI_ENABLED (ai.context.enabled)", "AI_ENABLED", "false"),
        ("APP_KNOWLEDGE_ENABLED (app.knowledge.enabled)", "APP_KNOWLEDGE_ENABLED", "false"),
        ("AI_AGENT_TOOL_CALLING_ENABLED", "AI_AGENT_TOOL_CALLING_ENABLED", "false"),
        ("AI_LEARNING_ASSISTANT_MODE", "AI_LEARNING_ASSISTANT_MODE", "WORKFLOW"),
        ("REPORT_BATCH_ENABLED", "REPORT_BATCH_ENABLED", "true"),
    ]
    out = []
    for label, key, default in specs:
        raw = _get(env, key, default)
        out.append(
            CheckResult(
                label,
                raw not in ("", "false", "False"),
                False,  # 开关状态本身不是「调用成功」
                f"值={raw}（已配置；实际调用状态未验证）",
                "info",
            )
        )
    return out


def check_agent_credentials(env: dict[str, str]) -> CheckResult:
    """Agent 连接信息是否齐备；只判断是否填写，不读取、不打印密钥。"""
    base = _get(env, "AI_AGENT_BASE_URL")
    model = _get(env, "AI_AGENT_MODEL")
    key = _get(env, "AI_AGENT_API_KEY") or os.environ.get("AI_AGENT_API_KEY", "")
    key_present = bool(key.strip())
    configured = bool(base.strip()) and bool(model.strip()) and key_present
    detail_parts = [
        f"base-url={'有' if base.strip() else '空'}",
        f"model={'有' if model.strip() else '空'}",
        f"api-key={'已设置' if key_present else '空'}",
    ]
    return CheckResult(
        "Agent 连接信息",
        configured,
        False,
        "；".join(detail_parts) + "（已配置 ≠ 调用成功）",
        "info" if configured else "warn",
    )


def check_model_verification() -> CheckResult:
    return CheckResult(
        "真实模型调用",
        False,
        False,
        "本脚本只读，不发起付费模型调用 —— 永远显示「未验证」。"
        "请在有限预算的冒烟测试后，把结果记入 docs/ai-evaluation/v2-results.md",
        "info",
    )


def check_index_status() -> CheckResult:
    return CheckResult(
        "题库索引状态",
        False,
        False,
        "需服务启动后查询 GET /questions/teacher/{id}/index-status 或 "
        "question_index_jobs 表；本脚本不连接业务库",
        "info",
    )


def main() -> int:
    # 示例文件提供默认值，真实 .env 覆盖
    env = _load_env_file(REPO_ROOT / ".env.dev.example")
    env.update(_load_env_file(REPO_ROOT / ".env"))

    report = Report()
    report.add(check_java())
    report.add(check_mysql(env))
    report.add(check_redis(env))
    report.add(check_qdrant(env))
    for r in check_switches(env):
        report.add(r)
    report.add(check_agent_credentials(env))
    report.add(check_model_verification())
    report.add(check_index_status())

    as_json = "--json" in sys.argv
    if as_json:
        print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))
    else:
        print("AI 学习助手环境基线检查（只读，不调用付费模型）")
        print("=" * 60)
        width = max(len(r.name) for r in report.results)
        for r in report.results:
            state = "已配置" if r.configured else "未配置"
            verified = "已验证" if r.verified else "未验证"
            mark = {"info": "·", "warn": "!", "error": "x"}[r.level]
            print(f"[{mark}] {r.name:<{width}}  {state:6}  {verified:6}  {r.detail}")
        s = report.to_dict()["summary"]
        print("-" * 60)
        print(
            f"汇总：已配置 {s['configured']} 项 / 已验证 {s['verified']} 项 / "
            f"警告 {s['warn']} / 错误 {s['error']}"
        )
        print("提示：「已配置」≠「实际调用成功」；模型与向量效果须以 v2-results.md 实测为准。")

    # 仅当存在 error 级失败时返回非 0
    return 1 if any(r.level == "error" for r in report.results) else 0


if __name__ == "__main__":
    sys.exit(main())
