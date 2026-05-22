#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified template groups to exist.
 */
export class RequireTemplateGroupsRule implements HarnessCheckRule {
  static readonly category = "requireTemplateGroups";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireTemplateGroups;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(entry.groups).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireTemplateGroups);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const targetRoot = typeof parameters.targetRoot === "string" ? parameters.targetRoot : "";
    const groups = this.ctx.readStringArray(parameters.groups);
    return groups.flatMap((group) => {
      const path = `${targetRoot}/${group}`;
      return this.ctx.isDirectory(path)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireTemplateGroupsRule.category),
              category: RequireTemplateGroupsRule.category,
              message: `missing template group: ${path}`,
            },
          ];
    });
  }
}
