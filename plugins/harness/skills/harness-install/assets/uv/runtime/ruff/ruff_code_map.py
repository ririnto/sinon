# -*- coding: utf-8 -*-
"""
Mapping from ruff built-in rule codes to harness manifest categories.

Single-owner partition: every code listed here is detected by ruff and the
matching libcst rule module is removed. Keep this map to the codes actually
migrated; do not add codes for rules that remain in libcst.
"""

from __future__ import annotations

RUFF_CODE_TO_CATEGORY: dict[str, str] = {
    "F403": "wildcardImport",
}

RUFF_FIX_SAFETY: dict[str, str] = {
    "wildcardImport": "unsafe",
}

RUFF_CATEGORIES: tuple[str, ...] = ("wildcardImport",)
