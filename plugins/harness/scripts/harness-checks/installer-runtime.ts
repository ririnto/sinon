// -*- coding: utf-8 -*-

import {
  accessSync,
  appendFileSync,
  constants,
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  symlinkSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

type RuntimeAsset = Readonly<{
  kind: string;
  outcome: string;
  ownership: string;
  path: string;
  targetDigest?: string;
}>;

type RuntimeRecord = Readonly<{
  assets: readonly RuntimeAsset[];
  complete: boolean;
  expectedAssets: readonly string[];
  expectedPlanDigest: string;
  schemaVersion: number;
}>;

/** Raise one installer runtime test failure. */
const fail = (message: string): never => {
  throw new Error(`[installer runtime] ${message}`);
};

/** Require a runtime assertion. */
const requireCondition = (condition: boolean, message: string): void => {
  if (!condition) {
    fail(message);
  }
};

/** Count exact marker occurrences in one text value. */
const countOccurrences = (content: string, marker: string): number =>
  content.split(marker).length - 1;

/** Return an environment that lets Git resolve state from a temporary target. */
const targetGitEnvironment = (): NodeJS.ProcessEnv => {
  const environment = { ...process.env };
  for (const key of Object.keys(environment)) {
    if (key.startsWith("GIT_")) {
      Reflect.deleteProperty(environment, key);
    }
  }
  return environment;
};

/** Run one Bun script and enforce its expected exit result. */
const runScript = (
  scriptPath: string,
  args: readonly string[],
  expectSuccess: boolean
): string => {
  const result = Bun.spawnSync([process.execPath, scriptPath, ...args], {
    env: targetGitEnvironment(),
    stderr: "pipe",
    stdout: "pipe"
  });
  if (result.success !== expectSuccess) {
    fail(
      `${path.basename(scriptPath)} ${args.join(" ")} expected success=${expectSuccess}; stdout=${result.stdout.toString().trim()}; stderr=${result.stderr.toString().trim()}`
    );
  }
  return `${result.stdout.toString()}${result.stderr.toString()}`;
};

/** Run one native command from a target directory. */
const runCommand = (
  command: readonly string[],
  cwd: string,
  expectSuccess: boolean
): string => {
  const result = Bun.spawnSync([...command], {
    cwd,
    env: targetGitEnvironment(),
    stderr: "pipe",
    stdout: "pipe"
  });
  if (result.success !== expectSuccess) {
    fail(
      `${command.join(" ")} expected success=${expectSuccess}; stdout=${result.stdout.toString().trim()}; stderr=${result.stderr.toString().trim()}`
    );
  }
  return `${result.stdout.toString()}${result.stderr.toString()}`;
};

/** Read one schema-v2 runtime record. */
const readRecord = (target: string): RuntimeRecord => {
  const value = JSON.parse(
    readFileSync(path.join(target, ".harness", "install-record.json"), "utf-8")
  ) as RuntimeRecord;
  requireCondition(
    value.schemaVersion === 2,
    "record must use schemaVersion 2"
  );
  requireCondition(
    Array.isArray(value.assets),
    "record assets must be an array"
  );
  requireCondition(
    Array.isArray(value.expectedAssets) && value.expectedAssets.length > 0,
    "record expectedAssets must describe the selected full plan"
  );
  requireCondition(
    typeof value.expectedPlanDigest === "string" &&
      value.expectedPlanDigest.length > 0,
    "record expectedPlanDigest must be present"
  );
  requireCondition(
    new Set(value.assets.map((asset) => asset.path)).size ===
      value.assets.length,
    "record asset paths must be unique"
  );
  return value;
};

/** Initialize one temporary target as a Git repository. */
const initializeGitTarget = (target: string): void => {
  runCommand(["git", "init", "--quiet"], target, true);
};

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

/** Require one recorded asset by path. */
const requireAsset = (
  record: RuntimeRecord,
  assetPath: string
): RuntimeAsset => {
  const asset = record.assets.find((entry) => entry.path === assetPath);
  if (asset === undefined) {
    return fail(`record is missing ${assetPath}`);
  }
  return asset;
};

/** Require one outcome and ownership pair. */
const requireOutcome = (
  record: RuntimeRecord,
  assetPath: string,
  outcome: string,
  ownership: string
): void => {
  const asset = requireAsset(record, assetPath);
  requireCondition(
    asset.outcome === outcome && asset.ownership === ownership,
    `${assetPath} expected ${outcome}/${ownership}, got ${asset.outcome}/${asset.ownership}`
  );
};

/** Build common installer arguments for one target. */
const installArgs = (target: string, mode = "bun"): readonly string[] => [
  "--mode",
  mode,
  "--ci-host",
  "none",
  "--target",
  target
];

/** Require installed general/scoped agent routing and recorded inventory. */
const requireInstalledAgentRouting = (
  target: string,
  record: RuntimeRecord
): void => {
  const agentPaths = [
    ".claude/agents/implementation.md",
    ".claude/agents/scoped-implementer.md",
    ".codex/agents/implementation.toml",
    ".codex/agents/scoped-implementer.toml"
  ] as const;
  for (const agentPath of agentPaths) {
    requireAsset(record, agentPath);
    requireCondition(
      record.expectedAssets.includes(agentPath),
      `expected plan must include ${agentPath}`
    );
  }
  const claudeImplementation = readFileSync(
    path.join(target, ".claude", "agents", "implementation.md"),
    "utf-8"
  );
  const codexImplementation = readFileSync(
    path.join(target, ".codex", "agents", "implementation.toml"),
    "utf-8"
  );
  const claudeScoped = readFileSync(
    path.join(target, ".claude", "agents", "scoped-implementer.md"),
    "utf-8"
  );
  const codexScoped = readFileSync(
    path.join(target, ".codex", "agents", "scoped-implementer.toml"),
    "utf-8"
  );
  const installedWorkflow = readFileSync(
    path.join(target, "WORKFLOW.md"),
    "utf-8"
  );
  requireCondition(
    claudeImplementation.includes("model: sonnet") &&
      claudeImplementation.includes("effort: medium") &&
      codexImplementation.includes("model = '''gpt-5.6-terra'''") &&
      codexImplementation.includes("model_reasoning_effort = '''medium'''") &&
      claudeImplementation.includes("complete affected set") &&
      codexImplementation.includes("complete affected set"),
    "general implementation agents must keep Sonnet/Terra medium routing for broad or discovered file sets"
  );
  requireCondition(
    claudeScoped.includes("model: haiku") &&
      claudeScoped.includes("effort: low") &&
      claudeScoped.includes("Do not delegate or spawn another agent") &&
      codexScoped.includes("model = '''gpt-5.6-luna'''") &&
      codexScoped.includes("model_reasoning_effort = '''low'''") &&
      codexScoped.includes("exhaustive list of every file"),
    "scoped implementer agents must keep Haiku/Luna low routing and exhaustive ownership boundaries"
  );
  requireCondition(
    installedWorkflow.includes(
      "Never send an ambiguous or incomplete file set directly to `scoped-implementer`"
    ) &&
      installedWorkflow.includes(
        "Parallel `scoped-implementer` assignments MUST have disjoint ownership lists"
      ) &&
      installedWorkflow.includes("large or cross-file changes") &&
      installedWorkflow.includes(
        "Only the user-facing top-level or root agent acts as orchestrator"
      ) &&
      !record.expectedAssets.some((asset) =>
        asset.includes("project-orchestrator")
      ),
    "installed workflow must contrast scoped and general implementation routing"
  );
};

/** Run installer and install-record adversarial scenarios from a non-Git cache. */
export const checkInstallerRuntime = (harnessRoot: string): void => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "harness-runtime-"));
  try {
    const skillsCache = path.join(temporaryRoot, "cache", "skills");
    const installSkill = path.join(skillsCache, "harness-install");
    const validateSkill = path.join(skillsCache, "harness-validate");
    mkdirSync(skillsCache, { recursive: true });
    cpSync(path.join(harnessRoot, "skills", "harness-install"), installSkill, {
      recursive: true
    });
    cpSync(
      path.join(harnessRoot, "skills", "harness-validate"),
      validateSkill,
      {
        recursive: true
      }
    );
    requireCondition(
      !existsSync(path.join(temporaryRoot, "cache", ".git")),
      "cached plugin fixture must not contain Git metadata"
    );
    const installer = path.join(installSkill, "scripts", "install-harness.ts");
    const validator = path.join(
      validateSkill,
      "scripts",
      "validate-install-record.ts"
    );
    const sourceArchitecture = path.join(
      installSkill,
      "assets",
      "common",
      "ARCHITECTURE.md"
    );
    writeFileSync(
      path.join(installSkill, "assets", "common", "undeclared.txt"),
      "not declared\n",
      "utf-8"
    );

    const preexistingTarget = path.join(temporaryRoot, "preexisting-target");
    mkdirSync(preexistingTarget);
    const preexistingArchitecture = path.join(
      preexistingTarget,
      "ARCHITECTURE.md"
    );
    writeFileSync(preexistingArchitecture, "target-owned\n", "utf-8");
    runScript(installer, installArgs(preexistingTarget), true);
    let record = readRecord(preexistingTarget);
    requireOutcome(record, "ARCHITECTURE.md", "conflict", "target");
    requireCondition(
      readFileSync(preexistingArchitecture, "utf-8") === "target-owned\n",
      "normal install must preserve a pre-existing target file"
    );
    runScript(installer, [...installArgs(preexistingTarget), "--force"], true);
    record = readRecord(preexistingTarget);
    requireOutcome(record, "ARCHITECTURE.md", "updated", "harness");
    requireCondition(
      !existsSync(path.join(preexistingTarget, "undeclared.txt")),
      "non-Git cache installs must deny undeclared assets"
    );

    const legacyCodexTarget = path.join(temporaryRoot, "legacy-codex-target");
    mkdirSync(path.join(legacyCodexTarget, ".codex"), { recursive: true });
    symlinkSync(
      "../.claude/agents",
      path.join(legacyCodexTarget, ".codex", "agents"),
      "dir"
    );
    runScript(installer, [...installArgs(legacyCodexTarget), "--force"], true);
    requireCondition(
      lstatSync(
        path.join(legacyCodexTarget, ".codex", "agents")
      ).isDirectory() &&
        existsSync(
          path.join(
            legacyCodexTarget,
            ".codex",
            "agents",
            "implementation.toml"
          )
        ) &&
        existsSync(
          path.join(
            legacyCodexTarget,
            ".codex",
            "agents",
            "scoped-implementer.toml"
          )
        ),
      "forced install must replace the legacy Codex agent symlink with a regular directory"
    );

    const identicalTarget = path.join(temporaryRoot, "identical-target");
    mkdirSync(identicalTarget);
    writeFileSync(
      path.join(identicalTarget, "ARCHITECTURE.md"),
      readFileSync(sourceArchitecture, "utf-8"),
      "utf-8"
    );
    const identicalPreview = runScript(
      installer,
      [...installArgs(identicalTarget), "--preview", "--force"],
      true
    );
    requireCondition(
      identicalPreview.includes("overwrite (--force): ARCHITECTURE.md"),
      "force preview must report the actual overwrite even for identical bytes"
    );
    runScript(installer, [...installArgs(identicalTarget), "--force"], true);
    record = readRecord(identicalTarget);
    requireOutcome(record, "ARCHITECTURE.md", "updated", "harness");

    const managedTarget = path.join(temporaryRoot, "managed-target");
    mkdirSync(managedTarget);
    writeFileSync(
      path.join(managedTarget, "AGENTS.md"),
      "# Target Notes\n\nPreserve this content.\n",
      "utf-8"
    );
    runScript(installer, [...installArgs(managedTarget), "--force"], true);
    const firstManaged = readFileSync(
      path.join(managedTarget, "AGENTS.md"),
      "utf-8"
    );
    runScript(installer, [...installArgs(managedTarget), "--force"], true);
    const secondManaged = readFileSync(
      path.join(managedTarget, "AGENTS.md"),
      "utf-8"
    );
    record = readRecord(managedTarget);
    requireOutcome(record, "AGENTS.md", "updated", "shared");
    requireCondition(
      firstManaged === secondManaged &&
        secondManaged.includes("Preserve this content.") &&
        countOccurrences(secondManaged, "<!-- harness:managed begin -->") ===
          1 &&
        countOccurrences(secondManaged, "<!-- harness:managed end -->") === 1,
      "forced managed-block refresh must be byte-idempotent and preserve target content"
    );

    const refreshTarget = path.join(temporaryRoot, "refresh-target");
    mkdirSync(refreshTarget);
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireCondition(record.complete, "full install record must be complete");
    requireCondition(
      record.assets.length === record.expectedAssets.length,
      "complete record inventory must equal its expected plan"
    );
    requireInstalledAgentRouting(refreshTarget, record);
    requireOutcome(record, "ARCHITECTURE.md", "created", "harness");
    const seed = requireAsset(record, "docs/templates/docs/AGENTS.md");
    requireCondition(
      seed.kind === "seed" && seed.ownership === "target",
      "nested shipped templates must be target-owned seeds"
    );
    const shownAgents = runScript(
      installer,
      [...installArgs(refreshTarget), "--show", "AGENTS.md"],
      true
    );
    requireCondition(
      shownAgents.includes("<!-- harness:managed begin -->") &&
        shownAgents.includes("<!-- harness:managed end -->"),
      "--show AGENTS.md must always render the managed block"
    );
    const targetArchitecture = path.join(refreshTarget, "ARCHITECTURE.md");
    appendFileSync(targetArchitecture, "\nvalidation-drift\n", "utf-8");
    const targetDriftOutput = runScript(validator, [refreshTarget], false);
    requireCondition(
      targetDriftOutput.includes("installed target drift"),
      "validator must reject harness-owned target drift"
    );
    writeFileSync(
      targetArchitecture,
      readFileSync(sourceArchitecture, "utf-8"),
      "utf-8"
    );
    runScript(validator, [refreshTarget], true);
    const recordPath = path.join(
      refreshTarget,
      ".harness",
      "install-record.json"
    );
    const validRecordText = readFileSync(recordPath, "utf-8");
    writeFileSync(
      recordPath,
      validRecordText.replace(
        '"canonicalCheckCommand": "bun run check"',
        '"canonicalCheckCommand": "bun run fix"'
      ),
      "utf-8"
    );
    const commandDriftOutput = runScript(validator, [refreshTarget], false);
    requireCondition(
      commandDriftOutput.includes("canonical check command mismatch"),
      "validator must reject recorded command drift"
    );
    writeFileSync(recordPath, validRecordText, "utf-8");
    const agentsPath = path.join(refreshTarget, "AGENTS.md");
    appendFileSync(agentsPath, "\ntarget-owned-contract-note\n", "utf-8");
    runScript(validator, [refreshTarget], true);
    writeFileSync(
      agentsPath,
      readFileSync(agentsPath, "utf-8").replace(
        "<!-- harness:managed end -->",
        "managed-drift\n<!-- harness:managed end -->"
      ),
      "utf-8"
    );
    const managedPreviewOutput = runScript(
      installer,
      [...installArgs(refreshTarget), "--preview"],
      true
    );
    requireCondition(
      managedPreviewOutput.includes("drift root contract: AGENTS.md"),
      "preview must report shared managed-block drift"
    );
    const managedDriftOutput = runScript(validator, [refreshTarget], false);
    requireCondition(
      managedDriftOutput.includes("managed block drift"),
      "validator must reject shared managed-block drift"
    );
    runScript(
      installer,
      [...installArgs(refreshTarget), "--only", "AGENTS.md", "--force"],
      true
    );
    record = readRecord(refreshTarget);
    requireOutcome(record, "AGENTS.md", "updated", "shared");
    requireCondition(
      readFileSync(agentsPath, "utf-8").includes("target-owned-contract-note"),
      "managed-block repair must preserve target-owned root-contract content"
    );
    runScript(validator, [refreshTarget], true);
    appendFileSync(sourceArchitecture, "\nrefresh-source-v2\n", "utf-8");
    const refreshPreviewOutput = runScript(
      installer,
      [...installArgs(refreshTarget), "--preview"],
      true
    );
    requireCondition(
      refreshPreviewOutput.includes("refresh owned: ARCHITECTURE.md"),
      "preview must identify a safe ownership-based refresh"
    );
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireOutcome(record, "ARCHITECTURE.md", "updated", "harness");
    requireCondition(
      readFileSync(
        path.join(refreshTarget, "ARCHITECTURE.md"),
        "utf-8"
      ).includes("refresh-source-v2"),
      "recorded harness ownership must permit safe source refresh"
    );
    runScript(validator, [refreshTarget], true);

    appendFileSync(
      path.join(refreshTarget, "ARCHITECTURE.md"),
      "\nuser-drift\n",
      "utf-8"
    );
    appendFileSync(sourceArchitecture, "\nrefresh-source-v3\n", "utf-8");
    const driftPreviewOutput = runScript(
      installer,
      [...installArgs(refreshTarget), "--preview"],
      true
    );
    requireCondition(
      driftPreviewOutput.includes("drift: ARCHITECTURE.md"),
      "preview must report target drift before refresh"
    );
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireOutcome(record, "ARCHITECTURE.md", "conflict", "target");
    requireCondition(
      readFileSync(
        path.join(refreshTarget, "ARCHITECTURE.md"),
        "utf-8"
      ).includes("user-drift"),
      "target drift must be preserved without --force"
    );
    const validationOutput = runScript(validator, [refreshTarget], false);
    requireCondition(
      validationOutput.includes("unresolved install conflict"),
      "validator must consume and reject conflict outcomes"
    );
    runScript(
      installer,
      [...installArgs(refreshTarget), "--adopt", "ARCHITECTURE.md"],
      true
    );
    record = readRecord(refreshTarget);
    requireCondition(record.complete, "adoption must preserve completeness");
    requireOutcome(record, "ARCHITECTURE.md", "kept", "target");
    runScript(validator, [refreshTarget], true);
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireOutcome(record, "ARCHITECTURE.md", "kept", "target");
    requireCondition(
      readFileSync(
        path.join(refreshTarget, "ARCHITECTURE.md"),
        "utf-8"
      ).includes("user-drift"),
      "refresh after adoption must preserve target truth"
    );
    runScript(validator, [refreshTarget], true);
    appendFileSync(targetArchitecture, "\npost-adoption-edit\n", "utf-8");
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireOutcome(record, "ARCHITECTURE.md", "kept", "target");
    requireCondition(
      readFileSync(targetArchitecture, "utf-8").includes("post-adoption-edit"),
      "later target-owned evolution must remain convergent after adoption"
    );
    runScript(validator, [refreshTarget], true);
    const assetCount = record.assets.length;
    runScript(
      installer,
      [...installArgs(refreshTarget), "--only", "ARCHITECTURE.md", "--force"],
      true
    );
    record = readRecord(refreshTarget);
    requireCondition(
      record.complete,
      "targeted refresh must preserve completeness"
    );
    requireCondition(
      record.assets.length === assetCount,
      "targeted refresh must merge without replacing the full inventory"
    );
    requireOutcome(record, "ARCHITECTURE.md", "updated", "harness");
    runScript(validator, [refreshTarget], true);

    const validCompleteRecord = readFileSync(recordPath, "utf-8");
    const truncated = JSON.parse(validCompleteRecord) as RuntimeRecord;
    writeFileSync(
      recordPath,
      `${JSON.stringify(
        { ...truncated, assets: [requireAsset(truncated, "AGENTS.md")] },
        null,
        2
      )}\n`,
      "utf-8"
    );
    runScript(validator, [refreshTarget], false);
    writeFileSync(recordPath, validCompleteRecord, "utf-8");
    runScript(validator, [refreshTarget], true);

    const symlinkPreviewTarget = path.join(
      temporaryRoot,
      "symlink-preview-target"
    );
    mkdirSync(symlinkPreviewTarget);
    const outsideArchitecture = path.join(
      temporaryRoot,
      "outside-architecture.md"
    );
    const outsideContent = readFileSync(sourceArchitecture, "utf-8");
    writeFileSync(outsideArchitecture, outsideContent, "utf-8");
    symlinkSync(
      outsideArchitecture,
      path.join(symlinkPreviewTarget, "ARCHITECTURE.md")
    );
    runScript(
      installer,
      [...installArgs(symlinkPreviewTarget), "--preview"],
      false
    );
    runScript(
      installer,
      [...installArgs(symlinkPreviewTarget), "--show", "ARCHITECTURE.md"],
      false
    );
    requireCondition(
      readFileSync(outsideArchitecture, "utf-8") === outsideContent &&
        !existsSync(path.join(symlinkPreviewTarget, "AGENTS.md")),
      "preview must fail closed without reading or writing through a destination symlink"
    );

    const partialTarget = path.join(temporaryRoot, "partial-target");
    mkdirSync(partialTarget);
    runScript(
      installer,
      [...installArgs(partialTarget), "--only", "ARCHITECTURE.md"],
      true
    );
    record = readRecord(partialTarget);
    requireCondition(
      !record.complete,
      "standalone --only record must be partial"
    );
    const partialOutput = runScript(validator, [partialTarget], false);
    requireCondition(
      partialOutput.includes("partial --only record"),
      "validator must reject an incomplete targeted record"
    );
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
      for (const ciHost of ["github", "gitlab", "both", "none"]) {
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
      }
    }
    console.error("[installer runtime] OK");
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
};
