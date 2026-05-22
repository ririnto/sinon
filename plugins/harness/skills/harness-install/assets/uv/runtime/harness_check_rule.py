#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = ["libcst>=1.8.6"]
# ///
"""ABC for harness check rules."""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections.abc import Iterable
from pathlib import Path
from typing import NamedTuple


class Finding(NamedTuple):
    """Represents a validation finding with severity, category, and message."""

    severity: str
    category: str
    message: str


class HarnessCheckRule(ABC):
    """Strategy ABC implemented by each validation rule."""

    @abstractmethod
    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""

    @abstractmethod
    def validate(self, project_dir: Path, manifest: dict) -> Iterable[Finding]:
        """Validate and return findings."""
