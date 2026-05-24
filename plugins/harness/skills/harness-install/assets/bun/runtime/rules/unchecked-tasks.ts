#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid unchecked task lists in completed plans.
 */
export const uncheckedTasksRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "uncheckedTasks",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.uncheckedTasks;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		return (
			enabled !== false &&
			typeof ctx.readJsonObject((section as Record<string, unknown>).parameters)
				.directory === "string"
		);
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.uncheckedTasks).parameters,
		);
		const directory =
			typeof parameters.directory === "string" ? parameters.directory : "";
		const patternStr =
			typeof parameters.uncheckedTaskPattern === "string"
				? parameters.uncheckedTaskPattern
				: "";
		if (!directory || !ctx.isDirectory(directory) || !patternStr) {
			return [];
		}
		let pattern: RegExp;
		try {
			pattern = new RegExp(patternStr);
		} catch {
			return [];
		}
		const [files, warnings] = ctx.walkDirectory(directory);
		return warnings.concat(
			files
				.filter((file) => file.endsWith(".md") && pattern.test(ctx.read(file)))
				.flatMap((file) => [
					{
						severity: ctx.severityOf(manifest, "uncheckedTasks"),
						category: "uncheckedTasks",
						message: `completed plan has unchecked tasks: ${file}`,
					},
				]),
		);
	},
});
