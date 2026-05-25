#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to contain generated markers.
 */
export const hookGeneratedMarkerRule: HarnessCheckRule = {
  category: "hookGeneratedMarker",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.hookGeneratedMarker;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readStringArray(
        ctx.readJsonObject((section as Record<string, unknown>).parameters)
          .hooks,
      ).length > 0
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.hookGeneratedMarker).parameters,
    );
    const hooks = ctx.readStringArray(parameters.hooks);
    const markerTemplate =
      typeof parameters.markerTemplate === "string"
        ? parameters.markerTemplate
        : "";
    const placeholderForbidden =
      typeof parameters.placeholderForbidden === "string"
        ? parameters.placeholderForbidden
        : "";
    const severity = ctx.severityOf("hookGeneratedMarker");
    const category = "hookGeneratedMarker";
    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      const marker = markerTemplate.replace(
        "{name}",
        hook.split("/").pop() ?? "",
      );
      const text = ctx.read(hook);
      const findings: Finding[] = [];
      if (!text.includes(marker)) {
        findings.push({
          severity,
          category,
          message: `${hook} must contain generated marker '${marker}'`,
          file: hook,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
        });
      }
      if (placeholderForbidden && text.includes(placeholderForbidden)) {
        findings.push({
          severity,
          category,
          message: `${hook} still contains packaging placeholder text`,
          file: hook,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
        });
      }
      return findings;
    });
  },
};
