import { existsSync } from "node:fs";
import path from "node:path";

import { validationCommandForMode } from "./commands.js";
import { installFullPlan, installOneTargetPath } from "./operations.js";
import { normalizeRequestedTargetPath, requiredSelectedPath } from "./paths.js";
import { previewInstallSet, showOneTargetPath } from "./preview.js";
import { fail } from "./types.js";
import type { InstallerConfig, Mode } from "./types.js";

type ActivationCommand = Readonly<{
  command: readonly string[];
  executable: string;
  failureLabel: string;
  successMessage: string;
}>;

const executableExists = (executable: string): boolean =>
  Bun.which(executable) !== null;

const printSummary = (
  config: InstallerConfig,
  onlySelected: null | string
): void => {
  console.log("");
  console.log(`target: ${process.cwd()}`);
  console.log(`mode: ${config.mode}`);
  console.log(`ci-host: ${config.ciHost}`);
  console.log(`validation command: ${validationCommandForMode(config.mode)}`);
  if (onlySelected !== null) {
    console.log(`selected file: ${onlySelected}`);
  }
};

const runActivationCommand = (activation: ActivationCommand): void => {
  if (!executableExists(activation.executable)) {
    console.log(
      `[warning] ${activation.executable} not in PATH; skipping ${activation.failureLabel}`
    );
    return;
  }
  const proc = Bun.spawnSync([...activation.command], {
    stderr: "pipe",
    stdout: "pipe"
  });
  if (proc.success) {
    console.log(activation.successMessage);
    return;
  }
  console.log(
    `[warning] ${activation.failureLabel} failed: ${proc.stderr.toString().trim()}`
  );
};

const activateGitHooks = (mode: Mode): void => {
  switch (mode) {
    case "gradle": {
      console.log(
        "activate git hooks: Gradle plugin creates hooks on first build"
      );
      return;
    }
    case "maven":
    case "shell": {
      runActivationCommand({
        command: ["git", "config", "core.hooksPath", ".githooks/"],
        executable: "git",
        failureLabel: "git config core.hooksPath",
        successMessage:
          "activate git hooks: git config core.hooksPath .githooks/"
      });
      return;
    }
    case "uv": {
      runActivationCommand({
        command: ["uv", "run", "pre-commit", "install"],
        executable: "uv",
        failureLabel: "pre-commit install",
        successMessage: "activate git hooks: uv run pre-commit install"
      });
      return;
    }
    case "bun": {
      runActivationCommand({
        command: ["bun", "install"],
        executable: "bun",
        failureLabel: "bun install",
        successMessage: "activate git hooks: bun install (Husky prepare)"
      });
      return;
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

const runtimeAdvisoryForMode = (mode: Mode): void => {
  switch (mode) {
    case "gradle": {
      if (!existsSync("./gradlew")) {
        console.error(
          "[advisory] ./gradlew is required before running validation; add or restore the Gradle wrapper in the target repository."
        );
      }
      console.error(
        "[advisory] Git hooks are managed by the Gradle pre-commit-git-hooks plugin; hooks are created on first build."
      );
      return;
    }
    case "maven": {
      if (!existsSync("./mvnw")) {
        console.error(
          "[advisory] ./mvnw is required before running validation; add or restore the Maven wrapper in the target repository."
        );
      }
      console.error(
        "[advisory] Git hooks use .githooks/ with core.hooksPath; run ./mvnw validate to activate."
      );
      return;
    }
    case "uv": {
      if (!executableExists("uv")) {
        console.error(
          "[advisory] uv command not found on PATH; install via the official script (`curl -LsSf https://astral.sh/uv/install.sh | sh`) or Homebrew (`brew install uv`) before running validation."
        );
      }
      console.error(
        "[advisory] Git hooks use pre-commit framework; run uv sync && uv run pre-commit install to activate."
      );
      return;
    }
    case "bun": {
      if (!executableExists("bun")) {
        console.error(
          "[advisory] bun command not found on PATH; install via the official script (`curl -fsSL https://bun.sh/install | bash`) or Homebrew (`brew install oven-sh/bun/bun`) before running validation."
        );
      }
      console.error(
        "[advisory] Git hooks use Husky; run bun install to activate (Husky runs via the prepare script)."
      );
      return;
    }
    case "shell": {
      console.error(
        "[advisory] shellcheck and shfmt are required; install them via your OS package manager (for example, `apt install shellcheck shfmt` on Debian/Ubuntu or `brew install shellcheck shfmt` on macOS) before running validation."
      );
      console.error(
        "[advisory] Git hooks use .githooks/ with core.hooksPath; run git config core.hooksPath .githooks/ to activate."
      );
      return;
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

/** Run the requested installer action from the target repository root. */
export const runInstaller = async (config: InstallerConfig): Promise<void> => {
  const targetRoot = path.resolve(config.targetRoot);
  const targetStat = await Bun.file(targetRoot)
    .stat()
    .catch(() => null);
  if (!existsSync(targetRoot) || targetStat?.isDirectory() !== true) {
    fail(`target root is not a directory: ${config.targetRoot}`);
  }
  process.chdir(targetRoot);
  switch (config.action) {
    case "preview": {
      await previewInstallSet(config);
      return;
    }
    case "show": {
      await showOneTargetPath(
        config,
        normalizeRequestedTargetPath(requiredSelectedPath(config))
      );
      return;
    }
    case "only": {
      const selectedPath = normalizeRequestedTargetPath(
        requiredSelectedPath(config)
      );
      await installOneTargetPath(config, selectedPath);
      printSummary(config, selectedPath);
      runtimeAdvisoryForMode(config.mode);
      return;
    }
    case "install": {
      await installFullPlan(config);
      activateGitHooks(config.mode);
      printSummary(config, null);
      runtimeAdvisoryForMode(config.mode);
      return;
    }
    default: {
      return fail(`unsupported action: ${config.action}`);
    }
  }
};
