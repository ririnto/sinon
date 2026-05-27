#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { FunctionLike, Node, SourceFile } from "typescript@6.0.3";
import {
    createSourceFile,
    isBlock,
    isFunctionDeclaration,
    isIdentifier,
    isMethodDeclaration,
    ScriptTarget,
    SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

const CATEGORY = "leafFunctionBlankLines";

/**
 * Helper to check if a function has nested functions.
 */
const hasNestedFunctions = (funcNode: FunctionLike): boolean => {
    const checkNested = (node: Node, isRoot: boolean): boolean => {
        if (!isRoot) {
            switch (node.kind) {
                case SyntaxKind.FunctionDeclaration:
                case SyntaxKind.MethodDeclaration:
                case SyntaxKind.FunctionExpression:
                case SyntaxKind.ArrowFunction:
                case SyntaxKind.Constructor:
                    return true;
            }
        }
        return astChildrenOf(node).some((child) => checkNested(child, false));
    };
    return checkNested(funcNode, true);
};

export const leafFunctionBlankLinesRule: HarnessCheckRule = {
    category: "leafFunctionBlankLines",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw[CATEGORY]).parameters);
        const n = Number(parameters.maxConsecutiveBlankLines ?? 1);
        const maxConsecutiveBlankLines = Number.isFinite(n) ? Math.max(0, Math.trunc(n)) : 1;
        return ctx.stackSources(CATEGORY).flatMap((file) => findingsForFile(ctx, file, maxConsecutiveBlankLines));
    },
};

const findingsForFile = (ctx: RuleContext, file: string, maxConsecutiveBlankLines: number): readonly Finding[] => {
    const text = ctx.read(file);
    if (!text) {
        return [];
    }
    const sourceFile: SourceFile = createSourceFile(file, text, ScriptTarget.Latest, true);
    const visitNode = (node: Node): readonly Finding[] => {
        switch (node.kind) {
            case SyntaxKind.FunctionDeclaration:
            case SyntaxKind.MethodDeclaration:
            case SyntaxKind.FunctionExpression:
            case SyntaxKind.ArrowFunction:
            case SyntaxKind.Constructor: {
                const funcLike = node as FunctionLike;
                const fromHere =
                    funcLike.body && !hasNestedFunctions(funcLike)
                        ? extractBlankLineFindings(
                              ctx,
                              file,
                              text,
                              sourceFile,
                              funcLike,
                              funcLike.body,
                              maxConsecutiveBlankLines,
                          )
                        : [];
                return fromHere.concat(astChildrenOf(node).flatMap(visitNode));
            }
            default:
                return astChildrenOf(node).flatMap(visitNode);
        }
    };
    return visitNode(sourceFile);
};

const extractBlankLineFindings = (
    ctx: RuleContext,
    file: string,
    text: string,
    sourceFile: SourceFile,
    funcNode: FunctionLike,
    body: FunctionLike["body"],
    maxConsecutiveBlankLines: number,
): readonly Finding[] => {
    if (!isBlock(body) || body.statements.length === 0) {
        return [];
    }
    const startLine = sourceFile.getLineAndCharacterOfPosition(body.getStart(sourceFile, true)).line;
    const endLine = sourceFile.getLineAndCharacterOfPosition(body.getEnd()).line;
    const findings: Finding[] = [];
    let blankLines = 0;
    text.split(/\r?\n/)
        .slice(startLine, endLine + 1)
        .forEach((line, index) => {
            if (line.trim() === "") {
                blankLines += 1;
                if (blankLines > maxConsecutiveBlankLines) {
                    const lineNum = startLine + index + 1;
                    findings.push({
                        severity: ctx.severityOf(CATEGORY),
                        category: CATEGORY,
                        message: `leaf function \`${(isFunctionDeclaration(funcNode) && funcNode.name?.text) || (isMethodDeclaration(funcNode) && funcNode.name && isIdentifier(funcNode.name) && funcNode.name.text) || "<anonymous>"}\` contains too many blank lines; remove or extract the section`,
                        file,
                        startLine: lineNum,
                        startColumn: 1,
                        endLine: lineNum,
                        endColumn: line.length + 1,
                        fix: {
                            description: "remove extra blank lines",
                            safety: "safe",
                            edits: [
                                {
                                    file,
                                    startLine: lineNum,
                                    startColumn: 1,
                                    endLine: lineNum + 1,
                                    endColumn: 1,
                                    replacement: "",
                                },
                            ],
                        },
                    });
                }
            } else {
                blankLines = 0;
            }
        });
    return findings;
};
