#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import path from "node:path";

import { checkAssetManifest } from "./harness-checks/asset-manifest-check.js";
import { requireFile } from "./harness-checks/check-support.js";
import { checkCommonAssets } from "./harness-checks/common-assets.js";
import {
  checkInstallerContract,
  checkInstallerSurface,
  checkInstallerSecurityContract,
  checkRepositoryScripts
} from "./harness-checks/installer-assets.js";
import { checkInstallerRuntime } from "./harness-checks/installer-runtime.js";
import { checkMavenAssets } from "./harness-checks/maven-assets.js";
import { checkNativeTools } from "./harness-checks/native-tools.js";
import { checkPackageSurface } from "./harness-checks/package-surface.js";
import {
  checkBunAssets,
  checkGradleAssets,
  checkShellAssets
} from "./harness-checks/stack-assets.js";
import { checkStackCommonAssets } from "./harness-checks/stack-common-assets.js";
import { checkUvAssets } from "./harness-checks/uv-assets.js";

const main = (): number => {
  const root = Bun.argv[2] ?? path.resolve(import.meta.dirname, "..");
  try {
    console.error("Validating harness plugin native-lint end-state...");
    checkCommonAssets(root);
    checkStackCommonAssets(root);
    checkGradleAssets(root);
    checkMavenAssets(root);
    checkBunAssets(root);
    checkUvAssets(root);
    checkShellAssets(root);
    checkRepositoryScripts(root);
    checkPackageSurface(root);
    checkInstallerSurface(root);
    checkInstallerContract(root);
    checkInstallerSecurityContract(root);
    checkAssetManifest(root);
    checkInstallerRuntime(root);
    requireFile(path.join(root, "scripts", "plugin-self-check.ts"));
    checkNativeTools();
    console.error("\nHarness asset/package smoke checks passed.");
    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return 1;
  }
};

process.exit(main());
