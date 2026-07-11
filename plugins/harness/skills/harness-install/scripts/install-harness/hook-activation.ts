import { lstat, readFile } from "node:fs/promises";

import { isExecutable } from "./files.js";
import { checkSafeFileDestination, requiredSrc } from "./paths.js";
import { buildPlan } from "./planning.js";
import { fail } from "./types.js";
import type { InstallerConfig } from "./types.js";

type HookActivation = Readonly<{
  command: readonly string[];
  executable: string;
  message: string;
}>;

type HookSource = Readonly<{
  destination: string;
  source: string;
}>;

const hookDestinations = [
  ".githooks/pre-commit",
  ".githooks/pre-push"
] as const;

const posixHookHeader = "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n";

/** Return the shared explicit Git hook activation command. */
const hookActivation = (): HookActivation => ({
  command: ["git", "config", "--local", "core.hooksPath", ".githooks/"],
  executable: "git",
  message: "git config --local core.hooksPath .githooks/"
});

/** Return the command shown in post-install activation guidance. */
export const hookActivationMessage = (): string => hookActivation().message;

const requireRegularFile = async (
  filePath: string,
  description: string
): Promise<void> => {
  const stat = await lstat(filePath).catch(() => null);
  if (stat?.isFile() !== true) {
    fail(`--activate-hooks: ${description} must be a regular file`);
  }
};

const hookSourcesForConfig = async (
  config: InstallerConfig
): Promise<readonly HookSource[]> => {
  const candidates = await buildPlan(config);
  return hookDestinations.map((destination) => {
    const candidate = candidates.find((entry) => entry.dst === destination);
    if (candidate === undefined) {
      return fail(
        `--activate-hooks: missing selected package hook: ${destination}`
      );
    }
    return { destination, source: requiredSrc(candidate) };
  });
};

const requireMatchingHook = async (hook: HookSource): Promise<void> => {
  await requireRegularFile(hook.source, `package source ${hook.destination}`);
  await checkSafeFileDestination(hook.destination);
  await requireRegularFile(hook.destination, hook.destination);
  if (!(await isExecutable(hook.destination))) {
    fail(`--activate-hooks: ${hook.destination} must be executable`);
  }
  const [source, target] = await Promise.all([
    readFile(hook.source),
    readFile(hook.destination)
  ]);
  if (!target.toString("utf-8").startsWith(posixHookHeader)) {
    fail(
      `--activate-hooks: ${hook.destination} must use the POSIX hook header`
    );
  }
  if (!target.equals(source)) {
    fail(
      `--activate-hooks: ${hook.destination} must exactly match the selected package source`
    );
  }
};

/** Activate only selected, verified packaged hook files. */
export const activateHooks = async (config: InstallerConfig): Promise<void> => {
  if (!config.activateHooks) {
    return;
  }
  const activation = hookActivation();
  if (Bun.which(activation.executable) === null) {
    return fail(
      `--activate-hooks: ${activation.executable} not in PATH; cannot run ${activation.message}`
    );
  }
  const hooks = await hookSourcesForConfig(config);
  await Promise.all(hooks.map(requireMatchingHook));
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
