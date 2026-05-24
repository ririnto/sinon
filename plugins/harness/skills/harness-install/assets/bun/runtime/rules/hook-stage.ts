#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

const STACK = "bun";

export const hookStageRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "hookStage",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.hookStage;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		if (enabled === false) {
			return false;
		}
		const parameters = ctx.readJsonObject(
			(section as Record<string, unknown>).parameters,
		);
		const stages = ctx.readJsonObject(parameters.stages);
		return 0 < Object.keys(ctx.readJsonObject(stages[STACK])).length;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.hookStage).parameters,
		);
		const markerTemplate =
			typeof parameters.markerTemplate === "string"
				? parameters.markerTemplate
				: "";
		const stagesEntry = ctx.readJsonObject(parameters.stages);
		const stackStages = ctx.readJsonObject(stagesEntry[STACK]);
		const configuredHooks = ctx.readStringArray(parameters.hooks);
		const hooks =
			configuredHooks.length === 0
				? Object.keys(stackStages).map((stage) => `docs/harness/git-hooks/${stage}`)
				: configuredHooks;

		return hooks.flatMap((hook) => {
			if (!ctx.isFile(hook)) {
				return [];
			}
			const stageKey =
				(hook.split("/").pop() ?? "") === "pre-commit"
					? "pre-commit"
					: "pre-push";
			const stage =
				typeof stackStages[stageKey] === "string" ? stackStages[stageKey] : "";
			if (!stage) {
				return [];
			}
			const marker = markerTemplate.replace("{stage}", stage);
			const text = ctx.read(hook);

			return text.includes(marker)
				? []
				: [
						{
							severity: ctx.severityOf(manifest, "hookStage"),
							category: "hookStage",
							message: `${hook} must contain stage marker '${marker}'`,
						},
					];
		});
	},
});
