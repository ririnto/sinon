#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

export const requireHookStageRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireHookStage;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    const stages = ctx.readJsonObject(parameters.stages);
    return ctx.readJsonObject(stages[STACK]).length > 0;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const parameters = ctx.readJsonObject(ctx.readJsonObject(manifest.requireHookStage).parameters);
    const hooks = ctx.readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const stagesEntry = ctx.readJsonObject(parameters.stages);
    const stackStages = ctx.readJsonObject(stagesEntry[STACK]);

    return hooks.flatMap((hook) => {
      if (!ctx.isFile(hook)) {
        return [];
      }
      const stageKey = (hook.split("/").pop() ?? "") === "pre-commit" ? "pre-commit" : "pre-push";
      const stage = typeof stackStages[stageKey] === "string" ? stackStages[stageKey] : "";
      if (!stage) {
        return [];
      }
      const marker = markerTemplate.replace("{stage}", stage);
      const text = ctx.read(hook);

      return text.includes(marker)
        ? []
        : [
            {
              severity: ctx.severityOf(manifest, "requireHookStage"),
              category: "requireHookStage",
              message: `${hook} must contain stage marker '${marker}'`,
            },
          ];
    });
  }

});
