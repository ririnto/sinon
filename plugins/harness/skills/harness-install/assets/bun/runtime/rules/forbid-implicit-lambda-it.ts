#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid implicit `it` lambda parameters in Kotlin.
 */
export class ForbidImplicitLambdaItRule implements HarnessCheckRule {
  static readonly category = "forbidImplicitLambdaIt";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidImplicitLambdaIt;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return Array.isArray(parameters.directories) && parameters.directories.length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.forbidImplicitLambdaIt);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const directories = this.ctx.readStringArray(parameters.directories);
    const suffix = typeof parameters.filenameSuffix === "string" ? parameters.filenameSuffix : ".kt";
    return directories.flatMap((directory) => {
      const [files, warnings] = this.ctx.walkDirectory(directory);
      return warnings.concat(
        files
          .filter((file) => file.endsWith(suffix))
          .flatMap((file) => {
            const text = this.ctx.read(file);
            const lines = text.split(/\r?\n/);
            return lines.flatMap((line, index) => {
              const stripped = line.replace(/"[^"\\]*(?:\\.[^"\\]*)*"/g, "").replace(/\/\/.*$/, "");
              return /\bit\b\s*\./.test(stripped) || /\bit\b\s*\}/.test(stripped) || /->\s*it\b/.test(stripped)
                ? [
                    {
                      severity: this.ctx.severityOf(manifest, ForbidImplicitLambdaItRule.category),
                      category: ForbidImplicitLambdaItRule.category,
                      message: `Kotlin file ${file} uses implicit \`it\` lambda parameter at line ${index + 1}; use an explicit name`,
                    },
                  ]
                : [];
            });
          })
      );
    });
  }
}
