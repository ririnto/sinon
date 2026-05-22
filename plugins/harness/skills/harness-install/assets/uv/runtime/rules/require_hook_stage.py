#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require hook stage rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

STACK = "uv"

class RequireHookStageRule(HarnessCheckRule):
    """Validate requireHookStage check."""

    category = "requireHookStage"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireHookStage check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        marker_template = params.get("markerTemplate", "# Harness stage: {stage}")
        stages = params.get("stages", {})
        if not isinstance(stages, dict):
            return []
        stack_stages = stages.get(STACK, {})
        if not isinstance(stack_stages, dict):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "{hook} must contain stage marker '# Harness stage: {expectedStage}'")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(hook=hook_name, expectedStage=expected_stage),
            )
            for hook_name, expected_stage in stack_stages.items()
            if isinstance(hook_name, str)
            and isinstance(expected_stage, str)
            and is_safe_file(root / f"docs/harness/git-hooks/{hook_name}")
            and marker_template.format(stage=expected_stage) not in read_text(root / f"docs/harness/git-hooks/{hook_name}")
        ]


RULE: HarnessCheckRule = RequireHookStageRule()
