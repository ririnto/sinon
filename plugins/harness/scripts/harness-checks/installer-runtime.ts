// -*- coding: utf-8 -*-

import {
  appendFileSync,
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

type RuntimeAsset = Readonly<{
  outcome: string;
  ownership: string;
  path: string;
}>;

type RuntimeRecord = Readonly<{
  assets: readonly RuntimeAsset[];
  complete: boolean;
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

/** Run one Bun script and enforce its expected exit result. */
const runScript = (
  scriptPath: string,
  args: readonly string[],
  expectSuccess: boolean
): string => {
  const result = Bun.spawnSync([process.execPath, scriptPath, ...args], {
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
    new Set(value.assets.map((asset) => asset.path)).size ===
      value.assets.length,
    "record asset paths must be unique"
  );
  return value;
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

    const refreshTarget = path.join(temporaryRoot, "refresh-target");
    mkdirSync(refreshTarget);
    runScript(installer, installArgs(refreshTarget), true);
    record = readRecord(refreshTarget);
    requireCondition(record.complete, "full install record must be complete");
    requireOutcome(record, "ARCHITECTURE.md", "created", "harness");
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
    for (const mode of ["gradle", "maven", "uv", "shell"]) {
      const modeTarget = path.join(temporaryRoot, `${mode}-target`);
      mkdirSync(modeTarget);
      runScript(installer, installArgs(modeTarget, mode), true);
      readRecord(modeTarget);
      runScript(validator, [modeTarget], true);
    }
    console.error("[installer runtime] OK");
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
};
