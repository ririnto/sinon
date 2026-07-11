// -*- coding: utf-8 -*-

import { appendFileSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

import {
  installArgs,
  requireCondition,
  runScript
} from "./installer-runtime-support.js";
import type { RuntimeFixture } from "./installer-runtime-support.js";

/** Require a stale targeted plan to fail before changing target or record bytes. */
export const checkTargetedPlanCompatibility = (
  fixture: RuntimeFixture,
  refreshTarget: string,
  recordPath: string
): void => {
  const { commonAssets, installer, sourceArchitecture } = fixture;
  const targetArchitecture = path.join(refreshTarget, "ARCHITECTURE.md");
  const manifestPath = path.resolve(
    commonAssets,
    "..",
    "..",
    "asset-manifest.json"
  );
  const validManifest = readFileSync(manifestPath, "utf-8");
  const validArchitecture = readFileSync(sourceArchitecture, "utf-8");
  const validTargetArchitecture = readFileSync(targetArchitecture, "utf-8");
  const validTargetRecord = readFileSync(recordPath, "utf-8");
  writeFileSync(
    manifestPath,
    validManifest.replace('"common": [', '"common": [\n    "undeclared.txt",'),
    "utf-8"
  );
  appendFileSync(sourceArchitecture, "\nrejected-targeted-refresh\n", "utf-8");

  const output = runScript(
    installer,
    [...installArgs(refreshTarget), "--only", "ARCHITECTURE.md", "--force"],
    false
  );
  requireCondition(
    output.includes("selected install plan changed"),
    "targeted refresh must reject a changed install plan"
  );
  requireCondition(
    readFileSync(targetArchitecture, "utf-8") === validTargetArchitecture,
    "changed-plan rejection must preserve target bytes"
  );
  requireCondition(
    readFileSync(recordPath, "utf-8") === validTargetRecord,
    "changed-plan rejection must preserve record bytes"
  );
  writeFileSync(manifestPath, validManifest, "utf-8");
  writeFileSync(sourceArchitecture, validArchitecture, "utf-8");
};
