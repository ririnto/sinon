#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified template groups to exist.
 */
export const requireTemplateGroupsRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireTemplateGroups;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return ctx.readStringArray(entry.groups).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireTemplateGroups);
    const parameters = ctx.readJsonObject(section.parameters);
    const targetRoot = typeof parameters.targetRoot === "string" ? parameters.targetRoot : "";
    const groups = ctx.readStringArray(parameters.groups);
    return groups.flatMap((group) => {
      const path = `${targetRoot}/${group}`;
      return ctx.isDirectory(path)
        ? []
        : [
            {
              severity: ctx.severityOf(manifest, "requireTemplateGroups"),
              category: "requireTemplateGroups",
              message: `missing template group: ${path}`,
            },
          ];
    });
  }

});
