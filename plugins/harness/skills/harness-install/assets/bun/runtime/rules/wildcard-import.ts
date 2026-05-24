#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isImportDeclaration,
	isNamespaceImport,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid wildcard imports.
 */
export const wildcardImportRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "wildcardImport",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},
	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx
			.stackSources(manifest, "wildcardImport", "typescript")
			.map((file) => ({ file, text: ctx.read(file) }))
			.filter(({ text }) => text !== "")
			.flatMap(({ file, text }) => {
				let sourceFile: SourceFile;
				try {
					sourceFile = createSourceFile(
						file,
						text,
						SyntaxKind.LatestVersion,
						true,
					);
				} catch {
					return [
						{
							severity: ctx.severityOf(manifest, "wildcardImport"),
							category: "wildcardImport",
							message: `failed to parse TypeScript: ${file}`,
						},
					];
				}
				const findings: Finding[] = [];
				const visit = (node: Node): void => {
					if (
						isImportDeclaration(node) &&
						node.importClause?.namedBindings &&
						isNamespaceImport(node.importClause.namedBindings)
					) {
						const { line } = sourceFile.getLineAndCharacterOfPosition(
							node.getStart(sourceFile),
						);
						findings.push({
							severity: ctx.severityOf(manifest, "wildcardImport"),
							category: "wildcardImport",
							message: `${file}:${line + 1}: wildcard import \`import * as\` forbidden; import explicit symbols`,
						});
					}
					forEachChild(node, visit);
				};
				visit(sourceFile);
				return findings;
			});
	},
});
