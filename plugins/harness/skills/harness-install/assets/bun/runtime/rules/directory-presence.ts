#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require specified directories to exist.
 */
export const directoryPresenceRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "directoryPresence",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.directoryPresence;
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
			ctx.readJsonObject(manifest.directoryPresence).parameters,
		);
		const paths = ctx.readStringArray(parameters.paths);
		return paths.flatMap((path) => {
			if (ctx.isSymlink(path)) {
				return [
					{
						severity: ctx.severityOf(manifest, "directoryPresence"),
						category: "directoryPresence",
						message: `symlink directory is not allowed: ${path}`,
					},
				];
			}
			return ctx.isDirectory(path)
				? []
				: [
						{
							severity: ctx.severityOf(manifest, "directoryPresence"),
							category: "directoryPresence",
							message: `missing directory: ${path}`,
						},
					];
		});
	},
});
