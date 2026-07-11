// -*- coding: utf-8 -*-

import {
  accessSync,
  chmodSync,
  constants,
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  writeFileSync
} from "node:fs";
import path from "node:path";

import {
  fail,
  initializeGitTarget,
  installArgs,
  readRecord,
  requireCondition,
  runScript,
  targetGitEnvironment
} from "./installer-runtime-support.js";
import type {
  ModeWorkflowContext,
  RuntimeFixture
} from "./installer-runtime-support.js";

type WorkflowCompositionCheck = (context: ModeWorkflowContext) => void;

type InstalledAgentRoutingCheck = (
  record: ModeWorkflowContext["record"]
) => void;

/** Require both copied POSIX hooks and their explicit activation state. */
const requireHookState = (target: string, active: boolean): void => {
  const configured = Bun.spawnSync(
    ["git", "config", "--local", "--get", "core.hooksPath"],
    {
      cwd: target,
      env: targetGitEnvironment(),
      stderr: "pipe",
      stdout: "pipe"
    }
  );
  if (active) {
    requireCondition(
      configured.success &&
        configured.stdout.toString().trim() === ".githooks/",
      "active hooks must use the copied .githooks directory"
    );
  } else {
    requireCondition(
      !configured.success,
      "hooks must remain inactive until --activate-hooks is supplied"
    );
  }
  for (const name of ["pre-commit", "pre-push"]) {
    const hookPath = path.join(target, ".githooks", name);
    requireCondition(existsSync(hookPath), `${name} hook must be copied`);
    try {
      accessSync(hookPath, constants.X_OK);
    } catch {
      fail(`${name} hook must be executable`);
    }
    requireCondition(
      readFileSync(hookPath, "utf-8").startsWith(
        "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n"
      ),
      `${name} hook must use the POSIX header`
    );
  }
};

/** Reject conflicting executable hooks before changing the local Git hook path. */
const checkConflictingHookActivation = (
  fixture: RuntimeFixture,
  mode: string
): void => {
  const target = path.join(
    fixture.temporaryRoot,
    `${mode}-conflicting-hook-target`
  );
  mkdirSync(target);
  initializeGitTarget(target);
  runScript(fixture.installer, installArgs(target, mode), true);

  for (const name of ["pre-commit", "pre-push"]) {
    const hookPath = path.join(target, ".githooks", name);
    writeFileSync(
      hookPath,
      "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n\necho attacker-controlled\n",
      "utf-8"
    );
    chmodSync(hookPath, 0o755);
  }

  runScript(
    fixture.installer,
    [...installArgs(target, mode), "--activate-hooks"],
    false
  );
  requireHookState(target, false);

  runScript(
    fixture.installer,
    [...installArgs(target, mode), "--force", "--activate-hooks"],
    true
  );
  requireHookState(target, true);
  for (const name of ["pre-commit", "pre-push"]) {
    const source = path.join(
      fixture.commonAssets,
      "..",
      mode,
      ".githooks",
      name
    );
    const installed = path.join(target, ".githooks", name);
    requireCondition(
      readFileSync(installed).equals(readFileSync(source)),
      `${mode}/${name} force activation must restore exact packaged bytes`
    );
    requireCondition(
      lstatSync(installed).mode % 0o1000 === lstatSync(source).mode % 0o1000,
      `${mode}/${name} force activation must restore the packaged mode`
    );
  }
};

/** Exercise every stack hook lifecycle and CI host selection combination. */
export const checkModeScenarios = (
  fixture: RuntimeFixture,
  checkWorkflowComposition: WorkflowCompositionCheck,
  requireInstalledAgentRouting: InstalledAgentRoutingCheck
): void => {
  const { installer, temporaryRoot, validator } = fixture;
  const workflowNames: Readonly<Record<string, string>> = {
    bun: "ultracite.yaml",
    gradle: "ktlint.yaml",
    maven: "spotless.yaml",
    shell: "shellcheck.yaml",
    uv: "ruff.yaml"
  };
  for (const mode of ["bun", "gradle", "maven", "uv", "shell"]) {
    const hookTarget = path.join(temporaryRoot, `${mode}-hook-target`);
    mkdirSync(hookTarget);
    initializeGitTarget(hookTarget);
    runScript(installer, installArgs(hookTarget, mode), true);
    requireHookState(hookTarget, false);
    runScript(
      installer,
      [...installArgs(hookTarget, mode), "--activate-hooks"],
      true
    );
    requireHookState(hookTarget, true);
    checkConflictingHookActivation(fixture, mode);
    for (const ciHost of ["github", "gitlab", "both", "none"] as const) {
      const modeTarget = path.join(temporaryRoot, `${mode}-${ciHost}-target`);
      mkdirSync(modeTarget);
      runScript(
        installer,
        ["--mode", mode, "--ci-host", ciHost, "--target", modeTarget],
        true
      );
      const modeRecord = readRecord(modeTarget);
      requireCondition(
        modeRecord.complete &&
          modeRecord.assets.length === modeRecord.expectedAssets.length,
        `${mode}/${ciHost} must persist a complete exact inventory`
      );
      requireInstalledAgentRouting(modeRecord);
      runScript(validator, [modeTarget], true);
      const githubWorkflow = path.join(
        modeTarget,
        ".github",
        "workflows",
        workflowNames[mode] ?? "missing"
      );
      requireCondition(
        existsSync(githubWorkflow) ===
          (ciHost === "github" || ciHost === "both"),
        `${mode}/${ciHost} GitHub workflow selection must match the plan`
      );
      requireCondition(
        existsSync(path.join(modeTarget, ".gitlab-ci.yml")) ===
          (ciHost === "gitlab" || ciHost === "both"),
        `${mode}/${ciHost} GitLab workflow selection must match the plan`
      );
      if (mode === "bun") {
        checkWorkflowComposition({
          ciHost,
          record: modeRecord,
          target: modeTarget
        });
      }
    }
  }
};
