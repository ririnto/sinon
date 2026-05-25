#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  isCatchClause,
  isIdentifier,
  isThrowStatement,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";
import { astChildrenOf } from "../../core/ast-traversal";

/**
 * Forbid silent catch blocks without rethrow, throw, or logging.
 */
export const silentCatchRule: HarnessCheckRule = {
  category: "silentCatch",
  applies(ctx: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx.stackSources("silentCatch").flatMap((file) => {
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

      const hasSafeContent = (block: any): boolean => {
        if (block.statements.length === 0) {
          return false;
        }
        const state = { found: false };
        const visit = (node: Node): void => {
          if (state.found) {
            return;
          }
          if (isThrowStatement(node)) {
            state.found = true;
          }
          if (
            isIdentifier(node) &&
            node.text &&
            /^(console|logger|log)/.test(node.text)
          ) {
            state.found = true;
          }
          forEachChild(node, visit);
        };
        visit(block);
        return state.found;
      };

      const visitNode = (node: Node): readonly Finding[] => {
        if (!isCatchClause(node)) {
          return astChildrenOf(node).flatMap(visitNode);
        }
        if (hasSafeContent(node.block)) {
          return [];
        }
        const start = sourceFile.getLineAndCharacterOfPosition(
          node.getStart(sourceFile),
        );
        const end = sourceFile.getLineAndCharacterOfPosition(
          node.getEnd(),
        );
        return [{
          severity: ctx.severityOf("silentCatch"),
          category: "silentCatch",
          message: `${file}:${start.line + 1}: silent catch; rethrow, translate to a Finding, or log via structured logger`,
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
        }, ...astChildrenOf(node).flatMap(visitNode)];
      };

      return visitNode(sourceFile);
    });
  },
};
