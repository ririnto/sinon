#!/usr/bin/env bun
import {
  SyntaxKind,
  createSourceFile,
  forEachChild,
  isBinaryExpression,
  type Node,
  type SourceFile,
} from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid greater-than comparisons in TypeScript.
 */
export const forbidGreaterThanComparisonRule = (ctx: RuleContext): HarnessCheckRule => ({
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
            severity: ctx.severityOf(manifest, "forbidGreaterThanComparison"),
            category: "forbidGreaterThanComparison",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }
      const findings: Finding[] = [];
      const visit = (node: Node): void => {
        if (isBinaryExpression(node) && (node.operatorToken.kind === SyntaxKind.GreaterThanToken || node.operatorToken.kind === SyntaxKind.GreaterThanEqualsToken)) {
          const operator = node.operatorToken.kind === SyntaxKind.GreaterThanToken ? ">" : ">=";
          findings.push({
            severity: ctx.severityOf(manifest, "forbidGreaterThanComparison"),
            category: "forbidGreaterThanComparison",
            message: `${file}:${sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getStart(sourceFile)).line + 1}: forbidden \`${operator}\`; use \`${operator === ">" ? "<" : "<="}\``,
          });
        }
        forEachChild(node, visit);
      };
      visit(sourceFile);
      return findings;
    });
  }
});
