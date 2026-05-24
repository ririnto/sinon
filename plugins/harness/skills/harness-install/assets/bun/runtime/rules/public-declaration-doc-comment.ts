#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	getLeadingCommentRanges,
	isClassDeclaration,
	isFunctionDeclaration,
	isIdentifier,
	isInterfaceDeclaration,
	isTypeAliasDeclaration,
	isVariableStatement,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require JSDoc comments on public declarations.
 */
export const publicDeclarationDocCommentRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "publicDeclarationDocComment",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		return ctx.stackSources(manifest, "publicDeclarationDocComment", "typescript").flatMap((file) => {
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
						severity: ctx.severityOf(
							manifest,
							"publicDeclarationDocComment",
						),
						category: "publicDeclarationDocComment",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const hasJSDoc = (node: Node): boolean => {
				const fullText = sourceFile.getFullText();
				const leadingComments = getLeadingCommentRanges(
					fullText,
					node.getFullStart(),
				);
				return (
					leadingComments &&
					leadingComments.length > 0 &&
					fullText
						.slice(
							leadingComments[leadingComments.length - 1].pos,
							leadingComments[leadingComments.length - 1].end,
						)
						.includes("/**")
				);
			};

			const checkDeclaration = (node: Node): void => {
				const name =
					(isFunctionDeclaration(node) && node.name?.text) ||
					(isClassDeclaration(node) && node.name?.text) ||
					(isInterfaceDeclaration(node) && node.name?.text) ||
					(isTypeAliasDeclaration(node) && node.name?.text) ||
					(isVariableStatement(node) &&
					node.declarationList.declarations[0]?.name &&
					isIdentifier(node.declarationList.declarations[0].name)
						? node.declarationList.declarations[0].name.text
						: "");
				if (name && !hasJSDoc(node)) {
					const { line } = sourceFile.getLineAndCharacterOfPosition(
						node.getStart(sourceFile),
					);
					findings.push({
						severity: ctx.severityOf(
							manifest,
							"publicDeclarationDocComment",
						),
						category: "publicDeclarationDocComment",
						message: `${file}:${line + 1}: public declaration \`${name}\` is missing a documentation comment`,
					});
				}
			};

			const visit = (node: Node): void => {
				const isExported =
					node.modifiers?.some((m) => m.kind === SyntaxKind.ExportKeyword) ??
					false;

				if (isExported) {
					switch (node.kind) {
						case SyntaxKind.FunctionDeclaration:
						case SyntaxKind.ClassDeclaration:
						case SyntaxKind.InterfaceDeclaration:
						case SyntaxKind.TypeAliasDeclaration:
						case SyntaxKind.VariableStatement:
							checkDeclaration(node);
					}
				}
				forEachChild(node, visit);
			};

			visit(sourceFile);
			return findings;
		});
	},
});
