#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node } from "typescript@6.0.3";
import {
  createSourceFile,
  getLeadingCommentRanges,
  SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require TSDoc comments to use multiline block style.
 */
export const multilineDocStyleRule: HarnessCheckRule = {
  category: "multilineDocStyle",
  applies(ctx: RuleContext): boolean {
    return ctx.isEnabled("multilineDocStyle") && ctx.stackSources("multilineDocStyle").length > 0;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    if (docStyleMode(ctx) !== "multiline") {
      return [];
    }
    return ctx.stackSources("multilineDocStyle").flatMap((file) => {
      const text = ctx.read(file);
      if (!text) {
        return [];
      }
      const sourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
      const fullText = sourceFile.getFullText();
      const visitedComments = new Set<number>();
      const visitNode = (node: Node): readonly Finding[] => {
        const leadingComments = getLeadingCommentRanges(fullText, node.getFullStart()) ?? [];
        const current = leadingComments
          .map((comment) => ({ comment, text: fullText.slice(comment.pos, comment.end) }))
          .filter(({ comment }) => {
            if (visitedComments.has(comment.pos)) {
              return false;
            }
            visitedComments.add(comment.pos);
            return true;
          })
          .filter(({ text: commentText }) => commentText.startsWith("/**") && !commentText.includes("\n"))
          .map(({ comment, text: commentText }) => {
            const start = sourceFile.getLineAndCharacterOfPosition(comment.pos);
            return {
              severity: ctx.severityOf("multilineDocStyle"),
              category: "multilineDocStyle",
              message: "TSDoc comment must use multiline style",
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: start.line + 1,
              endColumn: start.character + commentText.length + 1,
            } satisfies Finding;
          });
        return current.concat(astChildrenOf(node).flatMap(visitNode));
      };
      return visitNode(sourceFile);
    });
  },
};

function docStyleMode(ctx: RuleContext): string {
  const parameters = ctx.readJsonObject(ctx.categoryObject("multilineDocStyle").parameters);
  return typeof parameters.docStyleMode === "string" ? parameters.docStyleMode : "multiline";
}
