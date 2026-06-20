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
    root_contract_contains_marker,
)
from . import models
from .paths import (
    ensure_safe_file_destination,
    ensure_safe_parent_dir,
    ensure_safe_relative_path,
    required_real_target,
    required_src,
)


class ContractsMixin(models.InstallerSupport):
    def ensure_root_contracts(self) -> None:

        if not self.config.force:
            has_conflict = False
            if self.check_root_contract_conflict("AGENTS.md", AGENTS_MARKER):
                has_conflict = True
            if self.check_root_contract_conflict("CLAUDE.md", CLAUDE_MARKER):
                has_conflict = True
            if has_conflict:
                fail(
                    "root contract conflicts must be resolved before installing assets"
                )
        self.ensure_root_contract(
            "AGENTS.md", AGENTS_MARKER, TEMPLATE_DIR / "common" / "AGENTS.md"
        )
        self.ensure_root_contract(
            "CLAUDE.md", CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md"
        )

    def ensure_root_contract(self, dst: str, marker: str, template_path: Path) -> None:

        target = Path(dst)
        if target.is_symlink() and self.config.force:
            target.unlink()
        ensure_safe_file_destination(dst)
        if not target.exists():
            tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
            ensure_safe_file_destination(str(tmp))
            tmp.write_text(self.read_install_asset(template_path), encoding="utf-8")
            os.replace(tmp, target)
            print(f"create root contract: {dst}")
            return
        content = target.read_text(encoding="utf-8")
        if root_contract_contains_marker(dst, marker, content):
            print(f"skip root contract: {dst}")
            return
        if not self.config.force:
            print(
                f"conflict root contract: {dst} lacks marker {marker}; "
                "rerun with --force to update",
                file=sys.stderr,
            )
            return
        tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        if dst == "CLAUDE.md":
            next_content = self.read_install_asset(template_path)
        else:
            next_content = f"{content}\n{self.read_install_asset(template_path)}"
        tmp.write_text(next_content, encoding="utf-8")
        os.replace(tmp, target)
        print(f"update root contract (--force): {dst}")

    def ensure_one_root_contract(self, candidate: InstallCandidate) -> None:

        real_target = required_real_target(candidate)
        template_path = required_src(candidate)
        target = Path(real_target)
        if target.is_symlink() and self.config.force:
            target.unlink()
        ensure_safe_file_destination(real_target)
        if target.exists() and root_contract_contains_marker(
            candidate.dst,
            candidate.marker,
            target.read_text(encoding="utf-8"),
        ):
            print(f"skip root contract: {candidate.dst}")
            return
        if target.exists() and not self.config.force:
            fail(
                f"conflict root contract: {real_target} lacks marker "
                f"{candidate.marker}; rerun with --force to update"
            )
        tmp = Path(f"{real_target}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        if target.exists():
            content = target.read_text(encoding="utf-8")
            if candidate.dst == "CLAUDE.md":
                next_content = self.read_install_asset(template_path)
            else:
                next_content = f"{content}\n{self.read_install_asset(template_path)}"
            tmp.write_text(next_content, encoding="utf-8")
            os.replace(tmp, target)
            print(f"update root contract (--force): {candidate.dst}")
        else:
            tmp.write_text(self.read_install_asset(template_path), encoding="utf-8")
            os.replace(tmp, target)
            print(f"create root contract: {candidate.dst}")

    def check_root_contract_conflict(self, dst: str, marker: str) -> bool:

        target = Path(dst)
        if target.is_symlink():
            print(
                f"conflict root contract: {dst} is a symlink; rerun with --force to replace it",
                file=sys.stderr,
            )
            return True
        ensure_safe_file_destination(dst)
        if target.exists() and not root_contract_contains_marker(
            dst,
            marker,
            read_text(dst),
        ):
            print(
                f"conflict root contract: {dst} lacks marker {marker}; "
                "rerun with --force to update",
                file=sys.stderr,
            )
            return True
        return False

    def ensure_target_symlink(
        self,
        link_path: str,
        target: str,
        *,
        target_is_directory: bool,
    ) -> None:

        link = Path(link_path)
        if link.is_symlink():
            if os.readlink(link_path) == target:
                return
            if not self.config.force:
                current_target = os.readlink(link_path)
                fail(
                    f"conflict symlink: {link_path} points to {current_target}; "
                    "rerun with --force to replace it"
                )
            link.unlink()
        elif link.exists():
            fail(f"conflict symlink: {link_path} already exists and is not a symlink")
        ensure_safe_file_destination(link_path)
        link.symlink_to(target, target_is_directory=target_is_directory)
        print(f"create symlink: {link_path} -> {target}")

    def ensure_target_directory(self, directory_path: str) -> None:

        directory = Path(directory_path)
        if directory.is_symlink():
            if not self.config.force:
                fail(
                    f"conflict directory: {directory_path} is a symlink; "
                    "rerun with --force to replace it"
                )
            directory.unlink()
        if directory.exists():
            if not directory.is_dir():
                fail(
                    f"conflict directory: {directory_path} exists and is not a directory"
                )
            return
        ensure_safe_relative_path(directory_path)
        parent = directory.parent
        if parent.as_posix() not in {"", "."}:
            ensure_safe_parent_dir(f"{parent.as_posix()}/.keep")
        directory.mkdir()
        print(f"create directory: {directory_path}")

    def ensure_runtime_symlinks(self) -> None:

        if not Path(".claude").is_dir():
            return
        self.ensure_target_directory(".agents")
        self.ensure_target_directory(".codex")
        if Path(".claude/skills").is_dir():
            self.ensure_target_symlink(
                ".agents/skills",
                "../.claude/skills",
                target_is_directory=True,
            )
        if Path(".claude/agents").is_dir():
            self.ensure_target_symlink(
                ".codex/agents",
                "../.claude/agents",
                target_is_directory=True,
            )
