#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  getLeadingCommentRanges,
  isClassDeclaration,
  isFunctionDeclaration,
  isIdentifier,
  isInterfaceDeclaration,
  isTypeAliasDeclaration,
  isVariableStatement,
  SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Read the visibility tokens for a given category from manifest configuration.
 *
 * @param ctx Rule context with manifest access.
 * @param category The rule category key (e.g. "publicDeclarationDocComment").
 * @param stack The stack identifier (e.g. "typescript").
 * @return Array of configured visibility tokens, or ["export"] as default if not configured.
 */
function readVisibilityTokens(ctx: RuleContext, category: string, stack: string): string[] {
  void stack;
  const config = ctx.manifest.raw[category];
  if (!config || typeof config !== "object") {
    return ["export"];
  }
  const tokens = config.parameters?.visibility;
  if (Array.isArray(tokens)) {
    return tokens;
  }
  return ["export"];
}

/**
 * Require JSDoc comments on public declarations.
 */
export const publicDeclarationDocCommentRule: HarnessCheckRule = {
  category: "publicDeclarationDocComment",
  applies(_: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const visibilityTokens = readVisibilityTokens(ctx, "publicDeclarationDocComment", ctx.stack);
    if (visibilityTokens.length === 0) {
      return [];
    }

    return ctx.stackSources("publicDeclarationDocComment").flatMap((file) => {
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

      const hasJSDoc = (node: Node): boolean => {
        const fullText = sourceFile.getFullText();
        const leadingComments = getLeadingCommentRanges(
          fullText,
          node.getFullStart(),
        );
        return (
          leadingComments &&
          leadingComments.length > 0 &&
          fullText
            .slice(
              leadingComments[leadingComments.length - 1].pos,
              leadingComments[leadingComments.length - 1].end,
            )
            .includes("/**")
        );
      };

      const checkDeclaration = (node: Node): readonly Finding[] => {
        const name =
          (isFunctionDeclaration(node) && node.name?.text) ||
          (isClassDeclaration(node) && node.name?.text) ||
          (isInterfaceDeclaration(node) && node.name?.text) ||
          (isTypeAliasDeclaration(node) && node.name?.text) ||
          (isVariableStatement(node) &&
          node.declarationList.declarations[0]?.name &&
          isIdentifier(node.declarationList.declarations[0].name)
            ? node.declarationList.declarations[0].name.text
            : "");
        if (!name || hasJSDoc(node)) {
          return [];
        }
        const start = sourceFile.getLineAndCharacterOfPosition(
          node.getStart(sourceFile),
        );
        const end = sourceFile.getLineAndCharacterOfPosition(
          node.getEnd(),
        );
        return [{
          severity: ctx.severityOf(
            "publicDeclarationDocComment",
          ),
          category: "publicDeclarationDocComment",
          message: `public declaration \`${name}\` is missing a documentation comment`,
          file,
          startLine: start.line + 1,
          startColumn: start.character + 1,
          endLine: end.line + 1,
          endColumn: end.character + 1,
          fix: {
            description: `add JSDoc comment for \`${name}\``,
            safety: "manual",
            edits: [],
          },
        }];
      };

      const matchesVisibility = (node: Node): boolean => {
        const hasToken = visibilityTokens.some((token) => {
          if (token === "export") {
            return node.modifiers?.some((m) => m.kind === SyntaxKind.ExportKeyword) ?? false;
          }
          return false;
        });
        return hasToken;
      };

      const visitNode = (node: Node): readonly Finding[] => {
        const fromHere =
          matchesVisibility(node) &&
          (node.kind === SyntaxKind.FunctionDeclaration ||
          node.kind === SyntaxKind.ClassDeclaration ||
          node.kind === SyntaxKind.InterfaceDeclaration ||
          node.kind === SyntaxKind.TypeAliasDeclaration ||
          node.kind === SyntaxKind.VariableStatement)
            ? checkDeclaration(node)
            : [];
        return fromHere.concat(astChildrenOf(node).flatMap(visitNode));
      };

      return visitNode(sourceFile);
    });
  },
};
