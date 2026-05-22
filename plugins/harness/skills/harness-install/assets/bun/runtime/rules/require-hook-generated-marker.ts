#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to contain generated markers.
 */
export const requireHookGeneratedMarkerRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookGeneratedMarker;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return enabled !== false && ctx.readStringArray(ctx.readJsonObject((section as Record<string, unknown>).parameters).hooks).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const parameters = ctx.readJsonObject(ctx.readJsonObject(manifest.requireHookGeneratedMarker).parameters);
    const hooks = ctx.readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const placeholderForbidden = typeof parameters.placeholderForbidden === "string" ? parameters.placeholderForbidden : "";
    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      const marker = markerTemplate.replace("{name}", (hook.split("/").pop() ?? ""));
      const text = ctx.read(hook);
      return [
        !text.includes(marker)
          ? {
              severity: ctx.severityOf(manifest, "requireHookGeneratedMarker"),
              category: "requireHookGeneratedMarker",
              message: `${hook} must contain generated marker '${marker}'`,
            }
          : null,
        placeholderForbidden && text.includes(placeholderForbidden)
          ? {
              severity: ctx.severityOf(manifest, "requireHookGeneratedMarker"),
              category: "requireHookGeneratedMarker",
              message: `${hook} still contains packaging placeholder text`,
            }
          : null,
      ].filter((f): f is Finding => f !== null);
    });
  }

});
