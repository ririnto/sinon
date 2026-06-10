from __future__ import annotations

import os
from pathlib import Path
import subprocess

from .errors import fail
from .models import SKILL_DIR, VALIDATION_PLACEHOLDER
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

    def render_template(self, template_file: Path) -> str:

        if not template_file.is_file():
            fail(f"[render_validation_stream] missing template: {template_file}")
        text = template_file.read_text(encoding="utf-8")
        return text.replace(VALIDATION_PLACEHOLDER, self.config.validation_command)

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
