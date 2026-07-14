import { existsSync } from "node:fs";
import path from "node:path";

import { validationCommandForMode } from "./commands.js";
import { hookActivationMessage, activateHooks } from "./hook-activation.js";
import { installFullPlan, installOneTargetPath } from "./operations.js";
import { normalizeRequestedTargetPath, requiredSelectedPath } from "./paths.js";
import { previewInstallSet, showOneTargetPath } from "./preview.js";
import { fail } from "./types.js";
import type { InstallerConfig, Mode } from "./types.js";

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

const runtimeAdvisoryForMode = (mode: Mode, hooksActive: boolean): void => {
  const activationMessage = hookActivationMessage();
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
      ? `[advisory] Git hooks are active through: ${activationMessage}`
      : `[advisory] To activate Git hooks, run: ${activationMessage}`
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
      runtimeAdvisoryForMode(config.mode, false);
      return;
    }
    case "install": {
      await installFullPlan(config);
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
