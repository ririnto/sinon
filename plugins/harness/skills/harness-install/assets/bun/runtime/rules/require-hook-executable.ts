#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to be executable.
 */
export const requireHookExecutableRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.requireHookExecutable;
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
		return ctx.readStringArray(parameters.hooks).length > 0;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.requireHookExecutable).parameters,
		);
		const hooks = ctx.readStringArray(parameters.hooks);
		return hooks.flatMap((hook) => {
			if (!ctx.isFile(hook)) {
				return [];
			}
			return ctx.isExecutablePath(hook)
				? []
				: [
						{
							severity: ctx.severityOf(manifest, "requireHookExecutable"),
							category: "requireHookExecutable",
							message: `${hook} must be executable`,
						},
					];
		});
	},
});
