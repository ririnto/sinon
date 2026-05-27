#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Manifest payload with accessor methods.
"""

from __future__ import annotations

import sys


from typing import Protocol, cast

from .severity import Severity

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

HarnessManifest = dict[str, object]


class Manifest(Protocol):
    """Raw manifest payload with accessor methods."""

    raw: dict[str, object]

    def is_enabled(self, category: str) -> bool:
        """Check if category is enabled in manifest."""
        ...

    def severity_of(self, category: str) -> Severity:
        """Get severity level for category."""
        ...

    def string_array(self, category: str) -> list[str]:
        """Get string array from category."""
        ...

    def string_value(self, category: str) -> str:
        """Get string value from category."""
        ...

    def category_object(self, category: str) -> dict[str, object]:
        """Get category object from manifest."""
        ...


def as_record(value: object) -> dict[str, object]:
    """Convert value to dict or return empty dict."""
    if isinstance(value, dict):
        return value
    return {}


def category_object_from_manifest(
    raw_manifest: dict[str, object],
    category: str,
) -> dict[str, object]:
    """Extract category object from manifest."""
    category_value = raw_manifest.get(category)
    if isinstance(category_value, dict):
        return category_value
    return {}


def is_enabled_from_manifest(
    raw_manifest: dict[str, object],
    category: str,
) -> bool:
    """Check if category is enabled in manifest."""
    return category_object_from_manifest(raw_manifest, category).get("enabled", True) is not False


def severity_from_manifest(
    raw_manifest: dict[str, object],
    category: str,
) -> Severity:
    """Get severity for category from manifest."""
    severity = category_object_from_manifest(raw_manifest, category).get("severity")
    if severity in ("ERROR", "WARN", "INFO"):
        return cast(Severity, severity)
    return "ERROR"


def string_value_from_manifest(
    raw_manifest: dict[str, object],
    category: str,
) -> str:
    """Get string value for category from manifest."""
    value = raw_manifest.get(category)
    if isinstance(value, str):
        return value
    return ""


def string_array_from_manifest(
    raw_manifest: dict[str, object],
    category: str,
) -> list[str]:
    """Get string array for category from manifest."""
    value = raw_manifest.get(category)
    if isinstance(value, list):
        return [item for item in value if isinstance(item, str)]
    return list()


def create_manifest(raw_manifest: object) -> Manifest:
    """Factory function to create a Manifest from a raw manifest object."""

    class ManifestImpl:
        """Manifest implementation."""

        def __init__(self, data: dict[str, object]) -> None:
            """Initialize with raw manifest data."""
            self.raw = data

        def is_enabled(self, category: str) -> bool:
            """Check if category is enabled."""
            return is_enabled_from_manifest(self.raw, category)

        def severity_of(self, category: str) -> Severity:
            """Get severity for category."""
            return severity_from_manifest(self.raw, category)

        def string_array(self, category: str) -> list[str]:
            """Get string array from category."""
            return string_array_from_manifest(self.raw, category)

        def string_value(self, category: str) -> str:
            """Get string value from category."""
            return string_value_from_manifest(self.raw, category)

        def category_object(self, category: str) -> dict[str, object]:
            """Get category object."""
            return category_object_from_manifest(self.raw, category)

    return ManifestImpl(as_record(raw_manifest))
