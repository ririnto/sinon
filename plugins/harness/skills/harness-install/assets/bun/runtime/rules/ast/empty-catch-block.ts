#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  isCatchClause,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";
import { astChildrenOf } from "../../core/ast-traversal";

/**
 * Forbid empty catch blocks.
 */
export const emptyCatchBlockRule: HarnessCheckRule = {
  category: "emptyCatchBlock",
  applies(ctx: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx.stackSources("emptyCatchBlock").flatMap((file) => {
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

      const visitNode = (node: Node): readonly Finding[] => {
        if (!isCatchClause(node) || node.block.statements.length !== 0) {
          return astChildrenOf(node).flatMap(visitNode);
        }
        const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
        const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
        return [{
          severity: ctx.severityOf("emptyCatchBlock"),
          category: "emptyCatchBlock",
          message: `${file}:${start.line + 1}: empty catch block; handle, rethrow, or convert to a Finding`,
          file,
          startLine: start.line + 1,
          startColumn: start.character + 1,
          endLine: end.line + 1,
          endColumn: end.character + 1,
          fix: {
            description: "add catch block handling or rethrow statement",
            safety: "unsafe",
            edits: [],
          },
        }, ...astChildrenOf(node).flatMap(visitNode)];
      };

      return visitNode(sourceFile);
    });
  },
};
