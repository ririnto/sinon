// -*- coding: utf-8 -*-

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

import {
  fail,
  installArgs,
  readRecord,
  requireAsset,
  requireCondition,
  requireOutcome,
  runScript
} from "./installer-runtime-support.js";
import type {
  RuntimeFixture,
  RuntimeRecord
} from "./installer-runtime-support.js";

const forbiddenAgentDestinations = new Set([
  ".claude/agents/project-orchestrator.md",
  ".claude/agents/explorer.md",
  ".codex/agents/project-orchestrator.toml",
  ".codex/agents/explorer.toml"
]);

const validationExecutorDestinations = [
  ".claude/agents/validation-executor.md",
  ".codex/agents/validation-executor.toml"
] as const;

/** Count exact marker occurrences in one text value. */
const countOccurrences = (content: string, marker: string): number =>
  content.split(marker).length - 1;

/** Check whether a target path is a forbidden root-only agent destination. */
export const isForbiddenAgentDestination = (assetPath: string): boolean =>
  forbiddenAgentDestinations.has(assetPath);

/** Check whether a completed record has one planned and recorded executor pair. */
export const hasValidationExecutorDestinations = (
  record: RuntimeRecord
): boolean =>
  validationExecutorDestinations.every(
    (assetPath) =>
      record.assets.filter((asset) => asset.path === assetPath).length === 1 &&
      record.expectedAssets.filter((expectedPath) => expectedPath === assetPath)
        .length === 1
  );

/** Check whether a parsed JSON value is an object record. */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Check whether a parsed JSON value is a string array. */
const isStringArray = (value: unknown): value is readonly string[] =>
  Array.isArray(value) && value.every((entry) => typeof entry === "string");

/** Add the executor pair to the non-Git cache fixture's declared package plan. */
const prepareCachedValidationExecutorPlan = (commonAssets: string): void => {
  const manifestPath = path.join(
    commonAssets,
    "..",
    "..",
    "asset-manifest.json"
  );
  const value: unknown = JSON.parse(readFileSync(manifestPath, "utf-8"));
  const manifest = isRecord(value)
    ? value
    : fail("cached asset manifest must be an object");
  const commonValue = manifest["common"];
  const common = isStringArray(commonValue)
    ? commonValue
    : fail("cached asset manifest must declare common asset paths");
  writeFileSync(
    manifestPath,
    `${JSON.stringify(
      {
        ...manifest,
        common: [
          ...new Set([...common, ...validationExecutorDestinations])
        ].toSorted()
      },
      null,
      2
    )}\n`,
    "utf-8"
  );
};

/** Require the installed leaf-agent inventory without inspecting policy prose. */
export const requireInstalledAgentRouting = (record: RuntimeRecord): void => {
  const agentPaths = [
    ".claude/agents/implementer.md",
    ".claude/agents/reviewer.md",
    ".claude/agents/scoped-implementer.md",
    ".claude/agents/validation-executor.md",
    ".codex/agents/implementer.toml",
    ".codex/agents/reviewer.toml",
    ".codex/agents/scoped-implementer.toml",
    ".codex/agents/validation-executor.toml"
  ] as const;
  for (const agentPath of agentPaths) {
    requireAsset(record, agentPath);
    requireCondition(
      record.expectedAssets.includes(agentPath),
      `expected plan must include ${agentPath}`
    );
  }
  requireCondition(
    hasValidationExecutorDestinations(record),
    "completed record and expected plan must contain the validation executor pair exactly once"
  );
  requireCondition(
    !record.expectedAssets.some(isForbiddenAgentDestination),
    "installed plan must not contain root-only agent profiles"
  );
};

/** Exercise initial installation, forced replacement, and managed-block scenarios. */
export const checkInstallationScenarios = (fixture: RuntimeFixture): void => {
  const { commonAssets, installer, sourceArchitecture, temporaryRoot } =
    fixture;
  prepareCachedValidationExecutorPlan(commonAssets);

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
      countOccurrences(secondManaged, "<!-- harness:managed begin -->") === 1 &&
      countOccurrences(secondManaged, "<!-- harness:managed end -->") === 1,
    "forced managed-block refresh must be byte-idempotent and preserve target content"
  );
};
