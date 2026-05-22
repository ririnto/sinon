#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isBlock, isFunctionDeclaration, isIdentifier, isMethodDeclaration, type FunctionLike, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Helper to check if a function has nested functions.
 */
function hasNestedFunctions(node: FunctionLike): boolean {
  let foundNested = false;
  const visit = (child: Node): void => {
    if (foundNested) {
      return;
    }
    if (child === node) {
      forEachChild(child, visit);
      return;
    }
    switch (child.kind) {
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.ArrowFunction:
      case SyntaxKind.Constructor:
        foundNested = true;
        return;
    }
    forEachChild(child, visit);
  };
  forEachChild(node, visit);
  return foundNested;
}

/**
 * Forbid blank lines in leaf function bodies.
 */
export class ForbidBlankLineInLeafFunctionRule implements HarnessCheckRule {
  static readonly category = "forbidBlankLineInLeafFunction";

  constructor(private readonly ctx: RuleContext) {}

  applies(_manifest: HarnessManifest): boolean {
    return true;
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
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
            severity: this.ctx.severityOf(manifest, ForbidBlankLineInLeafFunctionRule.category),
            category: ForbidBlankLineInLeafFunctionRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const extractBlankLineFindings = (
        funcNode: FunctionLike,
        body: FunctionLike["body"]
      ): readonly Finding[] => {
        if (!isBlock(body)) {
          return [];
        }

        const statements = body.statements;
        if (statements.length === 0) {
          return [];
        }

        const funcName =
          (isFunctionDeclaration(funcNode) && funcNode.name?.text) ||
          (isMethodDeclaration(funcNode) && funcNode.name && isIdentifier(funcNode.name) && funcNode.name.text) ||
          "<anonymous>";

        const blankLineFindings: Finding[] = [];

        const checkTrivia = (triviaStart: number, triviaEnd: number): void => {
          const trivia = text.slice(triviaStart, triviaEnd);
          const triviaLines = trivia.split(/\r?\n/);
          const triviaStartLine = sourceFile.getLineAndCharacterOfPosition(triviaStart).line;

          for (let i = 0; i < triviaLines.length; i++) {
            if (triviaLines[i].trim() === "") {
              blankLineFindings.push({
                severity: this.ctx.severityOf(manifest, ForbidBlankLineInLeafFunctionRule.category),
                category: ForbidBlankLineInLeafFunctionRule.category,
                message: `${file}:${triviaStartLine + i + 1}: leaf function \`${funcName}\` contains a blank line; remove or extract the section`,
              });
            }
          }
        };

        if (statements.length > 0) {
          checkTrivia(body.getStart(sourceFile, true), statements[0].getFullStart());
        }

        for (let i = 0; i < statements.length - 1; i++) {
          checkTrivia(statements[i].getEnd(), statements[i + 1].getFullStart());
        }

        if (statements.length > 0) {
          checkTrivia(statements[statements.length - 1].getEnd(), body.getEnd());
        }

        return blankLineFindings;
      };

      const visit = (node: Node): void => {
        switch (node.kind) {
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.Constructor: {
            const funcLike = node as FunctionLike;
            if (funcLike.body && !hasNestedFunctions(funcLike)) {
              findings.push(...extractBlankLineFindings(funcLike, funcLike.body));
            }
            break;
          }
        }
        forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  }
}
