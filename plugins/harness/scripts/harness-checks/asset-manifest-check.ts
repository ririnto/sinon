// -*- coding: utf-8 -*-

import { existsSync, lstatSync } from "node:fs";
import path from "node:path";

import {
  manifestPathFor,
  readAssetManifest
} from "../../skills/harness-install/scripts/asset-manifest.js";
import { fail, requireFile } from "./check-support.js";

export const checkAssetManifest = (root: string): void => {
  const skillDir = path.join(root, "skills", "harness-install");
  const manifestPath = manifestPathFor(skillDir);
  requireFile(manifestPath);
  const assetsDir = path.join(skillDir, "assets");
  const manifest = readAssetManifest(skillDir);
  const commonAssets = manifest["common"];
  const workflowSources = [
    "WORKFLOW.md",
    "WORKFLOW.github.md",
    "WORKFLOW.gitlab.md",
    "WORKFLOW.none.md"
  ];
  if (
    commonAssets === undefined ||
    !workflowSources.every((source) => commonAssets.includes(source))
  ) {
    fail("[assetManifest] all workflow composition sources must be packaged");
  }
  for (const [subdir, entries] of Object.entries(manifest)) {
    const subdirDir = path.resolve(assetsDir, subdir);
    if (
      !subdirDir.startsWith(`${assetsDir}${path.sep}`) ||
      !existsSync(subdirDir) ||
      !lstatSync(subdirDir).isDirectory()
    ) {
      fail(`[assetManifest] invalid asset subdirectory: ${subdir}`);
    }
    for (const entry of entries) {
      const assetPath = path.resolve(subdirDir, entry);
      if (
        !assetPath.startsWith(`${subdirDir}${path.sep}`) ||
        !existsSync(assetPath) ||
        !lstatSync(assetPath).isFile()
      ) {
        fail(`[assetManifest] invalid asset path: ${subdir}/${entry}`);
      }
    }
  }
  console.error("[asset manifest] OK");
};
