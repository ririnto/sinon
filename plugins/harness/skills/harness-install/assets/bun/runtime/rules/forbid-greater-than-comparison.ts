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
export class ForbidGreaterThanComparisonRule implements HarnessCheckRule {
  static readonly category = "forbidGreaterThanComparison";

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
            severity: this.ctx.severityOf(manifest, ForbidGreaterThanComparisonRule.category),
            category: ForbidGreaterThanComparisonRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }
      const findings: Finding[] = [];
      const visit = (node: Node): void => {
        if (isBinaryExpression(node)) {
          const kind = node.operatorToken.kind;
          if (kind === SyntaxKind.GreaterThanToken || kind === SyntaxKind.GreaterThanEqualsToken) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getStart(sourceFile));
            const operator = kind === SyntaxKind.GreaterThanToken ? ">" : ">=";
            findings.push({
              severity: this.ctx.severityOf(manifest, ForbidGreaterThanComparisonRule.category),
              category: ForbidGreaterThanComparisonRule.category,
              message: `${file}:${line + 1}: forbidden \`${operator}\`; use \`${operator === ">" ? "<" : "<="}\``,
            });
          }
        }
        forEachChild(node, visit);
      };
      visit(sourceFile);
      return findings;
    });
  }
}
