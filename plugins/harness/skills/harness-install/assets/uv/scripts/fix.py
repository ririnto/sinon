#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.13"
# dependencies = []
# ///
"""
Fix runner using native ruff.
"""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import shutil
import subprocess
import sys
from typing import Final, Protocol, Sequence, runtime_checkable


RUFF_SPEC: Final = "ruff>=0.15.21,<0.16.0"


@runtime_checkable
class ReconfigurableTextStream(Protocol):
    """
    Text stream that can reset its runtime encoding.
    """

    def reconfigure(self, *, encoding: str) -> None:
        """
        Reset the stream encoding.
        """


def configure_utf8_streams() -> None:
    """
    Use UTF-8 for console output on runtimes that support reconfiguration.
    """
    for stream in (sys.stdout, sys.stderr):
        if isinstance(stream, ReconfigurableTextStream):
            stream.reconfigure(encoding="utf-8")


def run_markdownlint_fix() -> int:
    """
    Run markdownlint-cli2 --fix when it is available on PATH.
    """
    markdownlint = shutil.which("markdownlint-cli2")
    if markdownlint is None:
        print(
            "warning: markdownlint-cli2 not in PATH; skipping Markdown fixes",
            file=sys.stderr,
        )
        return 0
    return subprocess.run([markdownlint, "--fix"]).returncode


def run_command(command: Sequence[str]) -> int:
    """
    Run one subprocess command and return its exit status.
    """
    return subprocess.run(command).returncode


def run_ruff_fixes() -> int:
    """
    Run ruff lint fixes and format fixes with Ruff's project discovery.
    """
    commands = (
        ("check", "--fix", "."),
        ("format", "."),
    )
    status = 0
    for command in commands:
        result = run_command(
            [
                "uv",
                "run",
                "--with",
                RUFF_SPEC,
                "ruff",
                *command,
            ],
        )
        if result != 0:
            status = result
    return status


def main() -> int:
    """
    Run Markdown fixes and the Ruff fix pipeline in parallel.
    """
    with ThreadPoolExecutor(max_workers=2) as executor:
        markdown_future = executor.submit(run_markdownlint_fix)
        ruff_future = executor.submit(run_ruff_fixes)
        markdown_status = markdown_future.result()
        ruff_status = ruff_future.result()
    if markdown_status != 0:
        return markdown_status
    return ruff_status


if __name__ == "__main__":
    configure_utf8_streams()
    raise SystemExit(main())
