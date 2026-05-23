#!/usr/bin/env bun
import type { FunctionLike, Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isBlock,
	isFunctionDeclaration,
	isIdentifier,
	isMethodDeclaration,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Helper to check if a function has nested functions.
 */
export const forbidBlankLineInLeafFunctionRule = (
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
						severity: ctx.severityOf(manifest, "forbidBlankLineInLeafFunction"),
						category: "forbidBlankLineInLeafFunction",
						message: `failed to parse TypeScript: ${file}`,
					},
				];
			}

			const findings: Finding[] = [];

			const extractBlankLineFindings = (
				funcNode: FunctionLike,
				body: FunctionLike["body"],
			): readonly Finding[] => {
				const blankLineFindings: Finding[] = [];
				if (!isBlock(body) || body.statements.length === 0) {
					return blankLineFindings;
				}

				const statements = body.statements;

				const funcName =
					(isFunctionDeclaration(funcNode) && funcNode.name?.text) ||
					(isMethodDeclaration(funcNode) &&
						funcNode.name &&
						isIdentifier(funcNode.name) &&
						funcNode.name.text) ||
					"<anonymous>";

				const checkTrivia = (triviaStart: number, triviaEnd: number): void => {
					const triviaStartLine =
						sourceFile.getLineAndCharacterOfPosition(triviaStart).line;
					blankLineFindings.push(
						...text
							.slice(triviaStart, triviaEnd)
							.split(/\r?\n/)
							.map((line, i) => ({ line, index: i }))
							.filter(({ line }) => line.trim() === "")
							.map(({ index }) => ({
								severity: ctx.severityOf(
									manifest,
									"forbidBlankLineInLeafFunction",
								),
								category: "forbidBlankLineInLeafFunction",
								message: `${file}:${triviaStartLine + index + 1}: leaf function \`${funcName}\` contains a blank line; remove or extract the section`,
							})),
					);
				};

				if (statements.length > 0) {
					checkTrivia(
						body.getStart(sourceFile, true),
						statements[0].getFullStart(),
					);
				}
				statements.slice(0, -1).forEach((stmt, i) => {
					checkTrivia(stmt.getEnd(), statements[i + 1].getFullStart());
				});
				if (statements.length > 0) {
					checkTrivia(
						statements[statements.length - 1].getEnd(),
						body.getEnd(),
					);
				}

				return blankLineFindings;
			};

			const visit = (node: Node): void => {
				switch (node.kind) {
					case SyntaxKind.FunctionDeclaration:
					case SyntaxKind.MethodDeclaration:
					case SyntaxKind.FunctionExpression:
					case SyntaxKind.ArrowFunction:
					case SyntaxKind.Constructor: {
						const funcLike = node as FunctionLike;
						if (funcLike.body && !hasNestedFunctions(funcLike)) {
							findings.push(
								...extractBlankLineFindings(funcLike, funcLike.body),
							);
						}
						break;
					}
				}
				forEachChild(node, visit);
			};

			visit(sourceFile);
			return findings;
		});
	},
});
