#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid scaffold/placeholder patterns in active assets.
 */
export class ForbidScaffoldLeaksRule implements HarnessCheckRule {
  static readonly category = "forbidScaffoldLeaks";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.forbidScaffoldLeaks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    const scope = this.ctx.readJsonObject(parameters.scope);
    return this.ctx.readStringArray(scope.bases).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.forbidScaffoldLeaks);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const scope = this.ctx.readJsonObject(parameters.scope);
    const bases = this.ctx.readStringArray(scope.bases);
    const excludedSubtrees = this.ctx.readStringArray(scope.excludedSubtrees);
    const extensions = this.ctx.readStringArray(scope.extensions);
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
      const [files, warnings] = this.ctx.collectFilesUnder(base);
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
          const text = this.ctx.read(file);
          return patterns.flatMap(([pattern, label]) =>
            pattern.test(text)
              ? [
                  {
                    severity: this.ctx.severityOf(manifest, ForbidScaffoldLeaksRule.category),
                    category: ForbidScaffoldLeaksRule.category,
                    message: `${label} in active asset: ${file}`,
                  },
                ]
              : []
          );
        })
      );
    });
  }
}
