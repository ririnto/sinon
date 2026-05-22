#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid unchecked task lists in completed plans.
 */
export class ForbidUncheckedTasksUnderRule implements HarnessCheckRule {
  static readonly category = "forbidUncheckedTasksUnder";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidUncheckedTasksUnder;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.directory === "string";
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.forbidUncheckedTasksUnder);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const directory = typeof parameters.directory === "string" ? parameters.directory : "";
    const patternStr = typeof parameters.uncheckedTaskPattern === "string" ? parameters.uncheckedTaskPattern : "";

    if (!directory || !this.ctx.isDirectory(directory) || !patternStr) {
      return [];
    }

    let pattern: RegExp;
    try {
      pattern = new RegExp(patternStr);
    } catch {
      return [];
    }

    const [files, warnings] = this.ctx.walkDirectory(directory);
    return warnings.concat(
      files.flatMap((file) => {
        if (!file.endsWith(".md")) {
          return [];
        }
        return pattern.test(this.ctx.read(file))
          ? [
              {
                severity: this.ctx.severityOf(manifest, ForbidUncheckedTasksUnderRule.category),
                category: ForbidUncheckedTasksUnderRule.category,
                message: `completed plan has unchecked tasks: ${file}`,
              },
            ]
          : [];
      })
    );
  }
}
