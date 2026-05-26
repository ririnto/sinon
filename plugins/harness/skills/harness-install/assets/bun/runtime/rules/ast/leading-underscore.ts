#!/usr/bin/env bun
import type { Node, SourceFile } from "typescript@6.0.3";
import {
	createSourceFile,
	isClassDeclaration,
	isFunctionDeclaration,
	isIdentifier,
	isInterfaceDeclaration,
	isTypeAliasDeclaration,
	isVariableDeclaration,
	SyntaxKind,
} from "typescript@6.0.3";
// -*- coding: utf-8 -*-
import { basename } from "node:path";
import { astChildrenOf } from "../../core/ast-traversal";
import type {
	Finding,
	HarnessCheckRule,
	RuleContext,
} from "../harness-check-rule";

/**
 * Leading underscore rule configuration.
 */
interface RuleConfig {
	readonly allowedNames: ReadonlySet<string>;
	readonly allowedPatterns: readonly RegExp[];
}

/**
 * Forbid leading underscores in TypeScript file basenames and declarations.
 */
export const leadingUnderscoreRule: HarnessCheckRule = {
	category: "leadingUnderscore",
	applies(ctx: RuleContext): boolean {
		return (
			ctx.isEnabled("leadingUnderscore") &&
			ctx.stackSources("leadingUnderscore").length > 0
		);
	},

	validate(ctx: RuleContext): readonly Finding[] {
		const ruleConfig = readRuleConfig(ctx);
		return ctx.stackSources("leadingUnderscore").flatMap((file) => {
			const text = ctx.read(file);
			if (!text) {
				return [];
			}
			const sourceFile = createSourceFile(
				file,
				text,
				SyntaxKind.LatestVersion,
				true,
			);
			const basenameFinding = validateBasename(ctx, file, ruleConfig);
			return basenameFinding.concat(
				visitDeclarations(ctx, sourceFile, ruleConfig),
			);
		});
	},
};

function readRuleConfig(ctx: RuleContext): RuleConfig {
	const parameters = ctx.readJsonObject(
		ctx.categoryObject("leadingUnderscore").parameters,
	);
	const allowedNames = new Set(
		["_"].concat(ctx.readStringArray(parameters.allowedNames)),
	);
	const allowedPatterns = ctx
		.readStringArray(parameters.allowedPatterns)
		.map((pattern) => new RegExp(pattern));
	return { allowedNames, allowedPatterns };
}

function isForbidden(name: string, ruleConfig: RuleConfig): boolean {
	return (
		name.startsWith("_") &&
		!ruleConfig.allowedNames.has(name) &&
		!ruleConfig.allowedPatterns.some((pattern) => pattern.test(name))
	);
}

function validateBasename(
	ctx: RuleContext,
	file: string,
	ruleConfig: RuleConfig,
): readonly Finding[] {
	const fileBasename = basename(file).replace(/\.[^.]+$/, "");
	return isForbidden(fileBasename, ruleConfig)
		? [finding(ctx, file, fileBasename, 1, 1, 1, fileBasename.length + 1)]
		: [];
}

function visitDeclarations(
	ctx: RuleContext,
	sourceFile: SourceFile,
	ruleConfig: RuleConfig,
): readonly Finding[] {
	const declarationName = (node: Node): string => {
		if (
			(isFunctionDeclaration(node) ||
				isClassDeclaration(node) ||
				isInterfaceDeclaration(node) ||
				isTypeAliasDeclaration(node)) &&
			node.name
		) {
			return node.name.text;
		}
		if (isVariableDeclaration(node) && isIdentifier(node.name)) {
			return node.name.text;
		}
		return "";
	};
	const visitNode = (node: Node): readonly Finding[] => {
		const name = declarationName(node);
		const current = isForbidden(name, ruleConfig)
			? (() => {
					const start = sourceFile.getLineAndCharacterOfPosition(
						node.getStart(sourceFile),
					);
					const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
					return [
						finding(
							ctx,
							sourceFile.fileName,
							name,
							start.line + 1,
							start.character + 1,
							end.line + 1,
							end.character + 1,
						),
					];
				})()
			: [];
		return current.concat(astChildrenOf(node).flatMap(visitNode));
	};
	return visitNode(sourceFile);
}

function finding(
	ctx: RuleContext,
	file: string,
	name: string,
	startLine: number,
	startColumn: number,
	endLine: number,
	endColumn: number,
): Finding {
	return {
		severity: ctx.severityOf("leadingUnderscore"),
		category: "leadingUnderscore",
		message: `declaration \`${name}\` uses a leading underscore`,
		file,
		startLine,
		startColumn,
		endLine,
		endColumn,
		fix: {
			description: `rename \`${name}\` without a leading underscore`,
			safety: "manual",
			edits: [],
		},
	};
}
