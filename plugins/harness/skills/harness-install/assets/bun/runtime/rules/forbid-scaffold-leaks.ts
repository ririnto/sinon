#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid scaffold/placeholder patterns in active assets.
 */
export const forbidScaffoldLeaksRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidScaffoldLeaks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return enabled !== false && ctx.readStringArray(ctx.readJsonObject(ctx.readJsonObject((section as Record<string, unknown>).parameters).scope).bases).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const parameters = ctx.readJsonObject(ctx.readJsonObject(manifest.forbidScaffoldLeaks).parameters);
    const scope = ctx.readJsonObject(parameters.scope);
    const bases = ctx.readStringArray(scope.bases);
    const excludedSubtrees = ctx.readStringArray(scope.excludedSubtrees);
    const extensions = ctx.readStringArray(scope.extensions);
    const patterns: readonly [RegExp, string][] = Array.isArray(parameters.patterns)
      ? (parameters.patterns as unknown[])
          .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
          .flatMap((obj) => {
            const patternStr = typeof obj.pattern === "string" ? obj.pattern : "";
            const labelStr = typeof obj.label === "string" ? obj.label : "";
            if (!patternStr || !labelStr) {
              return [];
            }
            try {
              return [[new RegExp(patternStr), labelStr] as const];
            } catch {
              return [];
            }
          })
      : [];
    return bases.flatMap((base) => {
      const [files, warnings] = ctx.collectFilesUnder(base);
      return warnings.concat(
        files.flatMap((file) => {
          const isExcluded = excludedSubtrees.some((subtree) => file === subtree || file.startsWith(`${subtree}/`));
          const ext = /\.([a-z0-9]+)$/.exec(file)?.[1] ?? "";
          return !isExcluded && extensions.includes(ext)
            ? patterns.flatMap(([pattern, label]) =>
                pattern.test(ctx.read(file))
                  ? [
                      {
                        severity: ctx.severityOf(manifest, "forbidScaffoldLeaks"),
                        category: "forbidScaffoldLeaks",
                        message: `${label} in active asset: ${file}`,
                      },
                    ]
                  : []
              )
            : [];
        })
      );
    });
  }

});
