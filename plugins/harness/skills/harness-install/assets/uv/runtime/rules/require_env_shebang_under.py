#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require env shebang under rule."""

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import first_line, is_executable, relative, safe_walk, severity_for


class RequireEnvShebangUnderRule(HarnessCheckRule):
    """Validate requireEnvShebangUnder check."""

    category = "requireEnvShebangUnder"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
        """Validate requireEnvShebangUnder check."""
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
        result = []
        for directory in directories:
            if not isinstance(directory, str):
                continue
            for path in safe_walk(root / directory):
                if not path.is_file():
                    continue
                if not is_executable(path):
                    continue
                line = first_line(path)
                if not line.startswith("#!"):
                    continue
                if not line.startswith(expected_prefix):
                    result.append(Finding(
                        severity_for(manifest, self.category),
                        self.category,
                        template.format(file=relative(path)),
                    ))
        return result
