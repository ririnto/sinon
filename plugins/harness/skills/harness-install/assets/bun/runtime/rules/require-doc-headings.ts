#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified headings in documentation files.
 */
export class RequireDocHeadingsRule implements HarnessCheckRule {
  static readonly category = "requireDocHeadings";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireDocHeadings;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(entry.headings).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireDocHeadings);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const sourceCategory = typeof parameters.sourceFilesFromCategory === "string" ? parameters.sourceFilesFromCategory : "requireFilesExist";
    const requiredSection = this.ctx.readJsonObject(manifest[sourceCategory]);
    const requiredParameters = this.ctx.readJsonObject(requiredSection.parameters);
    const sourceFilter = this.ctx.readJsonObject(parameters.sourceFilter);
    const prefix = typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
    const suffix = typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
    const headings = this.ctx.readStringArray(parameters.headings);

    const allSourceFiles = this.ctx.readStringArray(requiredParameters.paths);
    const filteredFiles = allSourceFiles.filter(
      (f) => !prefix || (f.startsWith(prefix) && (!suffix || f.endsWith(suffix)))
    );

    return filteredFiles.flatMap((file) => {
      if (!this.ctx.isFile(file)) {
        return [];
      }
      const text = this.ctx.read(file);
      return headings.flatMap((heading) =>
        text.includes(heading)
          ? []
          : [
              {
                severity: this.ctx.severityOf(manifest, RequireDocHeadingsRule.category),
                category: RequireDocHeadingsRule.category,
                message: `doc missing ${heading}: ${file}`,
              },
            ]
      );
    });
  }
}
