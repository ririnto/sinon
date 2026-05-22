#!/usr/bin/env bun
import { readdirSync } from "node:fs";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require .gitkeep placeholder or real files in empty directories.
 */
export class RequireKeepfileInEmptyDirectoriesRule implements HarnessCheckRule {
  static readonly category = "requireKeepfileInEmptyDirectories";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireKeepfileInEmptyDirectories;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(entry.directories).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireKeepfileInEmptyDirectories);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const directories = this.ctx.readStringArray(parameters.directories);
    return directories.flatMap((dir) => {
      if (!this.ctx.isDirectory(dir)) {
        return [];
      }
      const realFiles = readdirSync(this.ctx.pathOf(dir)).filter((e) => e !== ".gitkeep");
      const keepPath = `${dir}/.gitkeep`;
      return realFiles.length === 0 && !this.ctx.isFile(keepPath)
        ? [
            {
              severity: this.ctx.severityOf(manifest, RequireKeepfileInEmptyDirectoriesRule.category),
              category: RequireKeepfileInEmptyDirectoriesRule.category,
              message: `empty directory must keep placeholder or real files: ${dir}`,
            },
          ]
        : [];
    });
  }
}
