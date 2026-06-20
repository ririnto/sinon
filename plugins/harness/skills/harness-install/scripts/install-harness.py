#!/usr/bin/env -S uv run --script
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///

from __future__ import annotations

import sys
from typing import Protocol, runtime_checkable

from install_harness.cli import main


@runtime_checkable
class ReconfigurableTextStream(Protocol):
    """Text stream that can reset its runtime encoding."""

    def reconfigure(self, *, encoding: str) -> None:
        """Reset the stream encoding."""


def configure_utf8_streams() -> None:
    """Use UTF-8 for console output on runtimes that support reconfiguration."""

    for stream in (sys.stdout, sys.stderr):
        if isinstance(stream, ReconfigurableTextStream):
            stream.reconfigure(encoding="utf-8")


if __name__ == "__main__":
    configure_utf8_streams()
    raise SystemExit(main(sys.argv[1:]))
