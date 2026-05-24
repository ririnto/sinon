#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isBlock,
	isIfStatement,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require braced blocks on if/else statements.
 */
export const ifStatementBracesRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "ifStatementBraces",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "ifStatementBraces", "typescript").flatMap((file) => {
			const text = ctx.read(file);
			if (!text) {
				return [];
			}

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
						severity: ctx.severityOf(manifest, "ifStatementBraces"),
						category: "ifStatementBraces",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const visit = (node: Node): void => {
				if (isIfStatement(node)) {
					if (!isBlock(node.thenStatement)) {
						const { line } = sourceFile.getLineAndCharacterOfPosition(
							node.getStart(sourceFile),
						);
						findings.push({
							severity: ctx.severityOf(manifest, "ifStatementBraces"),
							category: "ifStatementBraces",
							message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
						});
					}
					if (
						node.elseStatement &&
						!isBlock(node.elseStatement) &&
						!isIfStatement(node.elseStatement)
					) {
						const { line } = sourceFile.getLineAndCharacterOfPosition(
							node.elseStatement.getStart(sourceFile),
						);
						findings.push({
							severity: ctx.severityOf(manifest, "ifStatementBraces"),
							category: "ifStatementBraces",
							message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
						});
					}
				}
				forEachChild(node, visit);
			};

			visit(sourceFile);
			return findings;
		});
	},
});
