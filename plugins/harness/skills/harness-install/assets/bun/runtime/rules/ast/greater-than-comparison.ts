#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  isBinaryExpression,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";
import { astChildrenOf } from "../../core/ast-traversal";

/**
 * Forbid greater-than comparisons in TypeScript.
 */
export const greaterThanComparisonRule: HarnessCheckRule = {
  category: "greaterThanComparison",
  applies(ctx: RuleContext): boolean {
    return true;
  },
  validate(ctx: RuleContext): readonly Finding[] {
    return ctx
      .stackSources("greaterThanComparison")
      .map((file) => ({ file, text: ctx.read(file) }))
      .filter(({ text }) => text !== "")
      .flatMap(({ file, text }) => {
        const sourceFile: SourceFile = createSourceFile(
          file,
          text,
          SyntaxKind.LatestVersion,
          true,
        );
        const visitNode = (node: Node): readonly Finding[] => {
          if (
            isBinaryExpression(node) &&
            (node.operatorToken.kind === SyntaxKind.GreaterThanToken ||
              node.operatorToken.kind === SyntaxKind.GreaterThanEqualsToken)
          ) {
            const operator =
              node.operatorToken.kind === SyntaxKind.GreaterThanToken
                ? ">"
                : ">=";
            const inverse =
              operator === ">" ? "<" : "<=";
            const start = sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getStart(sourceFile));
            const end = sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getEnd());
            return [{
              severity: ctx.severityOf("greaterThanComparison"),
              category: "greaterThanComparison",
              message: `${file}:${start.line + 1}: forbidden \`${operator}\` comparison; use \`${inverse}\` with operands flipped`,
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: end.line + 1,
              endColumn: end.character + 1,
              fix: {
                description: `flip operands and swap \`${operator}\` to \`${inverse}\``,
                safety: "unsafe",
                edits: [],
              },
            }, ...astChildrenOf(node).flatMap(visitNode)];
          }
          return astChildrenOf(node).flatMap(visitNode);
        };
        return visitNode(sourceFile);
      });
  },
};
