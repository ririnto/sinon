#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require specified files to exist.
 */
export const requireFilesExistRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireFilesExist;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return ctx.readStringArray(entry.paths).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireFilesExist);
    const parameters = ctx.readJsonObject(section.parameters);
    const paths = ctx.readStringArray(parameters.paths);
    return paths.flatMap((path) => {
      if (ctx.isSymlink(path) && ctx.allowedRootContractTarget(path) === null) {
        return [
          {
            severity: ctx.severityOf(manifest, "requireFilesExist"),
            category: "requireFilesExist",
            message: `symlink file is not allowed: ${path}`,
          },
        ];
      }
      return ctx.isFile(path)
        ? []
        : [
            {
              severity: ctx.severityOf(manifest, "requireFilesExist"),
              category: "requireFilesExist",
              message: `missing file: ${path}`,
            },
          ];
    });
  }

});
