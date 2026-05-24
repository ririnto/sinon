#!/usr/bin/env bun
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require Kotlin files to have exactly one top-level declaration.
 */
export const kotlinTopLevelDeclarationCountRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "kotlinTopLevelDeclarationCount",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.kotlinTopLevelDeclarationCount;
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
		return (
			typeof parameters.directories === "object" &&
			parameters.directories !== null
		);
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.kotlinTopLevelDeclarationCount)
				.parameters,
		);
		const directories = Array.isArray(parameters.directories)
			? parameters.directories
			: [];
		const directoryStrs = directories.filter(
			(item): item is string => typeof item === "string",
		);
		return directoryStrs.flatMap((directory) => {
			const [files, warnings] = ctx.walkDirectory(directory);
			return warnings.concat(
				files
					.filter((file) => file.endsWith(".kt"))
					.flatMap((file) => {
						const text = ctx.read(file);
						const declRegex =
							/^(class|interface|enum class|object|data class|sealed class|val|var|fun|typealias)\s/gm;
						const matches: RegExpExecArray[] = [];
						let match: RegExpExecArray | null;
						while ((match = declRegex.exec(text)) !== null) {
							matches.push(match);
						}
						return matches.length !== 1
							? [
									{
										severity: ctx.severityOf(
											manifest,
											"kotlinTopLevelDeclarationCount",
										),
										category: "kotlinTopLevelDeclarationCount",
										message: `Kotlin file must have exactly 1 top-level declaration: ${file} (found ${matches.length})`,
									},
								]
							: [];
					}),
			);
		});
	},
});
