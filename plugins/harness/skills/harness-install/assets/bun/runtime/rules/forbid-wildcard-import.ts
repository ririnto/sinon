#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isImportDeclaration, isNamespaceImport, isStringLiteral, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid wildcard imports.
 */
export class ForbidWildcardImportRule implements HarnessCheckRule {
  static readonly category = "forbidWildcardImport";

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
            severity: this.ctx.severityOf(manifest, ForbidWildcardImportRule.category),
            category: ForbidWildcardImportRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: Node): void => {
        if (isImportDeclaration(node)) {
          if (node.importClause && node.importClause.namedBindings) {
            const bindings = node.importClause.namedBindings;
            if (isNamespaceImport(bindings)) {
              const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
              findings.push({
                severity: this.ctx.severityOf(manifest, ForbidWildcardImportRule.category),
                category: ForbidWildcardImportRule.category,
                message: `${file}:${line + 1}: wildcard import \`import * as\` forbidden; import explicit symbols`,
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
