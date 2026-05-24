#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isCatchClause,
	isIdentifier,
	isThrowStatement,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid silent catch blocks without rethrow, throw, or logging.
 */
export const silentCatchRule = (ctx: RuleContext): HarnessCheckRule => ({
	category: "silentCatch",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "silentCatch", "typescript").flatMap((file) => {
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
						severity: ctx.severityOf(manifest, "silentCatch"),
						category: "silentCatch",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const hasSafeContent = (block: any): boolean => {
				if (block.statements.length === 0) {
					return false;
				}

				let hasThrowOrRethrow = false;

				const visit = (node: Node): void => {
					if (isThrowStatement(node)) {
						hasThrowOrRethrow = true;
					}
					if (
						isIdentifier(node) &&
						node.text &&
						/^(console|logger|log)/.test(node.text)
					) {
						hasThrowOrRethrow = true;
					}
					forEachChild(node, visit);
				};

				visit(block);
				return hasThrowOrRethrow;
			};

			const visit = (node: Node): void => {
				if (isCatchClause(node)) {
					if (!hasSafeContent(node.block)) {
						const { line } = sourceFile.getLineAndCharacterOfPosition(
							node.getStart(sourceFile),
						);
						findings.push({
							severity: ctx.severityOf(manifest, "silentCatch"),
							category: "silentCatch",
							message: `${file}:${line + 1}: silent catch; rethrow, translate to a Finding, or log via structured logger`,
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
