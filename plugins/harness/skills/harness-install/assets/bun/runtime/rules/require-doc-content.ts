#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require specified content in documentation files.
 */
export const requireDocContentRule = (ctx: RuleContext): HarnessCheckRule => ({
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.requireDocContent;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		if (enabled === false) {
			return false;
		}
		const checks = ctx.readJsonObject(
			(section as Record<string, unknown>).parameters,
		).checks;
		return Array.isArray(checks) && checks.length > 0;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const checks = ctx.readJsonObject(
			ctx.readJsonObject(manifest.requireDocContent).parameters,
		).checks;
		if (!Array.isArray(checks)) {
			return [];
		}
		return checks
			.filter((check) => typeof check === "object" && check !== null)
			.flatMap((check) => {
				const checkObj = check as Record<string, unknown>;
				const files = ctx.readStringArray(checkObj.files);
				const containsAll = ctx.readStringArray(checkObj.containsAll);
				const failureMessage =
					typeof checkObj.failureMessage === "string"
						? checkObj.failureMessage
						: "";
				const combinedText = files.map((f) => ctx.read(f)).join("\n");
				const hasAllSubstrings = containsAll.every((substring) =>
					combinedText.includes(substring),
				);
				return !hasAllSubstrings && failureMessage
					? [
							{
								severity: ctx.severityOf(manifest, "requireDocContent"),
								category: "requireDocContent",
								message: failureMessage,
							},
						]
					: [];
			});
	},
});
