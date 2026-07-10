// -*- coding: utf-8 -*-

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

const localScriptDir = import.meta.dirname;
const localSkillDir = path.join(localScriptDir, "..");

const manifestRelativePath = "skills/harness-install/asset-manifest.json";

/**
 * Return the checked-in asset manifest path for one skill directory.
 *
 * @param skillDir Harness install skill directory.
 * @returns Absolute path to the checked-in asset manifest.
 */
export const manifestPathFor = (skillDir: string): string =>
  path.join(skillDir, "asset-manifest.json");

/**
 * Group one flat list of git-tracked asset paths by top-level asset subdirectory.
 *
 * @param lines Git-tracked paths relative to the skill directory.
 * @returns Manifest grouped by asset subdirectory.
 */
const groupByAssetSubdir = (
  lines: readonly string[]
): Record<string, string[]> => {
  const grouped: Record<string, string[]> = {};
  for (const raw of lines) {
    const trimmed = raw.trim();
    if (trimmed === "") {
      continue;
    }
    const normalized = trimmed.split("\\").join("/");
    const afterAssets = normalized.startsWith("assets/")
      ? normalized.slice("assets/".length)
      : "";
    if (afterAssets === "" || afterAssets.includes("/") === false) {
      continue;
    }
    const slashIndex = afterAssets.indexOf("/");
    const subdir = afterAssets.slice(0, slashIndex);
    const rel = afterAssets.slice(slashIndex + 1);
    if (rel === "") {
      continue;
    }
    (grouped[subdir] ??= []).push(rel);
  }
  for (const subdir of Object.keys(grouped)) {
    grouped[subdir] = [...new Set(grouped[subdir])].toSorted();
  }
  return grouped;
};

/**
 * Compute the expected asset manifest from git-tracked files.
 *
 * @param skillDir Harness install skill directory.
 * @returns Manifest grouped by asset subdirectory.
 */
export const computeAssetManifest = (
  skillDir: string
): Record<string, readonly string[]> => {
  const environment = { ...process.env };
  delete environment.GIT_DIR;
  delete environment.GIT_INDEX_FILE;
  delete environment.GIT_PREFIX;
  delete environment.GIT_WORK_TREE;
  const proc = Bun.spawnSync(
    ["git", "-C", skillDir, "ls-files", "--", "assets/"],
    {
      env: environment,
      stderr: "pipe",
      stdout: "pipe"
    }
  );
  if (!proc.success) {
    throw new Error(
      `git ls-files failed for assets/: ${proc.stderr.toString().trim()}`
    );
  }
  const lines = proc.stdout.toString().split(/\r?\n/u);
  return groupByAssetSubdir(lines);
};

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
    throw new Error(
      `asset manifest missing: ${manifestPath} (run scripts/generate-asset-manifest.ts)`
    );
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

/**
 * Generate and write the asset manifest next to the skill entrypoint.
 *
 * @returns Process exit code.
 */
export const generateManifestMain = (): number => {
  const computed = computeAssetManifest(localSkillDir);
  const ordered: Record<string, readonly string[]> = {};
  for (const subdir of Object.keys(computed).toSorted()) {
    ordered[subdir] = computed[subdir];
  }
  const manifestPath = manifestPathFor(localSkillDir);
  writeFileSync(manifestPath, `${JSON.stringify(ordered, null, 2)}\n`, "utf-8");
  console.error(`wrote ${path.relative(localSkillDir, manifestPath)}`);
  console.error(
    `${manifestRelativePath}: ${Object.values(ordered).reduce(
      (total, entries) => total + entries.length,
      0
    )} asset entries across ${Object.keys(ordered).length} subdirectories`
  );
  return 0;
};
