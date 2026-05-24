#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isIdentifier,
	isImportDeclaration,
	isPropertyAccessExpression,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require imports instead of fully qualified names. When the simple name from an
 * import is available, use it instead of inline FQN. If simple name conflicts with
 * another import, FQN is allowed.
 */
export const importOverFqnRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "importOverFqn",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.importOverFqn;
		if (typeof section !== "object" || section === null) {
			return false;
		}
		const enabled = (section as { enabled?: unknown }).enabled;
		return enabled !== false;
	},
	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "importOverFqn", "typescript").flatMap((file) => {
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
						severity: ctx.severityOf(manifest, "importOverFqn"),
						category: "importOverFqn",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}
			const importedNames = new Set<string>();
			const visit = (node: Node): void => {
				if (
					isImportDeclaration(node) &&
					node.importClause &&
					node.importClause.namedBindings &&
					!("name" in node.importClause.namedBindings)
				) {
					const bindings = node.importClause.namedBindings;
					if ("elements" in bindings) {
						for (const element of bindings.elements) {
							if (element.propertyName) {
								importedNames.add(element.name.text);
							} else {
								importedNames.add(element.name.text);
							}
						}
					}
				}
				forEachChild(node, visit);
			};
			visit(sourceFile);
			const findings: Finding[] = [];
			const checkFqn = (node: Node): void => {
				if (isPropertyAccessExpression(node) && node.expression) {
					let depth = 0;
					let current: Node | undefined = node;
					while (current && isPropertyAccessExpression(current)) {
						depth += 1;
						current = current.expression;
					}
					if (depth >= 2 && current && isIdentifier(current)) {
						const simpleName = current.text;
						if (!importedNames.has(simpleName)) {
							const fqnParts: string[] = [simpleName];
							let walker: Node | undefined = node;
							while (walker && isPropertyAccessExpression(walker)) {
								fqnParts.push(walker.name.text);
								walker = walker.expression;
							}
							fqnParts.reverse();
							const fqnStr = fqnParts.join(".");
							const { line } = sourceFile.getLineAndCharacterOfPosition(
								node.getStart(sourceFile),
							);
							findings.push({
								severity: ctx.severityOf(manifest, "importOverFqn"),
								category: "importOverFqn",
								message: `${file}:${line + 1}: fully qualified name \`${fqnStr}\` used inline; add an import and use the simple name`,
							});
						}
					}
				}
				forEachChild(node, checkFqn);
			};
			checkFqn(sourceFile);
			return findings;
		});
	},
});
