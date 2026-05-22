#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid implicit `it` lambda parameters in Kotlin.
 */
export const forbidImplicitLambdaItRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidImplicitLambdaIt;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return Array.isArray(parameters.directories) && parameters.directories.length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.forbidImplicitLambdaIt);
    const parameters = ctx.readJsonObject(section.parameters);
    const directories = ctx.readStringArray(parameters.directories);
    const suffix = typeof parameters.filenameSuffix === "string" ? parameters.filenameSuffix : ".kt";
    return directories.flatMap((directory) => {
      const [files, warnings] = ctx.walkDirectory(directory);
      return warnings.concat(
        files
          .filter((file) => file.endsWith(suffix))
          .flatMap((file) => {
            const text = ctx.read(file);
            const lines = text.split(/\r?\n/);
            return lines.flatMap((line, index) => {
              const stripped = line.replace(/"[^"\\]*(?:\\.[^"\\]*)*"/g, "").replace(/\/\/.*$/, "");
              return /\bit\b\s*\./.test(stripped) || /\bit\b\s*\}/.test(stripped) || /->\s*it\b/.test(stripped)
                ? [
                    {
                      severity: ctx.severityOf(manifest, "forbidImplicitLambdaIt"),
                      category: "forbidImplicitLambdaIt",
                      message: `Kotlin file ${file} uses implicit \`it\` lambda parameter at line ${index + 1}; use an explicit name`,
                    },
                  ]
                : [];
            });
          })
      );
    });
  }

});
