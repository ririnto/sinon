#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///

from __future__ import annotations

from collections.abc import Callable
import importlib
import sys
from typing import cast

main = cast(
    Callable[[list[str]], int], importlib.import_module("install_harness.cli").main
)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
