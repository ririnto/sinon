#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { FunctionLike, Node, SourceFile, Statement } from "typescript@6.0.3";
import {
    createSourceFile,
    isBlock,
    isFunctionDeclaration,
    isIdentifier,
    isMethodDeclaration,
    isReturnStatement,
    SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Forbid early return statements in functions.
 */
export const earlyReturnRule: HarnessCheckRule = {
    category: "earlyReturn",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        return ctx
            .stackSources("earlyReturn")
            .map((file) => ({ file, text: ctx.read(file) }))
            .filter(({ text }) => text !== "")
            .flatMap(({ file, text }) => {
                const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
                const getFuncName = (funcNode: FunctionLike): string => {
                    if (isFunctionDeclaration(funcNode) && funcNode.name) {
                        return funcNode.name.text;
                    }
                    if (isMethodDeclaration(funcNode) && funcNode.name && isIdentifier(funcNode.name)) {
                        return funcNode.name.text;
                    }
                    return "<anonymous>";
                };
                const visitNode = (node: Node): readonly Finding[] => {
                    switch (node.kind) {
                        case SyntaxKind.FunctionDeclaration:
                        case SyntaxKind.MethodDeclaration:
                        case SyntaxKind.FunctionExpression:
                        case SyntaxKind.ArrowFunction:
                        case SyntaxKind.Constructor: {
                            const funcNode = node as FunctionLike;
                            if (!funcNode.body || !isBlock(funcNode.body) || funcNode.body.statements.length === 0) {
                                return astChildrenOf(node).flatMap(visitNode);
                            }
                            const earlyReturns = funcNode.body.statements
                                .slice(0, -1)
                                .filter(isReturnStatement)
                                .map((stmt: Statement) => {
                                    const start = sourceFile.getLineAndCharacterOfPosition(stmt.getStart(sourceFile));
                                    const end = sourceFile.getLineAndCharacterOfPosition(stmt.getEnd());
                                    return {
                                        severity: ctx.severityOf("earlyReturn"),
                                        category: "earlyReturn",
                                        message: `function \`${getFuncName(funcNode)}\` has an early return; restructure with single exit`,
                                        file,
                                        startLine: start.line + 1,
                                        startColumn: start.character + 1,
                                        endLine: end.line + 1,
                                        endColumn: end.character + 1,
                                        fix: {
                                            description: "restructure function with single exit point",
                                            safety: "unsafe",
                                            edits: [],
                                        },
                                    };
                                });
                            return earlyReturns.concat(astChildrenOf(node).flatMap(visitNode));
                        }
                        default:
                            return astChildrenOf(node).flatMap(visitNode);
                    }
                };
                return visitNode(sourceFile);
            });
    },
};
