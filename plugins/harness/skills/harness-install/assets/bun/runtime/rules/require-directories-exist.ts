#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified directories to exist.
 */
export class RequireDirectoriesExistRule implements HarnessCheckRule {
  static readonly category = "requireDirectoriesExist";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireDirectoriesExist;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(entry.paths).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireDirectoriesExist);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const paths = this.ctx.readStringArray(parameters.paths);
    return paths.flatMap((path) => {
      if (this.ctx.isSymlink(path)) {
        return [
          {
            severity: this.ctx.severityOf(manifest, RequireDirectoriesExistRule.category),
            category: RequireDirectoriesExistRule.category,
            message: `symlink directory is not allowed: ${path}`,
          },
        ];
      }
      return this.ctx.isDirectory(path)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireDirectoriesExistRule.category),
              category: RequireDirectoriesExistRule.category,
              message: `missing directory: ${path}`,
            },
          ];
    });
  }
}
