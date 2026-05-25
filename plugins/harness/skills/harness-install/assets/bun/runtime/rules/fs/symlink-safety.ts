#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Forbid unsafe symlinks. All scanned files and directories MUST NOT be symbolic
 * links, except symlinks within parameters.allowedSymlinkPairs (root contract symlinks).
 */
export const symlinkSafetyRule: HarnessCheckRule = {
  category: "symlinkSafety",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.symlinkSafety;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return enabled !== false;
  },
  validate(ctx: RuleContext): readonly Finding[] {
    const section = ctx.manifest.raw.symlinkSafety;
    if (typeof section !== "object" || section === null) {
      return [];
    }
    const params = ctx.readJsonObject(
      (section as Record<string, unknown>).parameters,
    );
    const allowedPairsList = params.allowedSymlinkPairs;
    const allowedSet = new Set<string>();
    if (Array.isArray(allowedPairsList)) {
      for (const pairItem of allowedPairsList) {
        if (Array.isArray(pairItem) && pairItem.length >= 2) {
          allowedSet.add(JSON.stringify([String(pairItem[0]), String(pairItem[1])].sort()));
        }
      }
    }
    const messages = ctx.readJsonObject(
      (section as Record<string, unknown>).messages,
    );
    const scanRootNotAllowedMsg =
      typeof messages.scanRootNotAllowed === "string"
        ? messages.scanRootNotAllowed
        : "symlink scan root is not allowed: {path}";
    const fileNotAllowedMsg =
      typeof messages.fileNotAllowed === "string"
        ? messages.fileNotAllowed
        : "symlink file is not allowed: {path}";
    const pathNotAllowedMsg =
      typeof messages.pathNotAllowed === "string"
        ? messages.pathNotAllowed
        : "symlink path is not allowed: {path}";
    const severity = ctx.severityOf("symlinkSafety");
    const findings: Finding[] = [];
    const scanBases = [
      ".claude",
      "docs",
      ".github",
      "AGENTS.md",
      "CLAUDE.md",
      "ARCHITECTURE.md",
    ];
    for (const base of scanBases) {
      if (ctx.isSymlink(base)) {
        const target = ctx.allowedRootContractTarget(base);
        if (target === null) {
          findings.push({
            severity,
            category: "symlinkSafety",
            message: scanRootNotAllowedMsg.replace("{path}", base),
            file: base,
            startLine: 1,
            startColumn: 1,
            endLine: 1,
            endColumn: 1,
          });
        }
        continue;
      }
      if (!ctx.isDirectory(base)) {
        continue;
      }
      const [files, walkerFindings] = ctx.walkDirectory(base);
      findings.push(...walkerFindings);
      for (const file of files) {
        if (ctx.isSymlink(file)) {
          const target = ctx.allowedRootContractTarget(file);
          if (target === null) {
            const isSpecialFile =
              file.split("/").length === 2 &&
              (file === ".claude/AGENTS.md" || file === ".claude/CLAUDE.md");
            findings.push({
              severity,
              category: "symlinkSafety",
              message: isSpecialFile
                ? fileNotAllowedMsg.replace("{path}", file)
                : pathNotAllowedMsg.replace("{path}", file),
              file,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: 1,
            });
          }
        }
      }
    }
    return findings;
  },
};
