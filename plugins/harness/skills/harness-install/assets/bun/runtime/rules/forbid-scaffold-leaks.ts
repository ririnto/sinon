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
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    const scope = ctx.readJsonObject(parameters.scope);
    return ctx.readStringArray(scope.bases).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.forbidScaffoldLeaks);
    const parameters = ctx.readJsonObject(section.parameters);
    const scope = ctx.readJsonObject(parameters.scope);
    const bases = ctx.readStringArray(scope.bases);
    const excludedSubtrees = ctx.readStringArray(scope.excludedSubtrees);
    const extensions = ctx.readStringArray(scope.extensions);
    const patternsRaw = parameters.patterns;

    const patterns: readonly [RegExp, string][] = Array.isArray(patternsRaw)
      ? patternsRaw
          .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
          .flatMap((obj) => {
            const pattern = typeof obj.pattern === "string" ? obj.pattern : "";
            const label = typeof obj.label === "string" ? obj.label : "";
            if (!pattern || !label) {
              return [];
            }
            try {
              return [[new RegExp(pattern), label] as const];
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
          if (isExcluded) {
            return [];
          }
          const extMatch = /\.([a-z0-9]+)$/.exec(file);
          const ext = extMatch ? extMatch[1] : "";
          if (!extensions.includes(ext)) {
            return [];
          }
          const text = ctx.read(file);
          return patterns.flatMap(([pattern, label]) =>
            pattern.test(text)
              ? [
                  {
                    severity: ctx.severityOf(manifest, "forbidScaffoldLeaks"),
                    category: "forbidScaffoldLeaks",
                    message: `${label} in active asset: ${file}`,
                  },
                ]
              : []
          );
        })
      );
    });
  }

});
