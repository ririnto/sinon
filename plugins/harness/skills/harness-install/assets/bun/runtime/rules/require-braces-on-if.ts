#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isBlock, isIfStatement, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require braced blocks on if/else statements.
 */
export class RequireBracesOnIfRule implements HarnessCheckRule {
  static readonly category = "requireBracesOnIf";

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
            severity: this.ctx.severityOf(manifest, RequireBracesOnIfRule.category),
            category: RequireBracesOnIfRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: Node): void => {
        if (isIfStatement(node)) {
          if (!isBlock(node.thenStatement)) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
            findings.push({
              severity: this.ctx.severityOf(manifest, RequireBracesOnIfRule.category),
              category: RequireBracesOnIfRule.category,
              message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
            });
          }
          if (node.elseStatement && !isBlock(node.elseStatement) && !isIfStatement(node.elseStatement)) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.elseStatement.getStart(sourceFile));
            findings.push({
              severity: this.ctx.severityOf(manifest, RequireBracesOnIfRule.category),
              category: RequireBracesOnIfRule.category,
              message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
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
