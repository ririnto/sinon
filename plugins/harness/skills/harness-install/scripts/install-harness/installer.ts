import { existsSync } from "node:fs";
import path from "node:path";

import { validationCommandForMode } from "./commands.js";
import { installFullPlan, installOneTargetPath } from "./operations.js";
import { normalizeRequestedTargetPath, requiredSelectedPath } from "./paths.js";
import { previewInstallSet, showOneTargetPath } from "./preview.js";
import { writeInstallRecord } from "./record.js";
import { fail } from "./types.js";
import type { InstallerConfig, Mode } from "./types.js";

type HookActivation = Readonly<{
  command: readonly string[];
  executable: string;
  message: string;
  prepare?: readonly string[];
}>;

const executableExists = (executable: string): boolean =>
  Bun.which(executable) !== null;

/**
 * Return the explicit Git hook activation command for one stack mode.
 *
 * @param mode Selected stack mode.
 * @returns Hook activation command, executable dependency, and human message.
 */
const hookActivationForMode = (mode: Mode): HookActivation => {
  switch (mode) {
    case "gradle": {
      return {
        command: [],
        executable: "",
        message:
          "Git hooks are created by the Gradle pre-commit-git-hooks plugin on first build; no explicit activation step."
      };
    }
    case "maven":
    case "shell": {
      return {
        command: ["git", "config", "core.hooksPath", ".githooks/"],
        executable: "git",
        message: "git config core.hooksPath .githooks/"
      };
    }
    case "uv": {
      return {
        command: ["uv", "run", "pre-commit", "install"],
        executable: "uv",
        message: "uv sync && uv run pre-commit install",
        prepare: ["uv", "sync"]
      };
    }
    case "bun": {
      return {
        command: ["bun", "install"],
        executable: "bun",
        message: "bun install (Husky prepare)"
      };
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

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

/**
 * Activate Git hooks when explicitly requested; fail on any activation error.
 *
 * @param config Installer config.
 */
const activateHooks = (config: InstallerConfig): void => {
  if (!config.activateHooks) {
    return;
  }
  const activation = hookActivationForMode(config.mode);
  if (activation.command.length === 0) {
    console.log(`activate git hooks: ${activation.message}`);
    return;
  }
  if (!executableExists(activation.executable)) {
    return fail(
      `--activate-hooks: ${activation.executable} not in PATH; cannot run ${activation.message}`
    );
  }
  if (activation.prepare !== undefined) {
    const prepared = Bun.spawnSync([...activation.prepare], {
      stderr: "pipe",
      stdout: "pipe"
    });
    if (!prepared.success) {
      return fail(
        `--activate-hooks: ${activation.prepare.join(" ")} failed: ${prepared.stderr.toString().trim()}`
      );
    }
  }
  const proc = Bun.spawnSync([...activation.command], {
    stderr: "pipe",
    stdout: "pipe"
  });
  if (!proc.success) {
    return fail(
      `--activate-hooks: ${activation.message} failed: ${proc.stderr.toString().trim()}`
    );
  }
  console.log(`activate git hooks: ${activation.message}`);
};

const runtimeAdvisoryForMode = (mode: Mode): void => {
  const activation = hookActivationForMode(mode);
  switch (mode) {
    case "gradle": {
      if (!existsSync("./gradlew")) {
        console.error(
          "[advisory] ./gradlew is required before running validation; add or restore the Gradle wrapper in the target repository."
        );
      }
      break;
    }
    case "maven": {
      if (!existsSync("./mvnw")) {
        console.error(
          "[advisory] ./mvnw is required before running validation; add or restore the Maven wrapper in the target repository."
        );
      }
      break;
    }
    case "uv": {
      if (!executableExists("uv")) {
        console.error(
          "[advisory] uv command not found on PATH; install via the official script (`curl -LsSf https://astral.sh/uv/install.sh | sh`) or Homebrew (`brew install uv`) before running validation."
        );
      }
      break;
    }
    case "bun": {
      if (!executableExists("bun")) {
        console.error(
          "[advisory] bun command not found on PATH; install via the official script (`curl -fsSL https://bun.sh/install | bash`) or Homebrew (`brew install oven-sh/bun/bun`) before running validation."
        );
      }
      break;
    }
    case "shell": {
      console.error(
        "[advisory] shellcheck and shfmt are required; install them via your OS package manager (for example, `apt install shellcheck shfmt` on Debian/Ubuntu or `brew install shellcheck shfmt` on macOS) before running validation."
      );
      break;
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
  console.error(
    activation.command.length === 0
      ? `[advisory] ${activation.message}`
      : `[advisory] To activate Git hooks, run: ${activation.message}`
  );
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
      await writeInstallRecord(config);
      activateHooks(config);
      printSummary(config, null);
      runtimeAdvisoryForMode(config.mode);
      return;
    }
    default: {
      return fail(`unsupported action: ${config.action}`);
    }
  }
};
