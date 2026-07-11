// -*- coding: utf-8 -*-

import {
  appendFileSync,
  mkdirSync,
  readFileSync,
  writeFileSync
} from "node:fs";
import path from "node:path";

import { checkTargetedPlanCompatibility } from "./installer-runtime-plan-compatibility.js";
import {
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

export type RefreshScenario = Readonly<{
  recordPath: string;
  refreshTarget: string;
  sourceArchitecture: string;
}>;

type InstalledAgentRoutingCheck = (record: RuntimeRecord) => void;

/** Exercise first install and source-refresh scenarios before workflow refresh. */
export const prepareRefreshScenario = (
  fixture: RuntimeFixture,
  requireInstalledAgentRouting: InstalledAgentRoutingCheck
): RefreshScenario => {
  const { installer, sourceArchitecture, temporaryRoot, validator } = fixture;
  const refreshTarget = path.join(temporaryRoot, "refresh-target");
  mkdirSync(refreshTarget);
  runScript(installer, installArgs(refreshTarget), true);
  let record = readRecord(refreshTarget);
  requireCondition(record.complete, "full install record must be complete");
  requireCondition(
    record.assets.length === record.expectedAssets.length,
    "complete record inventory must equal its expected plan"
  );
  requireInstalledAgentRouting(record);
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
    readFileSync(targetArchitecture, "utf-8").includes("refresh-source-v2"),
    "recorded harness ownership must permit safe source refresh"
  );
  runScript(validator, [refreshTarget], true);
  return { recordPath, refreshTarget, sourceArchitecture };
};

/** Exercise conflicts, adoption, and targeted refresh after workflow refresh. */
export const checkRefreshOwnershipScenarios = (
  fixture: RuntimeFixture,
  scenario: RefreshScenario
): void => {
  const { installer, validator } = fixture;
  const { recordPath, refreshTarget, sourceArchitecture } = scenario;
  const targetArchitecture = path.join(refreshTarget, "ARCHITECTURE.md");
  appendFileSync(targetArchitecture, "\nuser-drift\n", "utf-8");
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
  let record = readRecord(refreshTarget);
  requireOutcome(record, "ARCHITECTURE.md", "conflict", "target");
  requireCondition(
    readFileSync(targetArchitecture, "utf-8").includes("user-drift"),
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
    readFileSync(targetArchitecture, "utf-8").includes("user-drift"),
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
  checkTargetedPlanCompatibility(fixture, refreshTarget, recordPath);

  const validCompleteRecord = readFileSync(recordPath, "utf-8");
  const truncated = JSON.parse(validCompleteRecord) as RuntimeRecord;
  writeFileSync(
    recordPath,
    `${JSON.stringify({ ...truncated, assets: [requireAsset(truncated, "AGENTS.md")] }, null, 2)}\n`,
    "utf-8"
  );
  runScript(validator, [refreshTarget], false);
  writeFileSync(recordPath, validCompleteRecord, "utf-8");
  runScript(validator, [refreshTarget], true);
};
