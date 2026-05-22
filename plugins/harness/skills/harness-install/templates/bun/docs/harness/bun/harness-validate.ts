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
    return JSON.parse(readFileSync(pathOf(MANIFEST_PATH), "utf8"));
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

  const allFindings = HARNESS_CHECKS.filter((check) => check.applies(manifest)).flatMap((check) =>
    check.validate(root, manifest)
  );

  const uniqueFindings = Array.from(
    new Map(allFindings.map((f) => [`${f.severity}|${f.category}|${f.message}`, f])).values()
  );

  const errors = uniqueFindings.filter((f) => f.severity === "ERROR");
  const warnings = uniqueFindings.filter((f) => f.severity === "WARN");
  const infos = uniqueFindings.filter((f) => f.severity === "INFO");

  errors.forEach((e) => console.error(`[ERROR] ${e.message}`));
  warnings.forEach((w) => console.error(`[WARN] ${w.message}`));
  infos.forEach((i) => console.error(`[INFO] ${i.message}`));

  if (errors.length > 0) {
    console.error("Harness validation failed");
    process.exit(1);
  }
  console.log("Harness validation passed");
}

main();
