#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, getLeadingCommentRanges, isClassDeclaration, isFunctionDeclaration, isIdentifier, isInterfaceDeclaration, isTypeAliasDeclaration, isVariableStatement, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require JSDoc comments on public declarations.
 */
export class RequireDocCommentOnPublicDeclarationRule implements HarnessCheckRule {
  static readonly category = "requireDocCommentOnPublicDeclaration";

  constructor(private readonly ctx: RuleContext) {}

  applies(_manifest: HarnessManifest): boolean {
    return true;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const sources = this.ctx.stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = this.ctx.read(file);
      if (!text) {
        return [];
      }

      let sourceFile: SourceFile;
      try {
        sourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
      } catch {
        return [
          {
            severity: this.ctx.severityOf(manifest, RequireDocCommentOnPublicDeclarationRule.category),
            category: RequireDocCommentOnPublicDeclarationRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const hasJSDoc = (node: Node): boolean => {
        const fullText = sourceFile.getFullText();
        const leadingComments = getLeadingCommentRanges(fullText, node.getFullStart());
        if (!leadingComments || leadingComments.length === 0) {
          return false;
        }
        const lastComment = leadingComments[leadingComments.length - 1];
        const commentText = fullText.slice(lastComment.pos, lastComment.end);
        return commentText.includes("/**");
      };

      const checkDeclaration = (node: Node): void => {
        const name =
          (isFunctionDeclaration(node) && node.name?.text) ||
          (isClassDeclaration(node) && node.name?.text) ||
          (isInterfaceDeclaration(node) && node.name?.text) ||
          (isTypeAliasDeclaration(node) && node.name?.text) ||
          (isVariableStatement(node) && node.declarationList.declarations[0]?.name && isIdentifier(node.declarationList.declarations[0].name) ? node.declarationList.declarations[0].name.text : "");

        if (name && !hasJSDoc(node)) {
          const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
          findings.push({
            severity: this.ctx.severityOf(manifest, RequireDocCommentOnPublicDeclarationRule.category),
            category: RequireDocCommentOnPublicDeclarationRule.category,
            message: `${file}:${line + 1}: public declaration \`${name}\` is missing a documentation comment`,
          });
        }
      };

      const visit = (node: Node): void => {
        const isExported = node.modifiers?.some((m) => m.kind === SyntaxKind.ExportKeyword) ?? false;

        if (isExported) {
          switch (node.kind) {
            case SyntaxKind.FunctionDeclaration:
            case SyntaxKind.ClassDeclaration:
            case SyntaxKind.InterfaceDeclaration:
            case SyntaxKind.TypeAliasDeclaration:
            case SyntaxKind.VariableStatement:
              checkDeclaration(node);
          }
        }
        forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  }
}
