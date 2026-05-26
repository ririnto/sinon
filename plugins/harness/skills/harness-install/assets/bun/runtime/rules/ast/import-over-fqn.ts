#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
    createSourceFile,
    isIdentifier,
    isImportDeclaration,
    isPropertyAccessExpression,
    SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require imports instead of fully qualified names. When the simple name from an
 * import is available, use it instead of inline FQN. If simple name conflicts with
 * another import, FQN is allowed.
 */
export const importOverFqnRule: HarnessCheckRule = {
    category: "importOverFqn",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.importOverFqn;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        return enabled !== false;
    },
    validate(ctx: RuleContext): readonly Finding[] {
        return ctx.stackSources("importOverFqn").flatMap((file) => {
            const text = ctx.read(file);
            if (!text) {
                return [];
            }
            const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
            const importedNames = new Set<string>();
            const collectImports = (node: Node): void => {
                if (
                    isImportDeclaration(node) &&
                    node.importClause?.namedBindings &&
                    !("name" in node.importClause.namedBindings)
                ) {
                    const bindings = node.importClause.namedBindings;
                    if ("elements" in bindings) {
                        for (const element of bindings.elements) {
                            importedNames.add(element.name.text);
                        }
                    }
                }
                astChildrenOf(node).forEach(collectImports);
            };
            collectImports(sourceFile);
            const checkFqn = (node: Node): readonly Finding[] => {
                if (isPropertyAccessExpression(node) && node.expression) {
                    const traverseDepth = (curr: Node | undefined, d: number): [number, Node | undefined] => {
                        if (!curr || !isPropertyAccessExpression(curr)) {
                            return [d, curr];
                        }
                        return traverseDepth(curr.expression, d + 1);
                    };
                    const [depth, current] = traverseDepth(node, 0);
                    if (depth >= 2 && current && isIdentifier(current)) {
                        const simpleName = current.text;
                        if (!importedNames.has(simpleName)) {
                            const buildFqnParts = (curr: Node | undefined, acc: string[]): string[] => {
                                if (!curr || !isPropertyAccessExpression(curr)) {
                                    return acc.reverse();
                                }
                                return buildFqnParts(curr.expression, acc.concat(curr.name.text));
                            };
                            const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
                            const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
                            return [
                                {
                                    severity: ctx.severityOf("importOverFqn"),
                                    category: "importOverFqn",
                                    message: `fully qualified name \`${buildFqnParts(node, [simpleName]).join(".")}\` used inline; add an import and use the simple name`,
                                    file,
                                    startLine: start.line + 1,
                                    startColumn: start.character + 1,
                                    endLine: end.line + 1,
                                    endColumn: end.character + 1,
                                    fix: {
                                        description: `add import for \`${simpleName}\` and replace FQN with simple name`,
                                        safety: "unsafe",
                                        edits: [],
                                    },
                                },
                            ].concat(astChildrenOf(node).flatMap(checkFqn));
                        }
                    }
                }
                return astChildrenOf(node).flatMap(checkFqn);
            };
            return checkFqn(sourceFile);
        });
    },
};
