#!/usr/bin/env bun
import {
	createSourceFile,
	forEachChild,
	isIdentifier,
	isNewExpression,
	type Node,
	type SourceFile,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Forbid mutable collection constructors.
 */
export const forbidMutableCollectionRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "typescript").flatMap((file) => {
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
						severity: ctx.severityOf(manifest, "forbidMutableCollection"),
						category: "forbidMutableCollection",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const visit = (node: Node): void => {
				if (isNewExpression(node)) {
					const expr = node.expression;
					if (isIdentifier(expr)) {
						const name = expr.text;
						if (name === "Array" || name === "Map" || name === "Set") {
							const { line } = sourceFile.getLineAndCharacterOfPosition(
								node.getStart(sourceFile),
							);
							findings.push({
								severity: ctx.severityOf(manifest, "forbidMutableCollection"),
								category: "forbidMutableCollection",
								message: `${file}:${line + 1}: mutable collection construction \`new ${name}\`; use functional alternative`,
							});
						}
					}
				}
				forEachChild(node, visit);
			};

			visit(sourceFile);
			return findings;
		});
	},
});
