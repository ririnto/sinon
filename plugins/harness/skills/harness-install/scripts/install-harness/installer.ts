import { existsSync } from "node:fs";
import path from "node:path";

import { validationCommandForMode } from "./commands.js";
import { isExecutable, readUtf8 } from "./files.js";
import {
  adoptOneTargetPath,
  installFullPlan,
  installOneTargetPath
} from "./operations.js";
import { normalizeRequestedTargetPath, requiredSelectedPath } from "./paths.js";
import { previewInstallSet, showOneTargetPath } from "./preview.js";
import { writeInstallRecord } from "./record.js";
import { fail } from "./types.js";
import type { InstallerConfig, Mode } from "./types.js";

type HookActivation = Readonly<{
  command: readonly string[];
  executable: string;
  message: string;
}>;

const executableExists = (executable: string): boolean =>
  Bun.which(executable) !== null;

/** Clear Git state inherited from a caller before operating on an explicit target. */
const isolateTargetGitEnvironment = (): void => {
  for (const key of Object.keys(process.env)) {
    if (key.startsWith("GIT_")) {
      Reflect.deleteProperty(process.env, key);
    }
  }
};

/** Return the shared explicit Git hook activation command. */
const hookActivation = (): HookActivation => ({
  command: ["git", "config", "--local", "core.hooksPath", ".githooks/"],
  executable: "git",
  message: "git config --local core.hooksPath .githooks/"
});

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
const activateHooks = async (config: InstallerConfig): Promise<void> => {
  if (!config.activateHooks) {
    return;
  }
  const activation = hookActivation();
  if (!executableExists(activation.executable)) {
    return fail(
      `--activate-hooks: ${activation.executable} not in PATH; cannot run ${activation.message}`
    );
  }
  const expectedHeader = "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n";
  const hookStates = await Promise.all(
    [".githooks/pre-commit", ".githooks/pre-push"].map(async (hook) => ({
      content: await readUtf8(hook).catch(() => ""),
      executable: await isExecutable(hook),
      hook
    }))
  );
  for (const state of hookStates) {
    if (!state.executable) {
      return fail(
        `--activate-hooks: ${state.hook} must exist and be executable`
      );
    }
    if (!state.content.startsWith(expectedHeader)) {
      return fail(
        `--activate-hooks: ${state.hook} must use the POSIX hook header`
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
  const configured = Bun.spawnSync(
    ["git", "config", "--local", "--get", "core.hooksPath"],
    { stderr: "pipe", stdout: "pipe" }
  );
  if (
    !configured.success ||
    configured.stdout.toString().trim() !== ".githooks/"
  ) {
    return fail(
      "--activate-hooks: core.hooksPath did not persist as .githooks/"
    );
  }
  console.log(`activate git hooks: ${activation.message}`);
};

const runtimeAdvisoryForMode = (mode: Mode, hooksActive: boolean): void => {
  const activation = hookActivation();
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
    hooksActive
      ? `[advisory] Git hooks are active through: ${activation.message}`
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
  isolateTargetGitEnvironment();
  switch (config.action) {
    case "adopt": {
      if (config.force) {
        return fail("--adopt cannot be combined with --force");
      }
      const selectedPath = normalizeRequestedTargetPath(
        requiredSelectedPath(config)
      );
      const results = await adoptOneTargetPath(config, selectedPath);
      await writeInstallRecord(config, results, false);
      printSummary(config, selectedPath);
      runtimeAdvisoryForMode(config.mode, false);
      return;
    }
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
      const results = await installOneTargetPath(config, selectedPath);
      await writeInstallRecord(config, results, false);
      printSummary(config, selectedPath);
      runtimeAdvisoryForMode(config.mode, false);
      return;
    }
    case "install": {
      const results = await installFullPlan(config);
      await writeInstallRecord(config, results, true);
      await activateHooks(config);
      printSummary(config, null);
      runtimeAdvisoryForMode(config.mode, config.activateHooks);
      return;
    }
    default: {
      return fail(`unsupported action: ${config.action}`);
    }
  }
};
