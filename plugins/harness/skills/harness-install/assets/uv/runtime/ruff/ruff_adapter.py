# -*- coding: utf-8 -*-
"""
Adapter that runs ruff over harness Python sources and maps built-in rule
codes to harness findings. Single-owner partition: only codes in
RUFF_CODE_TO_CATEGORY are surfaced; all other ruff output is ignored.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

from core.rule_context import RuleContext, relative
from harness_check_rule import Finding, FindingFix, FixSafety
from ruff.ruff_code_map import RUFF_CODE_TO_CATEGORY

RUFF_SPEC = "ruff@0.15.15"


def run_ruff(ctx: RuleContext) -> list[Finding]:
    """
    Run ruff over the wildcardImport source set and return mapped findings.

    :param ctx: Rule execution context.
    :returns: Findings for ruff codes present in RUFF_CODE_TO_CATEGORY.
    """
    sources = ctx.stack_sources("wildcardImport")
    if not sources:
        return []
    config_path = Path(__file__).resolve().parent / "ruff.toml"
    command = [
        "uvx",
        RUFF_SPEC,
        "check",
        "--config",
        str(config_path),
        "--output-format",
        "json",
        *[str(path) for path in sources],
    ]
    result = subprocess.run(command, cwd=ctx.root, capture_output=True, text=True)
    if result.returncode == 127 or "command not found" in result.stderr:
        print("[ruff] uvx not provisioned; skipping lint", file=sys.stderr)
        return []
    if result.returncode == 2:
        print(f"[ruff] config error:\n{result.stderr}", file=sys.stderr)
        sys.exit(1)
    diagnostics = json.loads(result.stdout) if result.stdout.strip() else []
    findings: list[Finding] = []
    for diagnostic in diagnostics:
        category = RUFF_CODE_TO_CATEGORY.get(diagnostic.get("code", ""))
        if category is None or not ctx.is_enabled(category):
            continue
        location = diagnostic.get("location", {})
        end_location = diagnostic.get("end_location", {})
        message = diagnostic.get("message", "")
        findings.append(
            Finding(
                ctx.severity_of(category),
                category,
                message,
                file=relative(Path(diagnostic.get("filename", "")).resolve(), ctx.root),
                start_line=location.get("row"),
                start_column=location.get("column"),
                end_line=end_location.get("row"),
                end_column=end_location.get("column"),
                fix=FindingFix(
                    description=message,
                    safety=FixSafety.UNSAFE,
                    edits=(),
                ),
            )
        )
    return findings
