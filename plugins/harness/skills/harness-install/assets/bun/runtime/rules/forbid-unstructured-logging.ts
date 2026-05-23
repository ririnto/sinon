#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isCallExpression,
	isIdentifier,
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
 * Forbid unstructured logging (console.log, console.error, etc.).
 */
export const forbidUnstructuredLoggingRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx
			.stackSources(manifest, "typescript")
			.map((file) => ({ file, text: ctx.read(file) }))
			.filter(
				({ text }): text is string =>
					text !== null && text !== undefined && text !== "",
			)
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
							severity: ctx.severityOf(manifest, "forbidUnstructuredLogging"),
							category: "forbidUnstructuredLogging",
							message: `failed to parse TypeScript: ${file}`,
						},
					];
				}
				const findings: Finding[] = [];
				const logMethods = ["log", "error", "warn", "info", "debug"];
				const visit = (node: Node): void => {
					if (
						isCallExpression(node) &&
						isPropertyAccessExpression(node.expression) &&
						isIdentifier(node.expression.expression) &&
						node.expression.expression.text === "console"
					) {
						const methodName = node.expression.name?.text;
						if (methodName && logMethods.includes(methodName)) {
							const { line } = sourceFile.getLineAndCharacterOfPosition(
								node.getStart(sourceFile),
							);
							findings.push({
								severity: ctx.severityOf(manifest, "forbidUnstructuredLogging"),
								category: "forbidUnstructuredLogging",
								message: `${file}:${line + 1}: unstructured logging \`console.${methodName}\`; use structured logger`,
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
