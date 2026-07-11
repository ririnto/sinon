// -*- coding: utf-8 -*-

import { chmodSync, lstatSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

import {
  installArgs,
  requireCondition,
  runScript
} from "./installer-runtime-support.js";
import type { RuntimeFixture } from "./installer-runtime-support.js";

const permissionBits = (filePath: string): number =>
  lstatSync(filePath).mode % 0o1000;

const requireMode = (filePath: string, expected: number): void => {
  requireCondition(
    permissionBits(filePath) === expected,
    `${filePath} must have mode ${expected.toString(8)}`
  );
};

/** Exercise forced replacements for regular and executable packaged assets. */
export const checkForcedReplacementPermissions = (
  fixture: RuntimeFixture
): void => {
  const { commonAssets, installer, sourceArchitecture, temporaryRoot } =
    fixture;
  const architectureTarget = path.join(
    temporaryRoot,
    "forced-permissions-architecture"
  );
  mkdirSync(architectureTarget);
  const installedArchitecture = path.join(
    architectureTarget,
    "ARCHITECTURE.md"
  );
  writeFileSync(installedArchitecture, "target-owned\n", "utf-8");
  chmodSync(installedArchitecture, 0o755);
  runScript(installer, [...installArgs(architectureTarget), "--force"], true);
  requireMode(sourceArchitecture, 0o644);
  requireMode(installedArchitecture, 0o644);

  const sourceHook = path.join(
    commonAssets,
    "..",
    "bun",
    ".githooks",
    "pre-commit"
  );
  const hookTarget = path.join(temporaryRoot, "forced-permissions-hook");
  const installedHook = path.join(hookTarget, ".githooks", "pre-commit");
  mkdirSync(path.dirname(installedHook), { recursive: true });
  writeFileSync(installedHook, "target-owned\n", "utf-8");
  chmodSync(installedHook, 0o644);
  runScript(installer, [...installArgs(hookTarget), "--force"], true);
  requireMode(sourceHook, 0o755);
  requireMode(installedHook, 0o755);
};
