#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  isImportDeclaration,
  isNamespaceImport,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";
import { astChildrenOf } from "../../core/ast-traversal";

/**
 * Forbid wildcard imports.
 */
export const wildcardImportRule: HarnessCheckRule = {
  category: "wildcardImport",
  applies(ctx: RuleContext): boolean {
    return true;
  },
  validate(ctx: RuleContext): readonly Finding[] {
    return ctx
      .stackSources("wildcardImport")
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
            isImportDeclaration(node) &&
            node.importClause?.namedBindings &&
            isNamespaceImport(node.importClause.namedBindings)
          ) {
            const start = sourceFile.getLineAndCharacterOfPosition(
              node.getStart(sourceFile),
            );
            const end = sourceFile.getLineAndCharacterOfPosition(
              node.getEnd(),
            );
            return [{
              severity: ctx.severityOf("wildcardImport"),
              category: "wildcardImport",
              message: `${file}:${start.line + 1}: wildcard import \`import * as\` forbidden; import explicit symbols`,
              file,
              startLine: start.line + 1,
              startColumn: start.character + 1,
              endLine: end.line + 1,
              endColumn: end.character + 1,
              fix: {
                description: "replace wildcard import with explicit named imports",
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
