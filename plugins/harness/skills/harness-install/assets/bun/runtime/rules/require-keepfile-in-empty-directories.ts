#!/usr/bin/env bun
import { readdirSync } from "node:fs";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require .gitkeep placeholder or real files in empty directories.
 */
export const requireKeepfileInEmptyDirectoriesRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.requireKeepfileInEmptyDirectories;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		return (
			enabled !== false &&
			ctx.readStringArray(
				ctx.readJsonObject((section as Record<string, unknown>).parameters)
					.directories,
			).length > 0
		);
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.requireKeepfileInEmptyDirectories).parameters,
		);
		const directories = ctx.readStringArray(parameters.directories);
		return directories
			.filter((dir) => ctx.isDirectory(dir))
			.flatMap((dir) => {
				const realFiles = readdirSync(ctx.pathOf(dir)).filter(
					(e) => e !== ".gitkeep",
				);
				return realFiles.length === 0 && !ctx.isFile(`${dir}/.gitkeep`)
					? [
							{
								severity: ctx.severityOf(
									manifest,
									"requireKeepfileInEmptyDirectories",
								),
								category: "requireKeepfileInEmptyDirectories",
								message: `empty directory must keep placeholder or real files: ${dir}`,
							},
						]
					: [];
			});
	},
});
