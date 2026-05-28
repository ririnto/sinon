#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
    createSourceFile,
    isCallExpression,
    isIdentifier,
    isNewExpression,
    isPropertyAccessExpression,
    SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Forbid mutable collection constructors.
 */
export const mutableCollectionRule: HarnessCheckRule = {
    category: "mutableCollection",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        return ctx.stackSources("mutableCollection").flatMap((file) => {
            const text = ctx.read(file);
            if (!text) {
                return [];
            }
            const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
            const parameters = ctx.readJsonObject(ctx.categoryObject("mutableCollection").parameters);
            const forbiddenConstructors =
                ctx.readStringArray(parameters.forbiddenConstructors).length > 0
                    ? ctx.readStringArray(parameters.forbiddenConstructors)
                    : ["Array", "Map", "Set", "WeakMap", "WeakSet"];
            const accumulationMethods =
                ctx.readStringArray(parameters.accumulationMethods).length > 0
                    ? ctx.readStringArray(parameters.accumulationMethods)
                    : ["push", "add", "set"];
            const allowedOneShotPatterns = ctx.readStringArray(parameters.allowedOneShotPatterns);
            const visit = (node: Node): readonly Finding[] => [
                ...findingForNode(
                    ctx,
                    file,
                    sourceFile,
                    node,
                    forbiddenConstructors,
                    accumulationMethods,
                    allowedOneShotPatterns,
                ),
                ...astChildrenOf(node).flatMap(visit),
            ];
            return visit(sourceFile);
        });
    },
};

function findingForNode(
    ctx: RuleContext,
    file: string,
    sourceFile: SourceFile,
    node: Node,
    forbiddenConstructors: readonly string[],
    accumulationMethods: readonly string[],
    allowedOneShotPatterns: readonly string[],
): readonly Finding[] {
    if (isNewExpression(node) && isIdentifier(node.expression)) {
        const name = node.expression.text;
        if (forbiddenConstructors.includes(name) && !isAllowedOneShot(node, allowedOneShotPatterns)) {
            return [mutableFinding(ctx, file, sourceFile, node, `new ${name}`)];
        }
    }
    if (
        isCallExpression(node) &&
        isPropertyAccessExpression(node.expression) &&
        accumulationMethods.includes(node.expression.name.text)
    ) {
        return [mutableFinding(ctx, file, sourceFile, node, node.expression.name.text)];
    }
    return [];
}

function isAllowedOneShot(node: Node, allowedOneShotPatterns: readonly string[]): boolean {
    return allowedOneShotPatterns.some((pattern) => {
        if (pattern === "Array.from(new Set(...))") {
            return isArrayFromNewSet(node);
        }
        if (pattern === "Array.from(new Map(...).values())") {
            return isArrayFromNewMapValues(node);
        }
        if (pattern === "new Set(readonlyArray)") {
            return isNewNamedExpression(node, "Set");
        }
        return false;
    });
}

function isArrayFromNewSet(node: Node): boolean {
    const parent = node.parent;
    return isArrayFromCall(parent) && parent.arguments.length === 1 && parent.arguments[0] === node &&
        isNewNamedExpression(node, "Set");
}

function isArrayFromNewMapValues(node: Node): boolean {
    const valuesCall = node.parent?.parent;
    const arrayFromCall = valuesCall?.parent;
    return isCallExpression(valuesCall) && isPropertyAccessExpression(valuesCall.expression) &&
        valuesCall.expression.expression === node && valuesCall.expression.name.text === "values" &&
        isNewNamedExpression(node, "Map") && isArrayFromCall(arrayFromCall) &&
        arrayFromCall.arguments.length === 1 && arrayFromCall.arguments[0] === valuesCall;
}

function isArrayFromCall(node: Node | undefined): node is import("typescript@6.0.3").CallExpression {
    return !!node && isCallExpression(node) && isPropertyAccessExpression(node.expression) &&
        isIdentifier(node.expression.expression) && node.expression.expression.text === "Array" &&
        node.expression.name.text === "from";
}

function isNewNamedExpression(node: Node, name: string): boolean {
    return isNewExpression(node) && isIdentifier(node.expression) && node.expression.text === name;
}

function mutableFinding(ctx: RuleContext, file: string, sourceFile: SourceFile, node: Node, name: string): Finding {
    const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
    const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
    return {
        severity: ctx.severityOf("mutableCollection"),
        category: "mutableCollection",
        message: `mutable collection construction \`${name}\`; use functional alternative`,
        file,
        startLine: start.line + 1,
        startColumn: start.character + 1,
        endLine: end.line + 1,
        endColumn: end.character + 1,
        fix: {
            description: `replace \`${name}\` with functional alternative`,
            safety: "unsafe",
            edits: [],
        },
    };
}
