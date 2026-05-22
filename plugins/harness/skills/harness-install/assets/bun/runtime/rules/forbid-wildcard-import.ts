#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isImportDeclaration, isNamespaceImport, isStringLiteral, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid wildcard imports.
 */
export const forbidWildcardImportRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(_manifest: HarnessManifest): boolean {
    return true;
  },
  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const sources = ctx.stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
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
            severity: ctx.severityOf(manifest, "forbidWildcardImport"),
            category: "forbidWildcardImport",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }
      const findings: Finding[] = [];
      const visit = (node: Node): void => {
        if (isImportDeclaration(node) && node.importClause && node.importClause.namedBindings && isNamespaceImport(node.importClause.namedBindings)) {
          const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
          findings.push({
            severity: ctx.severityOf(manifest, "forbidWildcardImport"),
            category: "forbidWildcardImport",
            message: `${file}:${line + 1}: wildcard import \`import * as\` forbidden; import explicit symbols`,
          });
        }
        forEachChild(node, visit);
      };
      visit(sourceFile);
      return findings;
    });
  },
});
