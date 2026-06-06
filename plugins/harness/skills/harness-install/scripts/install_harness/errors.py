from __future__ import annotations

from pathlib import Path
import sys
from typing import NoReturn


def read_text(path: str) -> str:

    return Path(path).read_text(encoding="utf-8")


def fail(message: str, exit_code: int = 1) -> NoReturn:

    print(f"[error] {message}", file=sys.stderr)
    raise SystemExit(exit_code)
