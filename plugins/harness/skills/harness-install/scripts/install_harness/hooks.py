from __future__ import annotations

import os
from pathlib import Path
import stat
import subprocess

from .commands import generated_hook_text
from .errors import fail, read_text
from .models import SKILL_DIR, VALIDATION_PLACEHOLDER, InstallCandidate
from .paths import ensure_safe_file_destination
from .models import InstallerSupport


class HooksMixin(InstallerSupport):
    def ensure_gitkeep_paths(self) -> None:

        for keep in (
            "docs/exec-plans/active/.gitkeep",
            "docs/exec-plans/completed/.gitkeep",
            "docs/generated/.gitkeep",
        ):
            self.install_one_gitkeep_path(keep, create_only=True)

    def install_one_gitkeep_path(self, keep: str, *, create_only: bool = False) -> None:

        ensure_safe_file_destination(keep)
        target = Path(keep)
        if target.exists():
            if create_only:
                return
            if self.config.force:
                tmp = self.temporary_destination(keep, "install_one_gitkeep_path")
                tmp.write_text("", encoding="utf-8")
                os.replace(tmp, target)
                print(f"overwrite (--force): {keep}")
            else:
                print(f"keep existing: {keep}")
            return
        target.write_text("", encoding="utf-8")
        print(f"write: {keep}")

    def install_target_hook_templates(self) -> None:

        for candidate in self.hook_install_candidates():
            self.install_one_target_hook_template(candidate)

    def install_one_target_hook_template(self, candidate: InstallCandidate) -> None:

        dst = candidate.dst
        ensure_safe_file_destination(dst)
        if (
            Path(dst).exists()
            and not self.config.force
            and not self.is_managed_generated_hook(dst, candidate.marker)
        ):
            if (
                "packaged placeholder is replaced during harness installation"
                in read_text(dst)
            ):
                fail(
                    f"[install_target_hook_template] existing harness hook placeholder is not selected-mode content: {dst}; rerun with --force to replace"
                )
            print(f"keep existing: {dst}")
            return
        tmp = self.temporary_destination(dst, "install_target_hook_template")
        if dst.endswith("/pre-commit"):
            tmp.write_text(self.pre_commit_hook_text(), encoding="utf-8")
        elif dst.endswith("/pre-push"):
            tmp.write_text(self.pre_push_hook_text(), encoding="utf-8")
        else:
            fail(
                f"[install_target_hook_template] unsupported harness hook template (must be pre-commit or pre-push): {dst}"
            )
        tmp.chmod(tmp.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        had_existing = Path(dst).exists()
        os.replace(tmp, dst)
        if had_existing:
            if self.config.force:
                print(f"overwrite (--force): {dst}")
            else:
                print(f"refresh generated {candidate.stage} hook: {dst}")
        else:
            print(f"write: {dst}")

    def is_managed_generated_hook(self, dst: str, marker: str) -> bool:

        return Path(dst).is_file() and marker in read_text(dst)

    def render_template(self, template_file: Path) -> str:

        if not template_file.is_file():
            fail(f"[render_validation_stream] missing template: {template_file}")
        text = template_file.read_text(encoding="utf-8")
        return text.replace(VALIDATION_PLACEHOLDER, self.config.validation_command)

    def pre_commit_hook_text(self) -> str:

        return generated_hook_text(
            "pre-commit", "harness-validation", self.config.validation_command
        )

    def pre_push_hook_text(self) -> str:

        return generated_hook_text(
            "pre-push", "full-validation", self.config.validation_command
        )

    def list_tracked_tree_files(self, src_dir: Path) -> list[Path]:

        if not src_dir.is_dir():
            return []
        src_rel = str(src_dir.relative_to(SKILL_DIR))
        proc = subprocess.run(
            ["git", "-C", str(SKILL_DIR), "ls-files", "--", src_rel],
            check=True,
            capture_output=True,
            text=True,
        )
        return [
            (SKILL_DIR / line).resolve()
            for line in proc.stdout.splitlines()
            if (SKILL_DIR / line).is_file()
        ]

    def temporary_destination(self, dst: str, label: str) -> Path:

        parent = Path(dst).parent if str(Path(dst).parent) != "." else Path(".")
        tmp = parent / f".harness-tmp-{os.getpid()}-{Path(dst).name}"
        ensure_safe_file_destination(str(tmp))
        if tmp.exists():
            fail(
                f"[{label}] temporary destination already exists: {tmp} (cleanup or retry)"
            )
        return tmp
