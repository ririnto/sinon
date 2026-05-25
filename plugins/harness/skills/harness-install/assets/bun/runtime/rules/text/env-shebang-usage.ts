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
 * Require /usr/bin/env shebangs in executable scripts.
 */
export const envShebangUsageRule: HarnessCheckRule = {
  category: "envShebangUsage",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.envShebangUsage;
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
    return ctx.readStringArray(parameters.directories).length > 0;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.envShebangUsage).parameters,
    );
    const directories = ctx.readStringArray(parameters.directories);
    const expectedPrefix =
      typeof parameters.expectedPrefix === "string"
        ? parameters.expectedPrefix
        : "#!/usr/bin/env ";
    return directories.flatMap((dir) => {
      const [files, warnings] = ctx.walkDirectory(dir);
      return warnings.concat(
        files.flatMap((file) => {
          if (!ctx.isExecutablePath(file)) {
            return [];
          }
          const firstLine = ctx.firstLine(file);
          if (!firstLine.startsWith("#!") || firstLine.startsWith(expectedPrefix)) {
            return [];
          }
          return [
            {
              severity: ctx.severityOf("envShebangUsage"),
              category: "envShebangUsage",
              message: `executable script should use /usr/bin/env shebang: ${file}`,
              file,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: firstLine.length + 1,
              fix: {
                description: `replace shebang with \`${expectedPrefix}...\``,
                safety: "safe",
                edits: [{
                  file,
                  startLine: 1,
                  startColumn: 1,
                  endLine: 1,
                  endColumn: firstLine.length + 1,
                  replacement: expectedPrefix + firstLine.slice(2).replace(/^\/\S+\//, ""),
                }],
              },
            },
          ];
        }),
      );
    });
  },
};
