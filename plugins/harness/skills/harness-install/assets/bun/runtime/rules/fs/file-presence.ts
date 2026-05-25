#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require specified files to exist.
 */
export const filePresenceRule: HarnessCheckRule = {
  category: "filePresence",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.filePresence;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = ctx.readJsonObject(
      (section as Record<string, unknown>).parameters,
    );
    return ctx.readStringArray(entry.paths).length > 0;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.filePresence).parameters,
    );
    const paths = ctx.readStringArray(parameters.paths);
    const severity = ctx.severityOf("filePresence");
    const category = "filePresence";
    return paths.flatMap((path) => {
      if (ctx.isSymlink(path) && ctx.allowedRootContractTarget(path) === null) {
        return [
          {
            severity,
            category,
            message: `symlink file is not allowed: ${path}`,
            file: path,
            startLine: 1,
            startColumn: 1,
            endLine: 1,
            endColumn: 1,
          },
        ];
      }
      if (ctx.isFile(path)) {
        return [];
      }
      return [
        {
          severity,
          category,
          message: `missing file: ${path}`,
          file: path,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
        },
      ];
    });
  },
};
