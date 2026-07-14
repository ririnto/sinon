// -*- coding: utf-8 -*-

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

/**
 * Return the checked-in asset manifest path for one skill directory.
 *
 * @param skillDir Harness install skill directory.
 * @returns Absolute path to the checked-in asset manifest.
 */
export const manifestPathFor = (skillDir: string): string =>
  path.join(skillDir, "asset-manifest.json");

/**
 * Read the checked-in deny-by-default asset manifest.
 *
 * @param skillDir Harness install skill directory.
 * @returns Manifest grouped by asset subdirectory.
 */
export const readAssetManifest = (
  skillDir: string
): Record<string, readonly string[]> => {
  const manifestPath = manifestPathFor(skillDir);
  if (!existsSync(manifestPath)) {
    throw new Error(`asset manifest missing: ${manifestPath}`);
  }
  const parsed: unknown = JSON.parse(readFileSync(manifestPath, "utf-8"));
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error(`asset manifest must be an object: ${manifestPath}`);
  }
  const result: Record<string, readonly string[]> = {};
  for (const [subdir, entries] of Object.entries(
    parsed as Record<string, unknown>
  )) {
    if (
      !Array.isArray(entries) ||
      !entries.every((entry) => typeof entry === "string")
    ) {
      throw new TypeError(
        `asset manifest entry must be a string array: ${subdir} in ${manifestPath}`
      );
    }
    result[subdir] = entries;
  }
  return result;
};

/**
 * Resolve the installable asset files for one asset subdirectory.
 *
 * @param skillDir Harness install skill directory.
 * @param subdir Asset subdirectory name (common, gradle, maven, uv, bun, shell).
 * @returns Absolute asset file paths declared by the manifest.
 */
export const manifestFilesForSubdir = (
  skillDir: string,
  subdir: string
): readonly string[] => {
  const manifest = readAssetManifest(skillDir);
  const entries = manifest[subdir];
  if (entries === undefined) {
    return [];
  }
  const subdirDir = path.join(skillDir, "assets", subdir);
  return entries.map((rel) => path.join(subdirDir, rel));
};
