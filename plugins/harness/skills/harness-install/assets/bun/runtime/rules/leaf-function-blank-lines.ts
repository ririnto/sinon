#!/usr/bin/env bun
import type { FunctionLike, Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	forEachChild,
	isBlock,
	isFunctionDeclaration,
	isIdentifier,
	isMethodDeclaration,
	ScriptTarget,
	SyntaxKind,
} from "typescript@6.0.3";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

const CATEGORY = "leafFunctionBlankLines";

/**
 * Helper to check if a function has nested functions.
 */
const hasNestedFunctions = (funcNode: FunctionLike): boolean => {
	let found = false;
	const visit = (node: Node): void => {
		if (found) {
			return;
		}
		if (node !== funcNode) {
			switch (node.kind) {
				case SyntaxKind.FunctionDeclaration:
				case SyntaxKind.MethodDeclaration:
				case SyntaxKind.FunctionExpression:
				case SyntaxKind.ArrowFunction:
				case SyntaxKind.Constructor: {
					found = true;
					return;
				}
			}
		}
		forEachChild(node, visit);
	};
	forEachChild(funcNode, visit);
	return found;
};

export const leafFunctionBlankLinesRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "leafFunctionBlankLines",
	applies(_manifest: HarnessManifest): boolean {
		return true;
	},

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest[CATEGORY]).parameters,
		);
		const configuredMaxConsecutiveBlankLines = Number(
			parameters.maxConsecutiveBlankLines ?? 1,
		);
		const maxConsecutiveBlankLines = Number.isFinite(
			configuredMaxConsecutiveBlankLines,
		)
			? Math.max(0, Math.trunc(configuredMaxConsecutiveBlankLines))
			: 1;
		return ctx
			.stackSources(manifest, CATEGORY, "typescript")
			.flatMap((file) => findingsForFile(ctx, manifest, file, maxConsecutiveBlankLines));
	},
});

const findingsForFile = (
	ctx: RuleContext,
	manifest: HarnessManifest,
	file: string,
	maxConsecutiveBlankLines: number,
): readonly Finding[] => {
	const text = ctx.read(file);
	if (!text) {
		return [];
	}
	let sourceFile: SourceFile;
	try {
		sourceFile = createSourceFile(file, text, ScriptTarget.Latest, true);
	} catch {
		return [
			{
				severity: ctx.severityOf(manifest, CATEGORY),
				category: CATEGORY,
				message: `failed to parse TypeScript: ${file}`,
			},
		];
	}
	const findings: Finding[] = [];
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
						...extractBlankLineFindings(
							ctx,
							manifest,
							file,
							text,
							sourceFile,
							funcLike,
							funcLike.body,
							maxConsecutiveBlankLines,
						),
					);
				}
				break;
			}
		}
		forEachChild(node, visit);
	};
	visit(sourceFile);
	return findings;
};

const extractBlankLineFindings = (
	ctx: RuleContext,
	manifest: HarnessManifest,
	file: string,
	text: string,
	sourceFile: SourceFile,
	funcNode: FunctionLike,
	body: FunctionLike["body"],
	maxConsecutiveBlankLines: number,
): readonly Finding[] => {
	const blankLineFindings: Finding[] = [];
	if (!isBlock(body) || body.statements.length === 0) {
		return blankLineFindings;
	}
	const funcName =
		(isFunctionDeclaration(funcNode) && funcNode.name?.text) ||
		(isMethodDeclaration(funcNode) &&
			funcNode.name &&
			isIdentifier(funcNode.name) &&
			funcNode.name.text) ||
		"<anonymous>";
	const startLine = sourceFile.getLineAndCharacterOfPosition(
		body.getStart(sourceFile, true),
	).line;
	const endLine = sourceFile.getLineAndCharacterOfPosition(body.getEnd()).line;
	let blankLines = 0;
	text
		.split(/\r?\n/)
		.slice(startLine, endLine + 1)
		.forEach((line, index) => {
			if (line.trim() === "") {
				blankLines += 1;
				if (blankLines > maxConsecutiveBlankLines) {
					blankLineFindings.push({
						severity: ctx.severityOf(manifest, CATEGORY),
						category: CATEGORY,
						message: `${file}:${startLine + index + 1}: leaf function \`${funcName}\` contains too many blank lines; remove or extract the section`,
					});
				}
			} else {
				blankLines = 0;
			}
		});
	return blankLineFindings;
};
