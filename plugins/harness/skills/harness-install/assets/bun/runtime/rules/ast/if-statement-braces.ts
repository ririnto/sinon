#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import { createSourceFile, forEachChild, isBlock, isIfStatement, SyntaxKind } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require braced blocks on if/else statements.
 */
export const ifStatementBracesRule: HarnessCheckRule = {
    category: "ifStatementBraces",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        return ctx.stackSources("ifStatementBraces").flatMap((file) => {
            const text = ctx.read(file);
            if (!text) {
                return [];
            }
            const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
            const visit = (node: Node): readonly Finding[] => {
                if (!isIfStatement(node)) {
                    let result: readonly Finding[] = [];
                    forEachChild(node, (child) => {
                        result = [...result, ...visit(child)];
                    });
                    return result;
                }
                const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
                const thenEnd = sourceFile.getLineAndCharacterOfPosition(node.thenStatement.getEnd());
                const thenFindings = !isBlock(node.thenStatement)
                    ? [
                          {
                              severity: ctx.severityOf("ifStatementBraces"),
                              category: "ifStatementBraces",
                              message: `if/else without braces; wrap the body in \`{ ... }\``,
                              file,
                              startLine: start.line + 1,
                              startColumn: start.character + 1,
                              endLine: thenEnd.line + 1,
                              endColumn: thenEnd.character + 1,
                              fix: {
                                  description: "wrap body with braces",
                                  safety: "safe",
                                  edits: [],
                              },
                          },
                      ]
                    : [];
                const elseStart = sourceFile.getLineAndCharacterOfPosition(node.elseStatement?.getStart(sourceFile));
                const elseEnd = sourceFile.getLineAndCharacterOfPosition(node.elseStatement?.getEnd());
                const elseFindings =
                    node.elseStatement && !isBlock(node.elseStatement) && !isIfStatement(node.elseStatement)
                        ? [
                              {
                                  severity: ctx.severityOf("ifStatementBraces"),
                                  category: "ifStatementBraces",
                                  message: `if/else without braces; wrap the body in \`{ ... }\``,
                                  file,
                                  startLine: elseStart.line + 1,
                                  startColumn: elseStart.character + 1,
                                  endLine: elseEnd.line + 1,
                                  endColumn: elseEnd.character + 1,
                                  fix: {
                                      description: "wrap body with braces",
                                      safety: "safe",
                                      edits: [],
                                  },
                              },
                          ]
                        : [];
                let result = [...thenFindings, ...elseFindings];
                forEachChild(node, (child) => {
                    result = [...result, ...visit(child)];
                });
                return result;
            };
            return visit(sourceFile);
        });
    },
};
