#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  FindingFix,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to be executable.
 */
export const hookExecutableRule: HarnessCheckRule = {
  category: "hookExecutable",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.hookExecutable;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject(
      (section as Record<string, unknown>).parameters,
    );
    return ctx.readStringArray(parameters.hooks).length > 0;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.hookExecutable).parameters,
    );
    const hooks = ctx.readStringArray(parameters.hooks);
    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      if (ctx.isExecutablePath(hook)) {
        return [];
      }
      return [
        {
          severity: ctx.severityOf("hookExecutable"),
          category: "hookExecutable",
          message: `${hook} must be executable`,
          file: hook,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
          fix: {
            description: `make \`${hook}\` executable`,
            safety: "safe",
            edits: [],
          },
        },
      ];
    });
  },
};
