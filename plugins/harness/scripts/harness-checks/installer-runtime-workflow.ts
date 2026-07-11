// -*- coding: utf-8 -*-

import { createHash } from "node:crypto";
import { appendFileSync, readFileSync } from "node:fs";
import path from "node:path";

import {
  readRecord,
  requireAsset,
  requireCondition,
  requireOutcome,
  runScript
} from "./installer-runtime-support.js";
import type {
  ModeWorkflowContext,
  RuntimeFixture
} from "./installer-runtime-support.js";

const workflowDestinations = [
  "WORKFLOW.md",
  "WORKFLOW.github.md",
  "WORKFLOW.gitlab.md",
  "WORKFLOW.none.md"
] as const;

const expectedWorkflowContent = (commonAssets: string, name: string): string =>
  readFileSync(path.join(commonAssets, name), "utf-8");

const installerArgs = (target: string, ciHost: string): readonly string[] => [
  "--mode",
  "bun",
  "--ci-host",
  ciHost,
  "--target",
  target
];

/** Exercise each workflow file's isolated refresh, adoption, and forced replacement. */
export const checkWorkflowRefreshScenario = (
  fixture: RuntimeFixture,
  refreshTarget: string
): void => {
  const { commonAssets, installer, validator } = fixture;

  for (const workflowDestination of workflowDestinations) {
    const workflowSource = path.join(commonAssets, workflowDestination);
    const targetWorkflow = path.join(refreshTarget, workflowDestination);
    const otherWorkflows = workflowDestinations.filter(
      (destination) => destination !== workflowDestination
    );
    const stableContents = new Map(
      otherWorkflows.map((destination) => [
        destination,
        readFileSync(path.join(refreshTarget, destination), "utf-8")
      ])
    );

    appendFileSync(workflowSource, "\nworkflow-refresh-v2\n", "utf-8");
    const workflowRefreshPreview = runScript(
      installer,
      [...installerArgs(refreshTarget, "none"), "--preview"],
      true
    );
    requireCondition(
      workflowRefreshPreview.includes(`refresh owned: ${workflowDestination}`),
      `${workflowDestination} must refresh when its source changes`
    );
    requireCondition(
      otherWorkflows.every(
        (destination) =>
          !workflowRefreshPreview.includes(`refresh owned: ${destination}`)
      ),
      `${workflowDestination} refresh must not select another workflow destination`
    );
    runScript(installer, installerArgs(refreshTarget, "none"), true);
    let record = readRecord(refreshTarget);
    requireOutcome(record, workflowDestination, "updated", "harness");
    requireCondition(
      readFileSync(targetWorkflow, "utf-8") ===
        expectedWorkflowContent(commonAssets, workflowDestination),
      `${workflowDestination} refresh must write its source bytes`
    );
    requireCondition(
      [...stableContents].every(
        ([destination, content]) =>
          readFileSync(path.join(refreshTarget, destination), "utf-8") ===
          content
      ),
      `${workflowDestination} refresh must preserve the other workflow files`
    );

    appendFileSync(targetWorkflow, "\nworkflow-user-drift\n", "utf-8");
    appendFileSync(workflowSource, "\nworkflow-refresh-v3\n", "utf-8");
    runScript(installer, installerArgs(refreshTarget, "none"), true);
    record = readRecord(refreshTarget);
    requireOutcome(record, workflowDestination, "conflict", "target");
    runScript(
      installer,
      [...installerArgs(refreshTarget, "none"), "--adopt", workflowDestination],
      true
    );
    requireCondition(
      readFileSync(targetWorkflow, "utf-8").includes("workflow-user-drift"),
      `${workflowDestination} adoption must preserve target bytes`
    );
    runScript(
      installer,
      [
        ...installerArgs(refreshTarget, "none"),
        "--only",
        workflowDestination,
        "--force"
      ],
      true
    );
    record = readRecord(refreshTarget);
    requireOutcome(record, workflowDestination, "updated", "harness");
    requireCondition(
      readFileSync(targetWorkflow, "utf-8") ===
        expectedWorkflowContent(commonAssets, workflowDestination),
      `${workflowDestination} force must restore source bytes`
    );
  }

  runScript(validator, [refreshTarget], true);
};

/** Require every workflow file to install, record, display, and force independently. */
export const checkWorkflowCompositionScenario = (
  fixture: RuntimeFixture,
  context: ModeWorkflowContext
): void => {
  const { commonAssets, installer, validator } = fixture;
  const { ciHost, record, target } = context;

  for (const workflowDestination of workflowDestinations) {
    const workflowPath = path.join(target, workflowDestination);
    const expected = expectedWorkflowContent(commonAssets, workflowDestination);
    const shown = runScript(
      installer,
      [...installerArgs(target, ciHost), "--show", workflowDestination],
      true
    );
    const preview = runScript(
      installer,
      [...installerArgs(target, ciHost), "--preview"],
      true
    );
    const workflowAssets = record.assets.filter(
      (asset) => asset.path === workflowDestination
    );
    requireCondition(
      workflowAssets.length === 1 &&
        record.expectedAssets.includes(workflowDestination),
      `${ciHost} must install exactly one ${workflowDestination} destination`
    );
    requireCondition(
      readFileSync(workflowPath, "utf-8") === expected && shown === expected,
      `${ciHost} ${workflowDestination} install and show must equal source bytes`
    );
    requireCondition(
      preview.includes(
        `keep existing (matches template): ${workflowDestination}`
      ),
      `${ciHost} preview must compare ${workflowDestination} with source bytes`
    );
    runScript(
      installer,
      [
        ...installerArgs(target, ciHost),
        "--only",
        workflowDestination,
        "--force"
      ],
      true
    );
    requireCondition(
      readFileSync(workflowPath, "utf-8") === expected,
      `${ciHost} force must restore ${workflowDestination} source bytes`
    );
    const refreshedRecord = readRecord(target);
    const refreshedWorkflow = requireAsset(
      refreshedRecord,
      workflowDestination
    );
    requireOutcome(refreshedRecord, workflowDestination, "updated", "harness");
    const digest = createHash("sha256").update(expected).digest("hex");
    requireCondition(
      refreshedWorkflow.targetDigest === digest &&
        refreshedWorkflow.sourceDigest === digest,
      `${ciHost} ${workflowDestination} source and target digests must use source bytes`
    );
  }

  runScript(validator, [target], true);
};
