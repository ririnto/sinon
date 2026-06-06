#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///

"""
Install target-owned harness assets into a repository.

:returns: ``None``.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys


MODES = ("gradle", "maven", "uv", "bun", "shell")
CI_HOSTS = ("github", "gitlab", "both", "none")
VALIDATION_PLACEHOLDER = "{{validation_command}}"
AGENTS_MARKER = "# Repository Harness Contract"
CLAUDE_MARKER = "# Entry Point"

SCRIPT_PATH = Path(__file__).resolve()
SCRIPT_DIR = SCRIPT_PATH.parent
SKILL_DIR = SCRIPT_DIR.parent
TEMPLATE_DIR = SKILL_DIR / "assets"


@dataclass(frozen=True)
class InstallerConfig:
    """InstallerConfig class.

    :returns: ``None``.
    """


    mode: str
    ci_host: str
    target_root: Path
    force: bool
    action: str
    selected_path: str | None

    @property
    def validation_command(self) -> str:
        """validation command operation.

        :returns: ``str``.
        """

        return validation_command_for_mode(self.mode)


@dataclass(frozen=True)
class InstallCandidate:
    """InstallCandidate class.

    :returns: ``None``.
    """


    kind: str
    dst: str
    src: Path | None = None
    seed: bool = False
    real_target: str | None = None
    marker: str = ""
    stage: str = ""
    symlink_target: str = ""


@dataclass(frozen=True)
class InstallPlan:
    """InstallPlan class.

    :returns: ``None``.
    """


    candidates: tuple[InstallCandidate, ...]

    def match(self, target_path: str) -> InstallCandidate:
        """match operation.

        :param target_path: Value for target_path.

        :returns: ``InstallCandidate``.
        """

        for candidate in self.candidates:
            if candidate.dst == target_path:
                return candidate
        fail(f"requested path is not in the selected install set: {target_path}")


class HarnessArgumentParser(argparse.ArgumentParser):
    """HarnessArgumentParser class.

    :returns: ``None``.
    """


    def error(self, message: str) -> None:
        """error operation.

        :param message: Value for message.

        :returns: ``None``.
        """

        if message == "argument --mode is required":
            message = "--mode is required (gradle|maven|uv|bun|shell)."
        elif message == "argument --ci-host is required":
            message = "--ci-host is required (github|gitlab|both|none)."
        fail(message, exit_code=2)


class HarnessInstaller:
    """HarnessInstaller class.

    :returns: ``None``.
    """


    def __init__(self, config: InstallerConfig) -> None:
        """init operation.

        :param config: Value for config.

        :returns: ``None``.
        """

        self.config = config
        self.target_root = config.target_root.resolve()

    def run(self) -> None:
        """run operation.

        :returns: ``None``.
        """

        if not self.target_root.is_dir():
            fail(f"target root is not a directory: {self.config.target_root}")
        os.chdir(self.target_root)

        if self.config.action == "preview":
            self.preview_install_set()
            return

        if self.config.action == "show":
            selected_path = normalize_requested_target_path(required_selected_path(self.config))
            self.show_one_target_path(selected_path)
            return

        if self.config.action == "only":
            selected_path = normalize_requested_target_path(required_selected_path(self.config))
            self.install_one_target_path(selected_path)
            self.print_summary(selected_path)
            self.runtime_advisory_for_mode()
            return

        self.install_full_plan()
        self.print_summary()
        self.runtime_advisory_for_mode()

    def build_plan(self) -> InstallPlan:
        """build plan operation.

        :returns: ``InstallPlan``.
        """

        candidates = (
            self.common_install_candidates()
            + self.root_contract_install_candidates()
            + self.stack_install_candidates()
            + self.hook_install_candidates()
            + self.gitkeep_install_candidates()
        )
        return InstallPlan(tuple(candidates))

    def common_install_candidates(self) -> list[InstallCandidate]:
        """common install candidates operation.

        :returns: ``list[InstallCandidate]``.
        """

        src_dir = TEMPLATE_DIR / "common"
        candidates: list[InstallCandidate] = []
        if not src_dir.is_dir():
            return candidates
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if is_common_skip_path(rel):
                continue
            candidates.append(
                InstallCandidate(
                    "seed" if is_direct_template_entry(rel) else "file",
                    rel,
                    src=src,
                    seed=is_direct_template_entry(rel),
                )
            )
        return candidates

    def stack_install_candidates(self) -> list[InstallCandidate]:
        """stack install candidates operation.

        :returns: ``list[InstallCandidate]``.
        """

        src_dir = TEMPLATE_DIR / self.config.mode
        candidates: list[InstallCandidate] = []
        if not src_dir.is_dir():
            return candidates
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if rel == ".gitlab-ci.yml":
                if self.config.ci_host in {"gitlab", "both"}:
                    candidates.append(InstallCandidate("stack-file", ".gitlab-ci.yml", src=src))
                continue
            if rel.startswith(".github/workflows/"):
                if self.config.ci_host in {"github", "both"}:
                    workflow = workflow_name_for_mode(self.config.mode)
                    candidates.append(InstallCandidate("stack-file", f".github/workflows/{workflow}", src=src))
                continue
            candidates.append(InstallCandidate("stack-file", rel, src=src))
        return candidates

    def root_contract_install_candidates(self) -> list[InstallCandidate]:
        """root contract install candidates operation.

        :returns: ``list[InstallCandidate]``.
        """

        candidates = [
            self.root_contract_candidate_for_request(
                "AGENTS.md",
                AGENTS_MARKER,
                TEMPLATE_DIR / "common" / "AGENTS.md",
            ),
            self.root_contract_candidate_for_request(
                "CLAUDE.md",
                CLAUDE_MARKER,
                TEMPLATE_DIR / "common" / "CLAUDE.md",
            ),
        ]
        agents_exists = os.path.lexists("AGENTS.md")
        claude_exists = os.path.lexists("CLAUDE.md")
        agents_is_symlink = Path("AGENTS.md").is_symlink() if agents_exists else False
        claude_is_symlink = Path("CLAUDE.md").is_symlink() if claude_exists else False
        if not agents_exists and not claude_exists:
            candidates.append(InstallCandidate("symlink", "AGENTS.md", symlink_target="CLAUDE.md"))
        elif not agents_exists and claude_exists and not claude_is_symlink:
            candidates.append(InstallCandidate("symlink", "AGENTS.md", symlink_target="CLAUDE.md"))
        elif not claude_exists and agents_exists and not agents_is_symlink:
            candidates.append(InstallCandidate("symlink", "CLAUDE.md", symlink_target="AGENTS.md"))
        if Path(".claude").is_dir():
            candidates.append(InstallCandidate("symlink", ".agents", symlink_target=".claude"))
        return candidates

    def root_contract_candidate_for_request(
        self,
        request_file: str,
        marker: str,
        template_path: Path,
    ) -> InstallCandidate:
        """root contract candidate for request operation.

        :param request_file: Value for request_file.
        :param marker: Value for marker.
        :param template_path: Value for template_path.

        :returns: ``InstallCandidate``.
        """

        if Path(request_file).is_symlink():
            real_target = self.root_contract_symlink_target(request_file)
            return InstallCandidate("root-contract", request_file, src=template_path, real_target=real_target, marker=marker)
        if Path(request_file).exists():
            return InstallCandidate("root-contract", request_file, src=template_path, real_target=request_file, marker=marker)
        other_file = "CLAUDE.md" if request_file == "AGENTS.md" else "AGENTS.md"
        if Path(other_file).is_symlink():
            real_target = self.root_contract_symlink_target(other_file)
            return InstallCandidate("root-contract", request_file, src=template_path, real_target=real_target, marker=marker)
        if Path(other_file).exists():
            return InstallCandidate("root-contract", request_file, src=template_path, real_target=other_file, marker=marker)
        return InstallCandidate("root-contract", request_file, src=template_path, real_target="CLAUDE.md", marker=marker)

    def hook_install_candidates(self) -> list[InstallCandidate]:
        """hook install candidates operation.

        :returns: ``list[InstallCandidate]``.
        """

        return [
            InstallCandidate(
                "generated-hook",
                "docs/harness/git-hooks/pre-commit",
                marker="Harness generated hook: pre-commit",
                stage="pre-commit",
            ),
            InstallCandidate(
                "generated-hook",
                "docs/harness/git-hooks/pre-push",
                marker="Harness generated hook: pre-push",
                stage="pre-push",
            ),
        ]

    def gitkeep_install_candidates(self) -> list[InstallCandidate]:
        """gitkeep install candidates operation.

        :returns: ``list[InstallCandidate]``.
        """

        return [
            InstallCandidate("gitkeep", "docs/exec-plans/active/.gitkeep"),
            InstallCandidate("gitkeep", "docs/exec-plans/completed/.gitkeep"),
            InstallCandidate("gitkeep", "docs/generated/.gitkeep"),
        ]

    def preview_install_set(self) -> None:
        """preview install set operation.

        :returns: ``None``.
        """

        print(f"harness target: {Path.cwd()}")
        print(f"harness mode: {self.config.mode}")
        print(f"ci-host: {self.config.ci_host}")
        print(f"validation command: {self.config.validation_command}")
        print(f"pre-commit command: {self.config.validation_command}")
        for candidate in self.build_plan().candidates:
            self.preview_candidate_status(candidate)

    def preview_candidate_status(self, candidate: InstallCandidate) -> None:
        """preview candidate status operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        if candidate.kind in {"file", "seed", "stack-file"}:
            self.preview_file_candidate(candidate)
        elif candidate.kind == "root-contract":
            self.preview_root_contract_candidate(candidate)
        elif candidate.kind == "generated-hook":
            self.preview_generated_hook_candidate(candidate)
        elif candidate.kind == "symlink":
            self.preview_symlink_candidate(candidate)
        elif candidate.kind == "gitkeep":
            self.preview_gitkeep_candidate(candidate)
        else:
            print(f"skip unknown candidate: {candidate.dst}", file=sys.stderr)

    def preview_file_candidate(self, candidate: InstallCandidate) -> None:
        """preview file candidate operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        if candidate.seed and Path(candidate.dst).exists():
            print(f"skip seed (target exists): {candidate.dst}")
            return
        if Path(candidate.dst).exists():
            if self.config.force:
                print(f"overwrite (--force): {candidate.dst}")
            else:
                print(f"keep existing: {candidate.dst}")
            return
        print(f"write: {candidate.dst}")

    def preview_root_contract_candidate(self, candidate: InstallCandidate) -> None:
        """preview root contract candidate operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        real_target = required_real_target(candidate)
        if Path(real_target).exists() and candidate.marker in read_text(real_target):
            print(f"skip root contract: {candidate.dst}")
            return
        if Path(real_target).exists() and not self.config.force:
            print(
                f"conflict root contract: {real_target} lacks marker {candidate.marker}; rerun with --force to append",
                file=sys.stderr,
            )
            return
        if Path(real_target).exists():
            print(f"update root contract (--force): {candidate.dst}")
        else:
            print(f"create root contract: {candidate.dst}")

    def preview_generated_hook_candidate(self, candidate: InstallCandidate) -> None:
        """preview generated hook candidate operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        dst = candidate.dst
        if Path(dst).exists() and not self.config.force and not self.is_managed_generated_hook(dst, candidate.marker):
            if "packaged placeholder is replaced during harness installation" in read_text(dst):
                print(f"error: {dst} is a packaged placeholder; rerun with --force to replace", file=sys.stderr)
                return
            print(f"keep existing: {dst}")
            return
        if Path(dst).exists():
            if self.config.force:
                print(f"overwrite (--force): {dst}")
            else:
                print(f"refresh generated {candidate.stage} hook: {dst}")
            return
        print(f"write: {dst}")

    def preview_symlink_candidate(self, candidate: InstallCandidate) -> None:
        """preview symlink candidate operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        if Path(candidate.dst).is_symlink():
            current_target = os.readlink(candidate.dst)
            if current_target == candidate.symlink_target:
                print(f"skip existing symlink: {candidate.dst} -> {candidate.symlink_target}")
            else:
                print(f"skip existing symlink: {candidate.dst} -> {current_target}", file=sys.stderr)
            return
        if Path(candidate.dst).exists():
            print(f"skip existing: {candidate.dst}")
            return
        print(f"create symlink: {candidate.dst} -> {candidate.symlink_target}")

    def preview_gitkeep_candidate(self, candidate: InstallCandidate) -> None:
        """preview gitkeep candidate operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        if Path(candidate.dst).exists():
            if self.config.force:
                print(f"overwrite (--force): {candidate.dst}")
            else:
                print(f"keep existing: {candidate.dst}")
            return
        print(f"write: {candidate.dst}")

    def show_one_target_path(self, requested_path: str) -> None:
        """show one target path operation.

        :param requested_path: Value for requested_path.

        :returns: ``None``.
        """

        candidate = self.build_plan().match(requested_path)
        if candidate.kind in {"file", "seed", "stack-file"}:
            print(self.render_template(required_src(candidate)), end="")
            return
        if candidate.kind == "generated-hook":
            if candidate.dst.endswith("/pre-commit"):
                print(self.pre_commit_hook_text(), end="")
            else:
                print(self.pre_push_hook_text(), end="")
            return
        if candidate.kind == "root-contract":
            real_target = required_real_target(candidate)
            if Path(real_target).exists() and candidate.marker in read_text(real_target):
                return
            if Path(real_target).exists():
                print(
                    f"note: requested root contract content would be appended to existing file: {real_target}",
                    file=sys.stderr,
                )
            print(self.render_template(required_src(candidate)), end="")
            return
        if candidate.kind == "gitkeep":
            return
        if candidate.kind == "symlink":
            fail(f"--show on symlink entry is not supported; run full install to create {requested_path}")
        fail(f"requested path is not renderable: {requested_path}")

    def install_one_target_path(self, requested_path: str) -> None:
        """install one target path operation.

        :param requested_path: Value for requested_path.

        :returns: ``None``.
        """

        candidate = self.build_plan().match(requested_path)
        if candidate.kind in {"file", "stack-file", "seed"}:
            self.copy_template_file(required_src(candidate), candidate.dst, seed=candidate.seed)
            return
        if candidate.kind == "generated-hook":
            self.install_one_target_hook_template(candidate)
            return
        if candidate.kind == "gitkeep":
            self.install_one_gitkeep_path(candidate.dst)
            return
        if candidate.kind == "root-contract":
            self.ensure_one_root_contract(candidate)
            return
        if candidate.kind == "symlink":
            fail(f"cannot place symlink entry with --only; run full install for {requested_path}")
        fail(f"unsupported --only target selection: {requested_path}")

    def install_full_plan(self) -> None:
        """install full plan operation.

        :returns: ``None``.
        """

        self.ensure_root_contracts()
        self.copy_tree(TEMPLATE_DIR / "common", ".", common=True)
        self.ensure_agents_symlink()
        self.ensure_gitkeep_paths()
        self.copy_stack_tree(TEMPLATE_DIR / self.config.mode, ".")
        self.install_target_hook_templates()

    def copy_tree(self, src_dir: Path, dst_dir: str, *, common: bool) -> None:
        """copy tree operation.

        :param src_dir: Value for src_dir.
        :param dst_dir: Value for dst_dir.
        :param common: Value for common.

        :returns: ``None``.
        """

        if not src_dir.is_dir():
            return
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if common and is_common_skip_path(rel):
                continue
            dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
            self.copy_template_file(src, dst, seed=common and is_direct_template_entry(rel))

    def copy_stack_tree(self, src_dir: Path, dst_dir: str) -> None:
        """copy stack tree operation.

        :param src_dir: Value for src_dir.
        :param dst_dir: Value for dst_dir.

        :returns: ``None``.
        """

        if not src_dir.is_dir():
            return
        workflow_name = workflow_name_for_mode(self.config.mode)
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if rel == ".gitlab-ci.yml":
                if self.config.ci_host not in {"gitlab", "both"}:
                    continue
                dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
                self.copy_template_file(src, dst)
                continue
            if rel.startswith(".github/workflows/"):
                if self.config.ci_host not in {"github", "both"}:
                    continue
                dst = f".github/workflows/{workflow_name}" if dst_dir in {"", "."} else f"{dst_dir}/.github/workflows/{workflow_name}"
                self.copy_template_file(src, dst)
                continue
            dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
            self.copy_template_file(src, dst)

    def copy_template_file(self, src_file: Path, dst: str, *, seed: bool = False) -> None:
        """copy template file operation.

        :param src_file: Value for src_file.
        :param dst: Value for dst.
        :param seed: Value for seed.

        :returns: ``None``.
        """

        ensure_safe_file_destination(dst)
        if Path(dst).exists() and not self.config.force:
            if seed:
                print(f"skip seed (target exists): {dst}")
            else:
                print(f"keep existing: {dst}")
            return
        tmp = self.temporary_destination(dst, "copy_file")
        tmp.write_text(self.render_template(src_file), encoding="utf-8")
        if os.access(src_file, os.X_OK):
            file_mode = tmp.stat().st_mode
            tmp.chmod(file_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        had_existing = Path(dst).exists()
        os.replace(tmp, dst)
        if had_existing:
            if seed:
                print(f"overwrite seed (--force): {dst}")
            else:
                print(f"overwrite (--force): {dst}")
        else:
            if seed:
                print(f"deliver seed: {dst}")
            else:
                print(f"write: {dst}")

    def ensure_root_contracts(self) -> None:
        """ensure root contracts operation.

        :returns: ``None``.
        """

        agents_exists = os.path.lexists("AGENTS.md")
        claude_exists = os.path.lexists("CLAUDE.md")
        agents_is_symlink = Path("AGENTS.md").is_symlink() if agents_exists else False
        claude_is_symlink = Path("CLAUDE.md").is_symlink() if claude_exists else False
        agents_target = self.root_contract_symlink_target("AGENTS.md") if agents_is_symlink else "AGENTS.md"
        claude_target = self.root_contract_symlink_target("CLAUDE.md") if claude_is_symlink else "CLAUDE.md"
        if agents_exists and claude_exists and agents_target != claude_target:
            fail("root contract files diverge: AGENTS.md and CLAUDE.md point to different targets; resolve divergent root contract files before install")
        if not self.config.force:
            root_conflicts = 0
            if self.check_root_contract_conflict(agents_target, AGENTS_MARKER):
                root_conflicts += 1
            if self.check_root_contract_conflict(claude_target, CLAUDE_MARKER):
                root_conflicts += 1
            if root_conflicts != 0:
                fail("root contract conflicts must be resolved before installing harness assets")
        if agents_exists and claude_exists and agents_target == claude_target:
            self.ensure_shared_root_contract(agents_target)
            return
        if not agents_exists and not claude_exists:
            self.ensure_root_contract("CLAUDE.md", CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md")
            self.ensure_target_symlink("AGENTS.md", "CLAUDE.md")
            return
        if not agents_exists and claude_exists:
            self.ensure_root_contract(claude_target, CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md")
            self.ensure_target_symlink("AGENTS.md", "CLAUDE.md")
            return
        if not claude_exists and agents_exists:
            self.ensure_root_contract(agents_target, AGENTS_MARKER, TEMPLATE_DIR / "common" / "AGENTS.md")
            self.ensure_target_symlink("CLAUDE.md", "AGENTS.md")
            return
        self.ensure_root_contract(agents_target, AGENTS_MARKER, TEMPLATE_DIR / "common" / "AGENTS.md")
        self.ensure_root_contract(claude_target, CLAUDE_MARKER, TEMPLATE_DIR / "common" / "CLAUDE.md")

    def ensure_root_contract(self, dst: str, marker: str, template_path: Path) -> None:
        """ensure root contract operation.

        :param dst: Value for dst.
        :param marker: Value for marker.
        :param template_path: Value for template_path.

        :returns: ``None``.
        """

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
            print(f"conflict root contract: {dst} lacks marker {marker}; rerun with --force to append", file=sys.stderr)
            return
        tmp = Path(f"{dst}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        tmp.write_text(f"{content}\n{self.render_template(template_path)}", encoding="utf-8")
        os.replace(tmp, target)
        print(f"update root contract (--force): {dst}")

    def ensure_shared_root_contract(self, dst: str) -> None:
        """ensure shared root contract operation.

        :param dst: Value for dst.

        :returns: ``None``.
        """

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
        """ensure one root contract operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        real_target = required_real_target(candidate)
        template_path = required_src(candidate)
        ensure_safe_file_destination(real_target)
        target = Path(real_target)
        if target.exists() and candidate.marker in target.read_text(encoding="utf-8"):
            print(f"skip root contract: {candidate.dst}")
            return
        if target.exists() and not self.config.force:
            fail(f"conflict root contract: {real_target} lacks marker {candidate.marker}; rerun with --force to append")
        tmp = Path(f"{real_target}.harness.tmp.{os.getpid()}")
        ensure_safe_file_destination(str(tmp))
        if target.exists():
            tmp.write_text(f"{target.read_text(encoding='utf-8')}\n{self.render_template(template_path)}", encoding="utf-8")
            os.replace(tmp, target)
            print(f"update root contract (--force): {candidate.dst}")
        else:
            tmp.write_text(self.render_template(template_path), encoding="utf-8")
            os.replace(tmp, target)
            print(f"create root contract: {candidate.dst}")

    def check_root_contract_conflict(self, dst: str, marker: str) -> bool:
        """check root contract conflict operation.

        :param dst: Value for dst.
        :param marker: Value for marker.

        :returns: ``bool``.
        """

        ensure_safe_file_destination(dst)
        if Path(dst).exists() and marker not in read_text(dst):
            print(f"conflict root contract: {dst} lacks marker {marker}; rerun with --force to append", file=sys.stderr)
            return True
        return False

    def root_contract_symlink_target(self, file_path: str) -> str:
        """root contract symlink target operation.

        :param file_path: Value for file_path.

        :returns: ``str``.
        """

        target = os.readlink(file_path)
        if file_path == "AGENTS.md" and target == "CLAUDE.md":
            return target
        if file_path == "CLAUDE.md" and target == "AGENTS.md":
            return target
        fail(f"[root_contract_symlink] unsupported symlink target (must be AGENTS.md <-> CLAUDE.md): {file_path} -> {target}")

    def ensure_target_symlink(self, link_path: str, target: str) -> None:
        """ensure target symlink operation.

        :param link_path: Value for link_path.
        :param target: Value for target.

        :returns: ``None``.
        """

        if os.path.lexists(link_path):
            return
        Path(link_path).symlink_to(target)
        print(f"create symlink: {link_path} -> {target}")

    def ensure_agents_symlink(self) -> None:
        """ensure agents symlink operation.

        :returns: ``None``.
        """

        if not os.path.lexists(".agents") and Path(".claude").is_dir():
            self.ensure_target_symlink(".agents", ".claude")

    def ensure_gitkeep_paths(self) -> None:
        """ensure gitkeep paths operation.

        :returns: ``None``.
        """

        for keep in (
            "docs/exec-plans/active/.gitkeep",
            "docs/exec-plans/completed/.gitkeep",
            "docs/generated/.gitkeep",
        ):
            self.install_one_gitkeep_path(keep, create_only=True)

    def install_one_gitkeep_path(self, keep: str, *, create_only: bool = False) -> None:
        """install one gitkeep path operation.

        :param keep: Value for keep.
        :param create_only: Value for create_only.

        :returns: ``None``.
        """

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
        """install target hook templates operation.

        :returns: ``None``.
        """

        for candidate in self.hook_install_candidates():
            self.install_one_target_hook_template(candidate)

    def install_one_target_hook_template(self, candidate: InstallCandidate) -> None:
        """install one target hook template operation.

        :param candidate: Value for candidate.

        :returns: ``None``.
        """

        dst = candidate.dst
        ensure_safe_file_destination(dst)
        if Path(dst).exists() and not self.config.force and not self.is_managed_generated_hook(dst, candidate.marker):
            if "packaged placeholder is replaced during harness installation" in read_text(dst):
                fail(f"[install_target_hook_template] existing harness hook placeholder is not selected-mode content: {dst}; rerun with --force to replace")
            print(f"keep existing: {dst}")
            return
        tmp = self.temporary_destination(dst, "install_target_hook_template")
        if dst.endswith("/pre-commit"):
            tmp.write_text(self.pre_commit_hook_text(), encoding="utf-8")
        elif dst.endswith("/pre-push"):
            tmp.write_text(self.pre_push_hook_text(), encoding="utf-8")
        else:
            fail(f"[install_target_hook_template] unsupported harness hook template (must be pre-commit or pre-push): {dst}")
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
        """is managed generated hook operation.

        :param dst: Value for dst.
        :param marker: Value for marker.

        :returns: ``bool``.
        """

        return Path(dst).is_file() and marker in read_text(dst)

    def render_template(self, template_file: Path) -> str:
        """render template operation.

        :param template_file: Value for template_file.

        :returns: ``str``.
        """

        if not template_file.is_file():
            fail(f"[render_validation_stream] missing template: {template_file}")
        text = template_file.read_text(encoding="utf-8")
        return text.replace(VALIDATION_PLACEHOLDER, self.config.validation_command)

    def pre_commit_hook_text(self) -> str:
        """pre commit hook text operation.

        :returns: ``str``.
        """

        return generated_hook_text("pre-commit", "harness-validation", self.config.validation_command)

    def pre_push_hook_text(self) -> str:
        """pre push hook text operation.

        :returns: ``str``.
        """

        return generated_hook_text("pre-push", "full-validation", self.config.validation_command)

    def list_tracked_tree_files(self, src_dir: Path) -> list[Path]:
        """list tracked tree files operation.

        :param src_dir: Value for src_dir.

        :returns: ``list[Path]``.
        """

        if not src_dir.is_dir():
            return []
        src_rel = str(src_dir.relative_to(SKILL_DIR))
        proc = subprocess.run(
            ["git", "-C", str(SKILL_DIR), "ls-files", "--", src_rel],
            check=True,
            capture_output=True,
            text=True,
        )
        return [(SKILL_DIR / line).resolve() for line in proc.stdout.splitlines() if (SKILL_DIR / line).is_file()]

    def temporary_destination(self, dst: str, label: str) -> Path:
        """temporary destination operation.

        :param dst: Value for dst.
        :param label: Value for label.

        :returns: ``Path``.
        """

        parent = Path(dst).parent if str(Path(dst).parent) != "." else Path(".")
        tmp = parent / f".harness-tmp-{os.getpid()}-{Path(dst).name}"
        ensure_safe_file_destination(str(tmp))
        if tmp.exists():
            fail(f"[{label}] temporary destination already exists: {tmp} (cleanup or retry)")
        return tmp

    def runtime_advisory_for_mode(self) -> None:
        """runtime advisory for mode operation.

        :returns: ``None``.
        """

        mode = self.config.mode
        if mode == "gradle" and not Path("./gradlew").is_file():
            print("[advisory] ./gradlew is required before running validation; add or restore the Gradle wrapper in the target repository.", file=sys.stderr)
        elif mode == "maven" and not Path("./mvnw").is_file():
            print("[advisory] ./mvnw is required before running validation; add or restore the Maven wrapper in the target repository.", file=sys.stderr)
        elif mode == "uv" and not shutil.which("uv"):
            print("[advisory] uv command not found on PATH; install via the official script (`curl -LsSf https://astral.sh/uv/install.sh | sh`) or Homebrew (`brew install uv`) before running validation.", file=sys.stderr)
        elif mode == "bun" and not shutil.which("bun"):
            print("[advisory] bun command not found on PATH; install via the official script (`curl -fsSL https://bun.sh/install | bash`) or Homebrew (`brew install oven-sh/bun/bun`) before running validation.", file=sys.stderr)
        elif mode == "shell":
            print("[advisory] shellcheck and shfmt are required; install them via your OS package manager (for example, `apt install shellcheck shfmt` on Debian/Ubuntu or `brew install shellcheck shfmt` on macOS) before running validation.", file=sys.stderr)

    def print_summary(self, only_selected: str | None = None) -> None:
        """print summary operation.

        :param only_selected: Value for only_selected.

        :returns: ``None``.
        """

        print("")
        print(f"harness target: {Path.cwd()}")
        print(f"harness mode: {self.config.mode}")
        print(f"ci-host: {self.config.ci_host}")
        print(f"validation command: {self.config.validation_command}")
        print(f"pre-commit command: {self.config.validation_command}")
        if only_selected is not None:
            print(f"selected file: {only_selected}")


def parse_args(argv: list[str]) -> InstallerConfig:
    """parse args operation.

    :param argv: Value for argv.

    :returns: ``InstallerConfig``.
    """

    parser = HarnessArgumentParser(
        prog="install-harness.py",
        description="Install target-owned repository harness assets.",
    )
    parser.add_argument("--mode", choices=MODES, required=True, help="Target stack mode.")
    parser.add_argument("--ci-host", choices=CI_HOSTS, required=True, help="CI host to install.")
    parser.add_argument("--target", default=os.environ.get("HARNESS_TARGET_ROOT", "."), help="Target repository root.")
    parser.add_argument("--force", action="store_true", help="Overwrite managed target files where supported.")
    parser.add_argument("--no-ci", action="store_true", help="Alias for --ci-host none.")
    action_group = parser.add_mutually_exclusive_group()
    action_group.add_argument("--preview", action="store_true", help="Print selected install set and statuses without writing.")
    action_group.add_argument("--show", metavar="PATH", help="Print rendered content for one final target-relative file without writing.")
    action_group.add_argument("--only", metavar="PATH", help="Install exactly one final target-relative file.")

    args = parser.parse_args(argv)
    ci_host = "none" if args.no_ci else args.ci_host
    if args.no_ci and args.ci_host != "none":
        fail("--no-ci cannot be combined with --ci-host other than none", exit_code=2)
    action = "install"
    selected_path: str | None = None
    if args.preview:
        action = "preview"
    elif args.show is not None:
        action = "show"
        selected_path = args.show
    elif args.only is not None:
        action = "only"
        selected_path = args.only
    return InstallerConfig(
        mode=args.mode,
        ci_host=ci_host,
        target_root=Path(args.target),
        force=args.force,
        action=action,
        selected_path=selected_path,
    )


def normalize_requested_target_path(requested_path: str) -> str:
    """normalize requested target path operation.

    :param requested_path: Value for requested_path.

    :returns: ``str``.
    """

    normalized = requested_path[2:] if requested_path.startswith("./") else requested_path
    reject_unsafe_relative_path(normalized)
    if normalized.endswith("/"):
        fail(f"unsafe target path: {normalized} (must be a file path, not a directory)")
    return normalized


def reject_unsafe_relative_path(path: str) -> None:
    """reject unsafe relative path operation.

    :param path: Value for path.

    :returns: ``None``.
    """

    unsafe_path = path[2:] if path.startswith("./") else path
    if unsafe_path in {"", "."} or unsafe_path.startswith("/"):
        fail(f"unsafe target path: {path} (must be relative, non-empty, no .. references)")
    parts = unsafe_path.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        fail(f"unsafe target path: {path} (must be relative, non-empty, no .. references)")


def ensure_safe_parent_dir(path: str) -> None:
    """ensure safe parent dir operation.

    :param path: Value for path.

    :returns: ``None``.
    """

    parent = Path(path).parent
    if str(parent) in {"", "."}:
        return
    reject_unsafe_relative_path(parent.as_posix())
    current = Path()
    for part in parent.parts:
        current = current / part
        if current.is_symlink():
            fail(f"[safe_parent] refusing symlink directory component: {current.as_posix()}")
        if current.exists() and not current.is_dir():
            fail(f"[safe_parent] parent component is not a directory: {current.as_posix()}")
    parent.mkdir(parents=True, exist_ok=True)


def ensure_safe_file_destination(path: str) -> None:
    """ensure safe file destination operation.

    :param path: Value for path.

    :returns: ``None``.
    """

    clean_path = path[2:] if path.startswith("./") else path
    reject_unsafe_relative_path(clean_path)
    ensure_safe_parent_dir(clean_path)
    target = Path(clean_path)
    if target.is_symlink():
        fail(f"[safe_destination] refusing symlink file destination: {clean_path}")
    if target.is_dir():
        fail(f"[safe_destination] refusing directory file destination: {clean_path}")


def is_common_skip_path(rel: str) -> bool:
    """is common skip path operation.

    :param rel: Value for rel.

    :returns: ``bool``.
    """
    return rel in {
        "AGENTS.md",
        "CLAUDE.md",
        "docs/harness/git-hooks/pre-commit",
        "docs/harness/git-hooks/pre-push",
    }


def is_direct_template_entry(rel: str) -> bool:
    """is direct template entry operation.

    :param rel: Value for rel.

    :returns: ``bool``.
    """

    return rel.startswith("docs/harness/templates/") and rel.count("/") == 3


def workflow_name_for_mode(mode: str) -> str:
    """workflow name for mode operation.

    :param mode: Value for mode.

    :returns: ``str``.
    """

    return {
        "gradle": "ktlint.yaml",
        "maven": "spotless.yaml",
        "uv": "ruff.yaml",
        "bun": "ultracite.yaml",
        "shell": "shellcheck.yaml",
    }[mode]


def validation_command_for_mode(mode: str) -> str:
    """validation command for mode operation.

    :param mode: Value for mode.

    :returns: ``str``.
    """

    if mode == "gradle":
        return "./gradlew ktlintCheck"
    if mode == "maven":
        return (
            'root=$(pwd -P); files=$(git ls-files -- "*.java" | while IFS= read -r file; do '
            "case \"$file\" in *,*) echo \"error: Java path contains comma and cannot be represented in spotlessFiles: $file\" >&2; exit 1;; esac; "
            "printf '%s/%s\\n' \"$root\" \"$file\" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); "
            'if [ -z "$files" ]; then ./mvnw validate; echo "spotless: no tracked Java files to check"; '
            'else ./mvnw validate -DspotlessFiles="$files"; fi'
        )
    if mode == "uv":
        return "uv run scripts/check.py"
    if mode == "bun":
        return "bun run check"
    if mode == "shell":
        return "sh scripts/check.sh"
    fail(f"[validation_command] unsupported mode (must be gradle|maven|uv|bun|shell): {mode}")


def generated_hook_text(stage: str, stage_label: str, validation_command: str) -> str:
    """generated hook text operation.

    :param stage: Value for stage.
    :param stage_label: Value for stage_label.
    :param validation_command: Value for validation_command.

    :returns: ``str``.
    """

    return (
        "#!/usr/bin/env sh\n"
        "# -*- coding: utf-8 -*-\n"
        f"# Harness generated hook: {stage}\n"
        f"# Harness stage: {stage_label}\n"
        f"# Harness validation command: {validation_command}\n"
        "set -e\n"
        "\n"
        f"{validation_command}\n"
    )


def required_selected_path(config: InstallerConfig) -> str:
    """required selected path operation.

    :param config: Value for config.

    :returns: ``str``.
    """

    if config.selected_path is None:
        fail(f"--{config.action} requires a path argument.")
    return config.selected_path


def required_src(candidate: InstallCandidate) -> Path:
    """required src operation.

    :param candidate: Value for candidate.

    :returns: ``Path``.
    """

    if candidate.src is None:
        fail(f"candidate has no source: {candidate.dst}")
    return candidate.src


def required_real_target(candidate: InstallCandidate) -> str:
    """required real target operation.

    :param candidate: Value for candidate.

    :returns: ``str``.
    """

    if candidate.real_target is None:
        fail(f"candidate has no root contract target: {candidate.dst}")
    return candidate.real_target


def read_text(path: str) -> str:
    """read text operation.

    :param path: Value for path.

    :returns: ``str``.
    """

    return Path(path).read_text(encoding="utf-8")


def fail(message: str, exit_code: int = 1) -> None:
    """fail operation.

    :param message: Value for message.
    :param exit_code: Value for exit_code.

    :returns: ``None``.
    """

    print(f"[error] {message}", file=sys.stderr)
    raise SystemExit(exit_code)


def main(argv: list[str]) -> int:
    """main operation.

    :param argv: Value for argv.

    :returns: ``int``.
    """

    config = parse_args(argv)
    HarnessInstaller(config).run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
