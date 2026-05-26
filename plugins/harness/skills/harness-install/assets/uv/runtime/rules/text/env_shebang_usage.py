#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require env shebang under rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import (
    Finding,
    FindingEdit,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
)
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class EnvShebangUsageRule(HarnessCheckRule):
    """Validate envShebangUsage check."""

    category = "envShebangUsage"

    def applies(self, ctx: RuleContext) -> bool:
        """
        Check if this rule applies to the context.

        :param ctx: rule execution context.
        :returns: ``True`` when the rule should run.
        """
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """
        Validate envShebangUsage check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
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
        template = messages.get(
            "default", "executable script should use /usr/bin/env shebang: {file}"
        )
        result = []
        for directory in directories:
            if isinstance(directory, str):
                files, _ = ctx.walk_directory(directory)
                for path in files:
                    path_str = path.relative_to(ctx.root).as_posix()
                    if ctx.is_executable(path_str):
                        line = ctx.first_line(path_str)
                        if line.startswith("#!") and not line.startswith(
                            expected_prefix
                        ):
                            relative_path = relative(path, ctx.root)
                            interpreter = line[2:].strip() if len(line) > 2 else ""
                            env_shebang = f"#!/usr/bin/env {interpreter.split()[-1] if interpreter else 'sh'}"
                            result.append(
                                Finding(
                                    ctx.severity_of(self.category),
                                    self.category,
                                    template.format(file=relative_path),
                                    file=relative_path,
                                    start_line=1,
                                    start_column=1,
                                    end_line=1,
                                    end_column=len(line) + 1,
                                    fix=FindingFix(
                                        description=f"replace shebang with `{env_shebang}`",
                                        safety=FixSafety.SAFE,
                                        edits=(
                                            FindingEdit(
                                                file=relative_path,
                                                start_line=1,
                                                start_column=1,
                                                end_line=1,
                                                end_column=len(line) + 1,
                                                replacement=env_shebang,
                                            ),
                                        ),
                                    ),
                                )
                            )
        return result


RULE: HarnessCheckRule = EnvShebangUsageRule()
