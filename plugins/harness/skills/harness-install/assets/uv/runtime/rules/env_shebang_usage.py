#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require env shebang under rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from .utils import first_line, is_executable, relative, safe_walk, severity_for

class EnvShebangUsageRule(HarnessCheckRule):
    """Validate envShebangUsage check."""

    category = "envShebangUsage"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate envShebangUsage check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        directories = params.get("directories", [])
        if not isinstance(directories, list):
            return []
        expected_prefix = params.get("expectedPrefix", "#!/usr/bin/env ")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "executable script should use /usr/bin/env shebang: {file}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(file=relative(path)),
            )
            for directory in directories
            if isinstance(directory, str)
            for path in safe_walk(root / directory)
            if path.is_file()
            and is_executable(path)
            and (line := first_line(path)).startswith("#!")
            and not line.startswith(expected_prefix)
        ]


RULE: HarnessCheckRule = EnvShebangUsageRule()
