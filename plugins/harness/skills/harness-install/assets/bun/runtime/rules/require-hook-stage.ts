#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

const STACK = "bun" as const;

/**
 * Require hooks to contain stage markers.
 */
export class RequireHookStageRule implements HarnessCheckRule {
  static readonly category = "requireHookStage";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookStage;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    const stages = this.ctx.readJsonObject(parameters.stages);
    return this.ctx.readJsonObject(stages[STACK]).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireHookStage);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const hooks = this.ctx.readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const stagesEntry = this.ctx.readJsonObject(parameters.stages);
    const stackStages = this.ctx.readJsonObject(stagesEntry[STACK]);

    return hooks.flatMap((hook) => {
      if (!this.ctx.isFile(hook)) {
        return [];
      }
      const hookName = hook.split("/").pop() ?? "";
      const stageKey = hookName === "pre-commit" ? "pre-commit" : "pre-push";
      const stage = typeof stackStages[stageKey] === "string" ? stackStages[stageKey] : "";
      if (!stage) {
        return [];
      }
      const marker = markerTemplate.replace("{stage}", stage);
      const text = this.ctx.read(hook);

      return text.includes(marker)
        ? []
        : [
            {
              severity: this.ctx.severityOf(manifest, RequireHookStageRule.category),
              category: RequireHookStageRule.category,
              message: `${hook} must contain stage marker '${marker}'`,
            },
          ];
    });
  }
}
