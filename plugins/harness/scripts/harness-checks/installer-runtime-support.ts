// -*- coding: utf-8 -*-

import {
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

export type RuntimeAsset = Readonly<{
  kind: string;
  outcome: string;
  ownership: string;
  path: string;
  sourceDigest?: string;
  targetDigest?: string;
}>;

export type RuntimeRecord = Readonly<{
  assets: readonly RuntimeAsset[];
  complete: boolean;
  expectedAssets: readonly string[];
  expectedPlanDigest: string;
  schemaVersion: number;
}>;

export type CiHost = "both" | "github" | "gitlab" | "none";

export type RuntimeFixture = Readonly<{
  commonAssets: string;
  installer: string;
  sourceArchitecture: string;
  temporaryRoot: string;
  validator: string;
}>;

export type ModeWorkflowContext = Readonly<{
  ciHost: CiHost;
  record: RuntimeRecord;
  target: string;
}>;

/** Raise one installer runtime test failure. */
export const fail = (message: string): never => {
  throw new Error(`[installer runtime] ${message}`);
};

/** Require a runtime assertion. */
export const requireCondition = (condition: boolean, message: string): void => {
  if (!condition) {
    fail(message);
  }
};

/** Return an environment that lets Git resolve state from a temporary target. */
export const targetGitEnvironment = (
  source: NodeJS.ProcessEnv = process.env
): NodeJS.ProcessEnv => {
  const environment = { ...source };
  for (const key of Object.keys(environment)) {
    if (key.startsWith("GIT_")) {
      Reflect.deleteProperty(environment, key);
    }
  }
  return environment;
};

/** Run one Bun script and enforce its expected exit result. */
export const runScript = (
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
export const runCommand = (
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
export const readRecord = (target: string): RuntimeRecord => {
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

/** Require one recorded asset by path. */
export const requireAsset = (
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
export const requireOutcome = (
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
export const installArgs = (
  target: string,
  mode = "bun"
): readonly string[] => [
  "--mode",
  mode,
  "--ci-host",
  "none",
  "--target",
  target
];

/** Initialize one temporary target as a Git repository. */
export const initializeGitTarget = (target: string): void => {
  runCommand(["git", "init", "--quiet"], target, true);
};

/** Run installer-runtime checks against an isolated copied plugin fixture. */
export const withRuntimeFixture = (
  harnessRoot: string,
  check: (fixture: RuntimeFixture) => void
): void => {
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
      { recursive: true }
    );
    requireCondition(
      !existsSync(path.join(temporaryRoot, "cache", ".git")),
      "cached plugin fixture must not contain Git metadata"
    );
    const commonAssets = path.join(installSkill, "assets", "common");
    const sourceArchitecture = path.join(commonAssets, "ARCHITECTURE.md");
    writeFileSync(
      path.join(commonAssets, "undeclared.txt"),
      "not declared\n",
      "utf-8"
    );
    check({
      commonAssets,
      installer: path.join(installSkill, "scripts", "install-harness.ts"),
      sourceArchitecture,
      temporaryRoot,
      validator: path.join(
        validateSkill,
        "scripts",
        "validate-install-record.ts"
      )
    });
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
};
