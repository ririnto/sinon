#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isCatchClause,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid empty catch blocks.
 */
export const emptyCatchBlockRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "emptyCatchBlock",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "emptyCatchBlock", "typescript").flatMap((file) => {
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
						severity: ctx.severityOf(manifest, "emptyCatchBlock"),
						category: "emptyCatchBlock",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const visit = (node: Node): void => {
				if (isCatchClause(node)) {
					if (node.block.statements.length === 0) {
						findings.push({
							severity: ctx.severityOf(manifest, "emptyCatchBlock"),
							category: "emptyCatchBlock",
							message: `${file}:${sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1}: empty catch block; handle, rethrow, or convert to a Finding`,
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
