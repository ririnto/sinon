#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified headings in documentation files.
 */
export const requireDocHeadingsRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireDocHeadings;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return ctx.readStringArray(entry.headings).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireDocHeadings);
    const parameters = ctx.readJsonObject(section.parameters);
    const sourceCategory = typeof parameters.sourceFilesFromCategory === "string" ? parameters.sourceFilesFromCategory : "requireFilesExist";
    const requiredSection = ctx.readJsonObject(manifest[sourceCategory]);
    const requiredParameters = ctx.readJsonObject(requiredSection.parameters);
    const sourceFilter = ctx.readJsonObject(parameters.sourceFilter);
    const prefix = typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
    const suffix = typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
    const headings = ctx.readStringArray(parameters.headings);

    const allSourceFiles = ctx.readStringArray(requiredParameters.paths);
    const filteredFiles = allSourceFiles.filter(
      (f) => !prefix || (f.startsWith(prefix) && (!suffix || f.endsWith(suffix)))
    );

    return filteredFiles.flatMap((file) => {
      if (!ctx.isFile(file)) {
        return [];
      }
      const text = ctx.read(file);
      return headings.flatMap((heading) =>
        text.includes(heading)
          ? []
          : [
              {
                severity: ctx.severityOf(manifest, "requireDocHeadings"),
                category: "requireDocHeadings",
                message: `doc missing ${heading}: ${file}`,
              },
            ]
      );
    });
  }

});
