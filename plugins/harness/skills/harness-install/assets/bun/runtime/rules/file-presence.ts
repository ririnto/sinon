#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require specified files to exist.
 */
export const filePresenceRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "filePresence",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.filePresence;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		if (enabled === false) {
			return false;
		}
		const entry = ctx.readJsonObject(
			(section as Record<string, unknown>).parameters,
		);
		return ctx.readStringArray(entry.paths).length > 0;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.filePresence).parameters,
		);
		const paths = ctx.readStringArray(parameters.paths);
		return paths.flatMap((path) => {
			if (ctx.isSymlink(path) && ctx.allowedRootContractTarget(path) === null) {
				return [
					{
						severity: ctx.severityOf(manifest, "filePresence"),
						category: "filePresence",
						message: `symlink file is not allowed: ${path}`,
					},
				];
			}
			return ctx.isFile(path)
				? []
				: [
						{
							severity: ctx.severityOf(manifest, "filePresence"),
							category: "filePresence",
							message: `missing file: ${path}`,
						},
					];
		});
	},
});
