#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  FindingEdit,
  FindingFix,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to have correct shebang.
 */
export const hookShebangRule: HarnessCheckRule = {
  category: "hookShebang",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.hookShebang;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readStringArray(
        ctx.readJsonObject((section as Record<string, unknown>).parameters)
          .hooks,
      ).length > 0
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.hookShebang).parameters,
    );
    const hooks = ctx.readStringArray(parameters.hooks);
    const expectedShebang =
      typeof parameters.expectedShebang === "string"
        ? parameters.expectedShebang
        : "#!/usr/bin/env sh";
    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      const firstLine = ctx.firstLine(hook);
      if (firstLine === expectedShebang) {
        return [];
      }
      return [
        {
          severity: ctx.severityOf("hookShebang"),
          category: "hookShebang",
          message: `${hook} must start with ${expectedShebang}`,
          file: hook,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: Math.max(firstLine.length, 1) + 1,
          fix: {
            description: `insert \`${expectedShebang}\` as line 1`,
            safety: "safe",
            edits: [{
              file: hook,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: firstLine.length + 1,
              replacement: expectedShebang,
            }],
          },
        },
      ];
    });
  },
};
