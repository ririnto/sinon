#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isBlock, isFunctionDeclaration, isIdentifier, isMethodDeclaration, isReturnStatement, type FunctionLike, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid early return statements in functions.
 */
export const forbidEarlyReturnRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(_manifest: HarnessManifest): boolean {
    return true;
  },

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    return ctx.stackSources(manifest, "typescript").flatMap((file) => {
      const text = ctx.read(file);
      if (!text) {
        return [];
      }

      let sourceFile: SourceFile;
      try {
        sourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
      } catch {
        return [
          {
            severity: ctx.severityOf(manifest, "forbidEarlyReturn"),
            category: "forbidEarlyReturn",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const getFuncName = (funcNode: FunctionLike): string => {
        if (isFunctionDeclaration(funcNode) && funcNode.name) {
          return funcNode.name.text;
        }
        if (isMethodDeclaration(funcNode) && funcNode.name && isIdentifier(funcNode.name)) {
          return funcNode.name.text;
        }
        return "<anonymous>";
      };

      const checkFunc = (funcNode: FunctionLike): void => {
        if (funcNode.body && isBlock(funcNode.body)) {
          const statements = funcNode.body.statements;
          if (statements.length > 0) {
            const funcName = getFuncName(funcNode);
            findings.push(
              ...statements
                .slice(0, -1)
                .filter(isReturnStatement)
                .map((stmt) => {
                  const { line } = sourceFile.getLineAndCharacterOfPosition(stmt.getStart(sourceFile));
                  return {
                    severity: ctx.severityOf(manifest, "forbidEarlyReturn"),
                    category: "forbidEarlyReturn",
                    message: `${file}:${line + 1}: function \`${funcName}\` has an early return; restructure with single exit`,
                  };
                })
            );
          }
        }
      };

      const visit = (node: Node): void => {
        switch (node.kind) {
          case SyntaxKind.FunctionDeclaration:
          case SyntaxKind.MethodDeclaration:
          case SyntaxKind.FunctionExpression:
          case SyntaxKind.ArrowFunction:
          case SyntaxKind.Constructor: {
            checkFunc(node as FunctionLike);
            break;
          }
        }
        forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  }

});
