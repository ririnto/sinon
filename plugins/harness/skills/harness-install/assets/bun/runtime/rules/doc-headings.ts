#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require specified headings in documentation files.
 */
export const docHeadingsRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "docHeadings",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.docHeadings;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		return (
			enabled !== false &&
			ctx.readStringArray(
				ctx.readJsonObject((section as Record<string, unknown>).parameters)
					.headings,
			).length > 0
		);
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.docHeadings).parameters,
		);
		const sourceCategory =
			typeof parameters.sourceFilesFromCategory === "string"
				? parameters.sourceFilesFromCategory
				: "filePresence";
		const sourceFilter = ctx.readJsonObject(parameters.sourceFilter);
		const prefix =
			typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
		const suffix =
			typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
		const headings = ctx.readStringArray(parameters.headings);
		const filteredFiles = ctx
			.readStringArray(
				ctx.readJsonObject(
					ctx.readJsonObject(manifest[sourceCategory]).parameters,
				).paths,
			)
			.filter(
				(f) =>
					!prefix || (f.startsWith(prefix) && (!suffix || f.endsWith(suffix))),
			);
		return filteredFiles
			.filter((file) => ctx.isFile(file))
			.flatMap((file) => {
				const text = ctx.read(file);
				return headings
					.filter((heading) => !text.includes(heading))
					.flatMap((heading) => [
						{
							severity: ctx.severityOf(manifest, "docHeadings"),
							category: "docHeadings",
							message: `doc missing ${heading}: ${file}`,
						},
					]);
			});
	},
});
