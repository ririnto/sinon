from __future__ import annotations

import os
from pathlib import Path
import sys

from .errors import fail, read_text
from .models import (
    AGENTS_MARKER,
    CLAUDE_MARKER,
    TEMPLATE_DIR,
    InstallCandidate,
)
from . import models
from .paths import ensure_safe_file_destination, required_real_target, required_src


class ContractsMixin(models.InstallerSupport):
    def ensure_root_contracts(self) -> None:

        agents_exists = os.path.lexists("AGENTS.md")
        claude_exists = os.path.lexists("CLAUDE.md")
        agents_is_symlink = Path("AGENTS.md").is_symlink() if agents_exists else False
        claude_is_symlink = Path("CLAUDE.md").is_symlink() if claude_exists else False
        agents_target = (
            self.root_contract_symlink_target("AGENTS.md")
            if agents_is_symlink
            else "AGENTS.md"
        )
        claude_target = (
            self.root_contract_symlink_target("CLAUDE.md")
            if claude_is_symlink
            else "CLAUDE.md"
        )
        if agents_exists and claude_exists and agents_target != claude_target:
            fail(
                "root contract files diverge: AGENTS.md and CLAUDE.md point to different targets; resolve divergent root contract files before install"
            )
        if not self.config.force:
            root_conflicts = 0
            if self.check_root_contract_conflict(agents_target, AGENTS_MARKER):
                root_conflicts += 1
            if self.check_root_contract_conflict(claude_target, CLAUDE_MARKER):
                root_conflicts += 1
            if root_conflicts != 0:
                fail(
                    "root contract conflicts must be resolved before installing assets"
                )
        if agents_exists and claude_exists and agents_target == claude_target:
            self.ensure_shared_root_contract(agents_target)
            return
        if not agents_exists and not claude_exists:
            self.ensure_root_contract(
                "CLAUDE.md", CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md"
            )
            self.ensure_target_symlink("AGENTS.md", "CLAUDE.md")
            return
        if not agents_exists and claude_exists:
            self.ensure_root_contract(
                claude_target, CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md"
            )
            self.ensure_target_symlink("AGENTS.md", "CLAUDE.md")
            return
        if not claude_exists and agents_exists:
            self.ensure_root_contract(
                agents_target, AGENTS_MARKER, TEMPLATE_DIR / "common" / "AGENTS.md"
            )
            self.ensure_target_symlink("CLAUDE.md", "AGENTS.md")
            return
        self.ensure_root_contract(
            agents_target, AGENTS_MARKER, TEMPLATE_DIR / "common" / "AGENTS.md"
        )
        self.ensure_root_contract(
            claude_target, CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md"
        )

    def ensure_root_contract(self, dst: str, marker: str, template_path: Path) -> None:

        ensure_safe_file_destination(dst)
        target = Path(dst)
        if not target.exists():
            tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
            ensure_safe_file_destination(str(tmp))
            tmp.write_text(self.render_template(template_path), encoding="utf-8")
            os.replace(tmp, target)
            print(f"create root contract: {dst}")
            return
        content = target.read_text(encoding="utf-8")
        if marker in content:
            print(f"skip root contract: {dst}")
            return
        if not self.config.force:
            print(
                f"conflict root contract: {dst} lacks marker {marker}; rerun with --force to append",
                file=sys.stderr,
            )
            return
        tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        tmp.write_text(
            f"{content}\n{self.render_template(template_path)}", encoding="utf-8"
        )
        os.replace(tmp, target)
        print(f"update root contract (--force): {dst}")

    def ensure_shared_root_contract(self, dst: str) -> None:

        target = Path(dst)
        had_file = target.is_file()
        existing = target.read_text(encoding="utf-8") if had_file else ""
        has_agents = AGENTS_MARKER in existing
        has_claude = CLAUDE_MARKER in existing
        if has_agents and has_claude:
            print(f"skip shared root contract: {dst}")
            return
        ensure_safe_file_destination(dst)
        combined = existing
        if not has_agents:
            if combined and not combined.endswith("\n"):
                combined += "\n"
            combined += self.render_template(TEMPLATE_DIR / "common" / "AGENTS.md")
        if not has_claude:
            if combined and not combined.endswith("\n"):
                combined += "\n"
            combined += self.render_template(TEMPLATE_DIR / "common" / "CLAUDE.md")
        tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        tmp.write_text(combined, encoding="utf-8")
        os.replace(tmp, target)
        if had_file:
            print(f"update shared root contract (--force): {dst}")
        else:
            print(f"create shared root contract: {dst}")

    def ensure_one_root_contract(self, candidate: InstallCandidate) -> None:

        real_target = required_real_target(candidate)
        template_path = required_src(candidate)
        ensure_safe_file_destination(real_target)
        target = Path(real_target)
        if target.exists() and candidate.marker in target.read_text(encoding="utf-8"):
            print(f"skip root contract: {candidate.dst}")
            return
        if target.exists() and not self.config.force:
            fail(
                f"conflict root contract: {real_target} lacks marker {candidate.marker}; rerun with --force to append"
            )
        tmp = Path(f"{real_target}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        if target.exists():
            tmp.write_text(
                f"{target.read_text(encoding='utf-8')}\n{self.render_template(template_path)}",
                encoding="utf-8",
            )
            os.replace(tmp, target)
            print(f"update root contract (--force): {candidate.dst}")
        else:
            tmp.write_text(self.render_template(template_path), encoding="utf-8")
            os.replace(tmp, target)
            print(f"create root contract: {candidate.dst}")

    def check_root_contract_conflict(self, dst: str, marker: str) -> bool:

        ensure_safe_file_destination(dst)
        if Path(dst).exists() and marker not in read_text(dst):
            print(
                f"conflict root contract: {dst} lacks marker {marker}; rerun with --force to append",
                file=sys.stderr,
            )
            return True
        return False

    def root_contract_symlink_target(self, file_path: str) -> str:

        target = os.readlink(file_path)
        if file_path == "AGENTS.md" and target == "CLAUDE.md":
            return target
        if file_path == "CLAUDE.md" and target == "AGENTS.md":
            return target
        fail(
            f"[root_contract_symlink] unsupported symlink target (must be AGENTS.md <-> CLAUDE.md): {file_path} -> {target}"
        )

    def ensure_target_symlink(self, link_path: str, target: str) -> None:

        if os.path.lexists(link_path):
            return
        Path(link_path).symlink_to(target)
        print(f"create symlink: {link_path} -> {target}")

    def ensure_agents_symlink(self) -> None:

        if not os.path.lexists(".agents") and Path(".claude").is_dir():
            self.ensure_target_symlink(".agents", ".claude")
