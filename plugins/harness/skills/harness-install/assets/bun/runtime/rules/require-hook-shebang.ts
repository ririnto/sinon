#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require hooks to have correct shebang.
 */
export const requireHookShebangRule = (ctx: RuleContext): HarnessCheckRule => ({
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.requireHookShebang;
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

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.requireHookShebang).parameters,
		);
		const hooks = ctx.readStringArray(parameters.hooks);
		const expectedShebang =
			typeof parameters.expectedShebang === "string"
				? parameters.expectedShebang
				: "#!/usr/bin/env sh";
		return hooks.flatMap((hook) => {
			return ctx.isFile(hook) && ctx.firstLine(hook) !== expectedShebang
				? [
						{
							severity: ctx.severityOf(manifest, "requireHookShebang"),
							category: "requireHookShebang",
							message: `${hook} must start with ${expectedShebang}`,
						},
					]
				: [];
		});
	},
});
