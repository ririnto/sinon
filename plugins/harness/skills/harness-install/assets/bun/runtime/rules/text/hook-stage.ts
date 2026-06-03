#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require hooks to contain stage markers.
 */
export const hookStageRule: HarnessCheckRule = {
    category: "hookStage",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.hookStage;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        if (enabled === false) {
            return false;
        }
        const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
        return 0 < Object.keys(ctx.readJsonObject(parameters.stages)).length;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.hookStage).parameters);
        const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
        const stages = ctx.readJsonObject(parameters.stages);
        const configuredHooks = ctx.readStringArray(parameters.hooks);
        const hooks =
            configuredHooks.length === 0
                ? Object.keys(stages).map((stage) => `docs/harness/git-hooks/${stage}`)
                : configuredHooks;
        return hooks.flatMap((hook) => {
            if (!ctx.isFile(hook)) {
                return [];
            }
            const stageKey = (hook.split("/").pop() ?? "") === "pre-commit" ? "pre-commit" : "pre-push";
            const stage = typeof stages[stageKey] === "string" ? stages[stageKey] : "";
            if (!stage) {
                return [];
            }
            const marker = markerTemplate.replace("{stage}", stage);
            const text = ctx.read(hook);
            if (text.includes(marker)) {
                return [];
            }
            return [
                {
                    severity: ctx.severityOf("hookStage"),
                    category: "hookStage",
                    message: `${hook} must contain stage marker '${marker}'`,
                    file: hook,
                    startLine: 1,
                    startColumn: 1,
                    endLine: 1,
                    endColumn: 1,
                },
            ];
        });
    },
};
