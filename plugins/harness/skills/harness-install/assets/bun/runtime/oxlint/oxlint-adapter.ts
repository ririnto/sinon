#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { join } from "node:path";
import type { RuleContext } from "../core/rule-context";
import type { Finding } from "../rules/harness-check-rule";
import { logger } from "../logger";
import { OXLINT_CODE_TO_CATEGORY, OXLINT_FIX_SAFETY } from "./oxlint-code-map";

interface OxlintDiagnostic {
  code: string;
  message: string;
  severity: "error" | "warning";
  filename: string;
  labels: Array<{
    span: {
      offset: number;
      length: number;
      line: number;
      column: number;
    };
  }>;
}

interface OxlintOutput {
  diagnostics: OxlintDiagnostic[];
  number_of_files: number;
  number_of_rules: number;
}

/**
 * Run oxlint over target TypeScript sources and translate diagnostics to Finding[].
 * Discovers files via ctx.stackSources() to preserve source-root and symlink containment.
 * Returns empty array and logs a WARN if oxlint is unprovisioned (offline case).
 * Throws/logs ERROR if oxlint is available but config or plugin loading fails.
 */
export function runOxlint(ctx: RuleContext): Finding[] {
  const files = ctx.stackSources("greaterThanComparison");
  if (files.length === 0) {
    return [];
  }
  const configPath = join(import.meta.dir, "..", ".oxlintrc.json");
  const proc = Bun.spawnSync(
    ["bunx", "oxlint@1.68.0", "--config", configPath, "--format", "json", ...files],
    { cwd: process.cwd() },
  );
  const stdout = proc.stdout ? new TextDecoder().decode(proc.stdout) : "";
  const stderr = proc.stderr ? new TextDecoder().decode(proc.stderr) : "";
  if (proc.exitCode === 127 || stdout.includes("command not found")) {
    logger.warn("[oxlint] bunx not provisioned; skipping custom-rule detection");
    return [];
  }
  if (stderr.includes("Failed to load JS plugin")) {
    logger.error(`[oxlint] plugin load failed:\n${stderr}`);
    process.exit(1);
  }
  if (stdout.trim().length === 0) {
    if (!proc.success) {
      logger.error(`[oxlint] unexpected error:\nstdout: ${stdout}\nstderr: ${stderr}`);
      process.exit(1);
    }
    return [];
  }
  const output: OxlintOutput = JSON.parse(stdout) as OxlintOutput;
  const findings: Finding[] = [];
  output.diagnostics.forEach((diag) => {
    const category = OXLINT_CODE_TO_CATEGORY[diag.code];
    if (!category) {
      return;
    }
    if (!ctx.isEnabled(category)) {
      return;
    }
    if (category === "multilineDocStyle") {
      const categoryObj = ctx.categoryObject(category);
      const docStyleMode = categoryObj.docStyleMode ?? "multiline";
      if (docStyleMode !== "multiline") {
        return;
      }
    }
    const label = diag.labels[0];
    if (!label) {
      return;
    }
    findings.push({
      severity: ctx.severityOf(category),
      category,
      message: diag.message,
      file: diag.filename,
      startLine: label.span.line,
      startColumn: label.span.column,
      fix: {
        description: diag.message,
        safety: OXLINT_FIX_SAFETY[category],
      },
    });
  });
  return findings;
}
