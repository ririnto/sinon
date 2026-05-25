#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  forEachChild,
  isBlock,
  isIfStatement,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require braced blocks on if/else statements.
 */
export const ifStatementBracesRule: HarnessCheckRule = {
  category: "ifStatementBraces",
  applies(ctx: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx.stackSources("ifStatementBraces").flatMap((file) => {
      const text = ctx.read(file);
      if (!text) {
        return [];
      }

      const sourceFile: SourceFile = createSourceFile(
        file,
        text,
        SyntaxKind.LatestVersion,
        true,
      );
      const findings: Finding[] = [];

      const visit = (node: Node): void => {
        if (isIfStatement(node)) {
          if (!isBlock(node.thenStatement)) {
            const start = sourceFile.getLineAndCharacterOfPosition(
              node.getStart(sourceFile),
            );
            const end = sourceFile.getLineAndCharacterOfPosition(
              node.thenStatement.getEnd(),
            );
            findings.push({
              severity: ctx.severityOf("ifStatementBraces"),
              category: "ifStatementBraces",
              message: `${file}:${start.line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: end.line + 1,
              endColumn: end.character + 1,
              fix: {
                description: "wrap body with braces",
                safety: "safe",
                edits: [],
              },
            });
          }
          if (
            node.elseStatement &&
            !isBlock(node.elseStatement) &&
            !isIfStatement(node.elseStatement)
          ) {
            const start = sourceFile.getLineAndCharacterOfPosition(
              node.elseStatement.getStart(sourceFile),
            );
            const end = sourceFile.getLineAndCharacterOfPosition(
              node.elseStatement.getEnd(),
            );
            findings.push({
              severity: ctx.severityOf("ifStatementBraces"),
              category: "ifStatementBraces",
              message: `${file}:${start.line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: end.line + 1,
              endColumn: end.character + 1,
              fix: {
                description: "wrap body with braces",
                safety: "safe",
                edits: [],
              },
            });
          }
        }
        forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};
