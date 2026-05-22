#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require hooks to contain generated markers.
 */
export class RequireHookGeneratedMarkerRule implements HarnessCheckRule {
  static readonly category = "requireHookGeneratedMarker";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookGeneratedMarker;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return this.ctx.readStringArray(parameters.hooks).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireHookGeneratedMarker);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const hooks = this.ctx.readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const placeholderForbidden = typeof parameters.placeholderForbidden === "string" ? parameters.placeholderForbidden : "";

    return hooks.flatMap((hook) => {
      if (!this.ctx.isFile(hook)) {
        return [];
      }
      const hookName = hook.split("/").pop() ?? "";
      const marker = markerTemplate.replace("{name}", hookName);
      const text = this.ctx.read(hook);

      return [
        !text.includes(marker)
          ? {
              severity: this.ctx.severityOf(manifest, RequireHookGeneratedMarkerRule.category),
              category: RequireHookGeneratedMarkerRule.category,
              message: `${hook} must contain generated marker '${marker}'`,
            }
          : null,
        placeholderForbidden && text.includes(placeholderForbidden)
          ? {
              severity: this.ctx.severityOf(manifest, RequireHookGeneratedMarkerRule.category),
              category: RequireHookGeneratedMarkerRule.category,
              message: `${hook} still contains packaging placeholder text`,
            }
          : null,
      ].filter((f): f is Finding => f !== null);
    });
  }
}
