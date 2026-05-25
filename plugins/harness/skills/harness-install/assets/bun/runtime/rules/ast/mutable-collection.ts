#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  forEachChild,
  isIdentifier,
  isNewExpression,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

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
      const sourceFile: SourceFile = createSourceFile(
        file,
        text,
        SyntaxKind.LatestVersion,
        true,
      );
      const findings: Finding[] = [];
      const visit = (node: Node): void => {
        if (isNewExpression(node) && isIdentifier(node.expression)) {
          const name = node.expression.text;
          if (name === "Array" || name === "Map" || name === "Set") {
            const start = sourceFile.getLineAndCharacterOfPosition(
              node.getStart(sourceFile),
            );
            const end = sourceFile.getLineAndCharacterOfPosition(
              node.getEnd(),
            );
            findings.push({
              severity: ctx.severityOf("mutableCollection"),
              category: "mutableCollection",
              message: `${file}:${start.line + 1}: mutable collection construction \`new ${name}\`; use functional alternative`,
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: end.line + 1,
              endColumn: end.character + 1,
              fix: {
                description: `replace \`new ${name}\` with functional alternative`,
                safety: "unsafe",
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
