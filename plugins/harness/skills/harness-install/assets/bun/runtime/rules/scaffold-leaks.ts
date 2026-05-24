#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Remove Markdown code blocks and inline code spans before prose-level checks.
 */
const stripMarkdownCode = (text: string): string => {
	let inFence = false;
	let fenceMarker = "";
	return text
		.split(/\r?\n/)
		.map((line) => {
			const fenceMatch = /^( {0,3})(`{3,}|~{3,})/.exec(line);
			if (fenceMatch) {
				const marker = fenceMatch[2]?.charAt(0) ?? "";
				if (!inFence) {
					inFence = true;
					fenceMarker = marker;
				} else if (marker === fenceMarker) {
					inFence = false;
				}
				return "";
			}
			if (inFence) {
				return "";
			}
			return line.replace(/`+[^`\n]*`+/g, "");
		})
		.join("\n");
};

/**
 * Forbid scaffold/placeholder patterns in active assets.
 */
export const scaffoldLeaksRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "scaffoldLeaks",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.scaffoldLeaks;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		return (
			enabled !== false &&
			ctx.readStringArray(
				ctx.readJsonObject(
					ctx.readJsonObject((section as Record<string, unknown>).parameters)
						.scope,
				).bases,
			).length > 0
		);
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.scaffoldLeaks).parameters,
		);
		const scope = ctx.readJsonObject(parameters.scope);
		const bases = ctx.readStringArray(scope.bases);
		const excludedSubtrees = ctx.readStringArray(scope.excludedSubtrees);
		const extensions = ctx.readStringArray(scope.extensions);
		const patterns: readonly [RegExp, string][] = Array.isArray(
			parameters.patterns,
		)
			? (parameters.patterns as unknown[])
					.filter(
						(item): item is Record<string, unknown> =>
							typeof item === "object" && item !== null,
					)
					.map((obj) => ({
						patternStr: typeof obj.pattern === "string" ? obj.pattern : "",
						labelStr: typeof obj.label === "string" ? obj.label : "",
					}))
					.filter(
						({ patternStr, labelStr }) => patternStr !== "" && labelStr !== "",
					)
					.flatMap(({ patternStr, labelStr }) => {
						try {
							return [[new RegExp(patternStr), labelStr] as const];
						} catch {
							return [];
						}
					})
			: [];
		return bases.flatMap((base) => {
			const [files, warnings] = ctx.collectFilesUnder(base);
			return warnings.concat(
				files
					.filter((file) => {
						const isExcluded = excludedSubtrees.some(
							(subtree) => file === subtree || file.startsWith(`${subtree}/`),
						);
						const ext = /\.([a-z0-9]+)$/.exec(file)?.[1] ?? "";
						return !isExcluded && extensions.includes(ext);
					})
					.flatMap((file) =>
						patterns
							.filter(([pattern]) => pattern.test(stripMarkdownCode(ctx.read(file))))
							.flatMap(([, label]) => [
								{
									severity: ctx.severityOf(manifest, "scaffoldLeaks"),
									category: "scaffoldLeaks",
									message: `${label} in active asset: ${file}`,
								},
							]),
					),
			);
		});
	},
});
