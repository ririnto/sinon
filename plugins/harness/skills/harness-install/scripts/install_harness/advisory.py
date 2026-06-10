from __future__ import annotations

from pathlib import Path
import shutil
import sys

from . import models


class AdvisoryMixin(models.InstallerSupport):
    def runtime_advisory_for_mode(self) -> None:

        mode = self.config.mode
        if mode == "gradle" and not Path("./gradlew").is_file():
            print(
                "[advisory] ./gradlew is required before running validation; add or restore the Gradle wrapper in the target repository.",
                file=sys.stderr,
            )
            print(
                "[advisory] Git hooks are managed by the Gradle pre-commit-git-hooks plugin; hooks are created on first build.",
                file=sys.stderr,
            )
        elif mode == "gradle":
            print(
                "[advisory] Git hooks are managed by the Gradle pre-commit-git-hooks plugin; hooks are created on first build.",
                file=sys.stderr,
            )
        elif mode == "maven" and not Path("./mvnw").is_file():
            print(
                "[advisory] ./mvnw is required before running validation; add or restore the Maven wrapper in the target repository.",
                file=sys.stderr,
            )
            print(
                "[advisory] Git hooks use .githooks/ with core.hooksPath; run ./mvnw validate to activate.",
                file=sys.stderr,
            )
        elif mode == "maven":
            print(
                "[advisory] Git hooks use .githooks/ with core.hooksPath; run ./mvnw validate to activate.",
                file=sys.stderr,
            )
        elif mode == "uv" and not shutil.which("uv"):
            print(
                "[advisory] uv command not found on PATH; install via the official script (`curl -LsSf https://astral.sh/uv/install.sh | sh`) or Homebrew (`brew install uv`) before running validation.",
                file=sys.stderr,
            )
            print(
                "[advisory] Git hooks use pre-commit framework; run uv sync && uv run pre-commit install to activate.",
                file=sys.stderr,
            )
        elif mode == "uv":
            print(
                "[advisory] Git hooks use pre-commit framework; run uv sync && uv run pre-commit install to activate.",
                file=sys.stderr,
            )
        elif mode == "bun" and not shutil.which("bun"):
            print(
                "[advisory] bun command not found on PATH; install via the official script (`curl -fsSL https://bun.sh/install | bash`) or Homebrew (`brew install oven-sh/bun/bun`) before running validation.",
                file=sys.stderr,
            )
            print(
                "[advisory] Git hooks use Husky; run bun install to activate (Husky runs via the prepare script).",
                file=sys.stderr,
            )
        elif mode == "bun":
            print(
                "[advisory] Git hooks use Husky; run bun install to activate (Husky runs via the prepare script).",
                file=sys.stderr,
            )
        elif mode == "shell":
            print(
                "[advisory] shellcheck and shfmt are required; install them via your OS package manager (for example, `apt install shellcheck shfmt` on Debian/Ubuntu or `brew install shellcheck shfmt` on macOS) before running validation.",
                file=sys.stderr,
            )
            print(
                "[advisory] Git hooks use .githooks/ with core.hooksPath; run git config core.hooksPath .githooks/ to activate.",
                file=sys.stderr,
            )

    def print_summary(self, only_selected: str | None = None) -> None:

        print("")
        print(f"harness target: {Path.cwd()}")
        print(f"harness mode: {self.config.mode}")
        print(f"ci-host: {self.config.ci_host}")
        print(f"validation command: {self.config.validation_command}")
        if only_selected is not None:
            print(f"selected file: {only_selected}")
