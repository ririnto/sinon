#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isCatchClause, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid empty catch blocks.
 */
export class ForbidEmptyCatchBlockRule implements HarnessCheckRule {
  static readonly category = "forbidEmptyCatchBlock";

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
            severity: this.ctx.severityOf(manifest, ForbidEmptyCatchBlockRule.category),
            category: ForbidEmptyCatchBlockRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: Node): void => {
        if (isCatchClause(node)) {
          if (node.block.statements.length === 0) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
            findings.push({
              severity: this.ctx.severityOf(manifest, ForbidEmptyCatchBlockRule.category),
              category: ForbidEmptyCatchBlockRule.category,
              message: `${file}:${line + 1}: empty catch block; handle, rethrow, or convert to a Finding`,
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
