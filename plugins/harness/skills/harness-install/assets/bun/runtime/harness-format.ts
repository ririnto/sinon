#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { existsSync, readFileSync } from "node:fs";
import { join, relative } from "node:path";
import type { HarnessManifest } from "./core/manifest";
import { createRuleContext } from "./core/rule-context";
import { HARNESS_CHECKS, MANIFEST_PATH } from "./harness-check";
import { logger } from "./logger";

const root = process.cwd();

async function main(): Promise<void> {
  const manifestPath = join(root, MANIFEST_PATH);
  if (!existsSync(manifestPath)) {
    logger.error(`failed to read ${MANIFEST_PATH}`);
    throw new Error(`${MANIFEST_PATH} not found`);
  }
  const manifest: HarnessManifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const executionContext = createRuleContext(root, manifest);
  const modified = HARNESS_CHECKS
    .filter((r) => r.applies(executionContext))
    .flatMap((rule) => typeof rule.format === "function" ? rule.format(executionContext).map((absolutePath) => relative(root, absolutePath)) : [])
    .sort();
  if (0 < modified.length) {
    logger.log(`formatted: ${modified.length}`);
    modified.forEach((path) => logger.log(`  ${path}`));
  } else {
    logger.log("no files formatted");
  }
  process.exit(0);
}

if (import.meta.main) {
  main();
}
