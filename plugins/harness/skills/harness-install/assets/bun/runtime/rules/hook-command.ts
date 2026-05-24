#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

const STACK = "bun";

export const hookCommandRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "hookCommand",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.hookCommand;
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
		return typeof parameters.prePushHook === "string";
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.hookCommand).parameters,
		);
		const allowedCommands = ctx.readJsonObject(parameters.allowedCommands);
		const stackCommands = ctx.readStringArray(allowedCommands[STACK]);

		const prePushHook =
			typeof parameters.prePushHook === "string" ? parameters.prePushHook : "";
		if (!ctx.isFile(prePushHook)) {
			return [];
		}

		const prePushText = ctx.read(prePushHook);
		const validationCommand =
			prePushText
				.split(/\r?\n/)
				.find((line) => line.startsWith("# Harness validation command: "))
				?.replace("# Harness validation command: ", "")
				.trim() ?? "";

		return [
			validationCommand.length === 0
				? {
						severity: ctx.severityOf(manifest, "hookCommand"),
						category: "hookCommand",
						message: "pre-push hook must declare Harness validation command",
					}
				: null,
			validationCommand && !stackCommands.includes(validationCommand)
				? {
						severity: ctx.severityOf(manifest, "hookCommand"),
						category: "hookCommand",
						message: `pre-push hook declares unsupported validation command: ${validationCommand}`,
					}
				: null,
			validationCommand &&
			!prePushText.split(/\r?\n/).includes(validationCommand)
				? {
						severity: ctx.severityOf(manifest, "hookCommand"),
						category: "hookCommand",
						message: "pre-push hook must run the declared validation command",
					}
				: null,
		].filter((f): f is Finding => f !== null);
	},
});
