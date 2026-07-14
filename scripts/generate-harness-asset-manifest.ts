#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { existsSync, writeFileSync } from "node:fs";
import path from "node:path";

const rootDir = path.join(import.meta.dirname, "..");
const skillDir = path.join(rootDir, "plugins/harness/skills/harness-install");

/**
 * Compute the expected asset manifest from tracked, present asset files.
 *
 * @returns Manifest grouped by top-level asset directory.
 */
export const computeHarnessAssetManifest = (): Record<
  string,
  readonly string[]
> => {
  const environment = { ...process.env };
  delete environment.GIT_DIR;
  delete environment.GIT_INDEX_FILE;
  delete environment.GIT_PREFIX;
  delete environment.GIT_WORK_TREE;
  const proc = Bun.spawnSync(
    ["git", "-C", skillDir, "ls-files", "--", "assets/"],
    { env: environment, stderr: "pipe", stdout: "pipe" }
  );
  if (!proc.success) {
    throw new Error(
      `git ls-files failed for assets/: ${proc.stderr.toString().trim()}`
    );
  }
  const grouped: Record<string, string[]> = {};
  for (const entry of proc.stdout.toString().split(/\r?\n/u)) {
    const relativePath = entry.trim();
    if (
      relativePath === "" ||
      !relativePath.startsWith("assets/") ||
      !existsSync(path.join(skillDir, relativePath))
    ) {
      continue;
    }
    const [, subdir, ...parts] = relativePath.split("/");
    if (subdir === undefined || parts.length === 0) {
      continue;
    }
    (grouped[subdir] ??= []).push(parts.join("/"));
  }
  return Object.fromEntries(
    Object.entries(grouped)
      .toSorted(([left], [right]) => left.localeCompare(right))
      .map(([subdir, entries]) => [subdir, [...new Set(entries)].toSorted()])
  );
};

/** Write the deterministic checked-in Harness asset manifest. */
export const generateHarnessAssetManifest = (): void => {
  const manifest = computeHarnessAssetManifest();
  const manifestPath = path.join(skillDir, "asset-manifest.json");
  writeFileSync(
    manifestPath,
    `${JSON.stringify(manifest, null, 2)}\n`,
    "utf-8"
  );
  console.error(`wrote ${path.relative(rootDir, manifestPath)}`);
};

if (import.meta.main) {
  generateHarnessAssetManifest();
}
