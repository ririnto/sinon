#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isBinaryExpression,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid greater-than comparisons in TypeScript.
 */
export const greaterThanComparisonRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "greaterThanComparison",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},
	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx
			.stackSources(manifest, "greaterThanComparison", "typescript")
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
							severity: ctx.severityOf(manifest, "greaterThanComparison"),
							category: "greaterThanComparison",
							message: `failed to parse TypeScript: ${file}`,
						},
					];
				}
				const findings: Finding[] = [];
				const visit = (node: Node): void => {
					if (
						isBinaryExpression(node) &&
						(node.operatorToken.kind === SyntaxKind.GreaterThanToken ||
							node.operatorToken.kind === SyntaxKind.GreaterThanEqualsToken)
					) {
						const operator =
							node.operatorToken.kind === SyntaxKind.GreaterThanToken
								? ">"
								: ">=";
						findings.push({
							severity: ctx.severityOf(manifest, "greaterThanComparison"),
							category: "greaterThanComparison",
							message: `${file}:${sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getStart(sourceFile)).line + 1}: forbidden \`${operator}\`; use \`${operator === ">" ? "<" : "<="}\``,
						});
					}
					forEachChild(node, visit);
				};
				visit(sourceFile);
				return findings;
			});
	},
});
