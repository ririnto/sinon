#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isIdentifier, isNewExpression, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid mutable collection constructors.
 */
export class ForbidMutableCollectionRule implements HarnessCheckRule {
  static readonly category = "forbidMutableCollection";

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
            severity: this.ctx.severityOf(manifest, ForbidMutableCollectionRule.category),
            category: ForbidMutableCollectionRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: Node): void => {
        if (isNewExpression(node)) {
          const expr = node.expression;
          if (isIdentifier(expr)) {
            const name = expr.text;
            if (name === "Array" || name === "Map" || name === "Set") {
              const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
              findings.push({
                severity: this.ctx.severityOf(manifest, ForbidMutableCollectionRule.category),
                category: ForbidMutableCollectionRule.category,
                message: `${file}:${line + 1}: mutable collection construction \`new ${name}\`; use functional alternative`,
              });
            }
          }
        }
        forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  }
}
