#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Block, Node, SourceFile } from "typescript@6.0.3";
import {
    createSourceFile,
    forEachChild,
    isCallExpression,
    isCatchClause,
    isIdentifier,
    isPropertyAccessExpression,
    isThrowStatement,
    SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Forbid silent catch blocks without rethrow, throw, or logging.
 */
export const silentCatchRule: HarnessCheckRule = {
    category: "silentCatch",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        return ctx.stackSources("silentCatch").flatMap((file) => {
            const text = ctx.read(file);
            if (!text) {
                return [];
            }

            const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);

            const hasSafeContent = (block: Block): boolean => {
                if (block.statements.length === 0) {
                    return false;
                }
                const visit = (node: Node): boolean => {
                    if (isThrowStatement(node)) {
                        return true;
                    }
                    if (isLoggingCall(node)) {
                        return true;
                    }
                    let found = false;
                    forEachChild(node, (child) => {
                        if (!found && visit(child)) {
                            found = true;
                        }
                    });
                    return found;
                };
                return visit(block);
            };

            const visitNode = (node: Node): readonly Finding[] => {
                if (!isCatchClause(node)) {
                    return astChildrenOf(node).flatMap(visitNode);
                }
                if (hasSafeContent(node.block)) {
                    return [];
                }
                const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
                const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
                return [
                    {
                        severity: ctx.severityOf("silentCatch"),
                        category: "silentCatch",
                        message: "silent catch; rethrow, translate to a Finding, or log via structured logger",
                        file,
                        startLine: start.line + 1,
                        startColumn: start.character + 1,
                        endLine: end.line + 1,
                        endColumn: end.character + 1,
                        fix: {
                            description: "add rethrow, structured logging, or error translation",
                            safety: "unsafe",
                            edits: [],
                        },
                    },
                ].concat(astChildrenOf(node).flatMap(visitNode));
            };

            return visitNode(sourceFile);
        });
    },
};

/**
 * Detect logging calls from TypeScript AST call/property nodes.
 */
function isLoggingCall(node: Node): boolean {
    if (!isCallExpression(node)) {
        return false;
    }
    const expression = node.expression;
    if (isIdentifier(expression)) {
        return ["logger", "log"].includes(expression.text);
    }
    if (!isPropertyAccessExpression(expression)) {
        return false;
    }
    const receiver = dottedExpressionName(expression.expression);
    return receiver === "console" || receiver === "logger" || receiver === "log" || receiver.endsWith(".logger") || receiver.endsWith(".log");
}

/**
 * Return a dotted identifier path for property-access receivers.
 */
function dottedExpressionName(node: Node): string {
    if (isIdentifier(node)) {
        return node.text;
    }
    if (isPropertyAccessExpression(node)) {
        const receiver = dottedExpressionName(node.expression);
        return receiver ? `${receiver}.${node.name.text}` : node.name.text;
    }
    return "";
}
