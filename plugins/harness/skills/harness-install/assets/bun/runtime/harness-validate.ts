#!/usr/bin/env bun
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { HARNESS_CHECKS } from "./harness-check";

const root = process.cwd();
const MANIFEST_PATH = "docs/harness/manifest.json";

interface Finding {
  severity: "ERROR" | "WARN" | "INFO";
  category: string;
  message: string;
}

type Manifest = Record<string, unknown>;

function pathOf(path: string): string {
  return join(root, path);
}

function loadManifest(): Manifest {
  try {
    return JSON.parse(readFileSync(pathOf(MANIFEST_PATH), "utf8")) as Manifest;
  } catch {
    return {};
  }
}

function main(): void {
  const manifest = loadManifest();
  if (!manifest || typeof manifest !== "object" || Object.keys(manifest).length === 0) {
    console.error(`[ERROR] manifest not found or invalid: ${MANIFEST_PATH}`);
    process.exit(1);
  }

  Object.keys(manifest)
    .filter((key) => !new Set<string>(HARNESS_CHECKS.map((c) => c.category)).has(key) && !new Set<string>(["name", "description", "$schema", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns"]).has(key))
    .map((key) => ({
      severity: "WARN" as const,
      category: "manifest-structure",
      message: `unknown manifest key: ${key}`,
    }))
    .forEach((f) => console.warn(`[WARN] ${f.message}`));

  const uniqueFindings = Array.from(
    new Map(HARNESS_CHECKS.filter((check) => check.applies(manifest)).flatMap((check) =>
      check.validate(root, manifest)
    ).map((f) => [`${f.severity}|${f.category}|${f.message}`, f])).values()
  );

  const errors = uniqueFindings.filter((f) => f.severity === "ERROR");
  const warnings = uniqueFindings.filter((f) => f.severity === "WARN");
  const infos = uniqueFindings.filter((f) => f.severity === "INFO");

  errors.forEach((e) => console.error(`[ERROR] ${e.message}`));
  warnings.forEach((w) => console.warn(`[WARN] ${w.message}`));
  infos.forEach((i) => console.info(`[INFO] ${i.message}`));

  if (errors.length > 0) {
    console.error("Harness validation failed");
    process.exit(1);
  }
  console.log("Harness validation passed");
}

main();
