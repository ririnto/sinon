// -*- coding: utf-8 -*-

import {
  existsSync,
  mkdirSync,
  readFileSync,
  symlinkSync,
  writeFileSync
} from "node:fs";
import path from "node:path";

import {
  installArgs,
  readRecord,
  requireCondition,
  runScript
} from "./installer-runtime-support.js";
import type { RuntimeFixture } from "./installer-runtime-support.js";

/** Exercise symlink rejection and partial-record validation. */
export const checkSafetyScenarios = (fixture: RuntimeFixture): void => {
  const { installer, sourceArchitecture, temporaryRoot, validator } = fixture;
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
  const record = readRecord(partialTarget);
  requireCondition(
    !record.complete,
    "standalone --only record must be partial"
  );
  const partialOutput = runScript(validator, [partialTarget], false);
  requireCondition(
    partialOutput.includes("partial --only record"),
    "validator must reject an incomplete targeted record"
  );
};
