#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified files to exist.
 */
export class RequireFilesExistRule implements HarnessCheckRule {
  static readonly category = "requireFilesExist";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireFilesExist;
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
    const section = this.ctx.readJsonObject(manifest.requireFilesExist);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const paths = this.ctx.readStringArray(parameters.paths);
    return paths.flatMap((path) => {
      if (this.ctx.isSymlink(path) && this.ctx.allowedRootContractTarget(path) === null) {
        return [
          {
            severity: this.ctx.severityOf(manifest, RequireFilesExistRule.category),
            category: RequireFilesExistRule.category,
            message: `symlink file is not allowed: ${path}`,
          },
        ];
      }
      return this.ctx.isFile(path)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireFilesExistRule.category),
              category: RequireFilesExistRule.category,
              message: `missing file: ${path}`,
            },
          ];
    });
  }
}
