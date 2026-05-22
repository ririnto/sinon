#!/usr/bin/env bun
import { SyntaxKind, createSourceFile, forEachChild, isCallExpression, isIdentifier, isPropertyAccessExpression, type Node, type SourceFile } from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Forbid unstructured logging (console.log, console.error, etc.).
 */
export class ForbidUnstructuredLoggingRule implements HarnessCheckRule {
  static readonly category = "forbidUnstructuredLogging";

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
            severity: this.ctx.severityOf(manifest, ForbidUnstructuredLoggingRule.category),
            category: ForbidUnstructuredLoggingRule.category,
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];
      const logMethods = ["log", "error", "warn", "info", "debug"];

      const visit = (node: Node): void => {
        if (isCallExpression(node)) {
          const expr = node.expression;
          if (isPropertyAccessExpression(expr)) {
            if (isIdentifier(expr.expression) && expr.expression.text === "console") {
              const methodName = expr.name?.text;
              if (methodName && logMethods.includes(methodName)) {
                const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
                findings.push({
                  severity: this.ctx.severityOf(manifest, ForbidUnstructuredLoggingRule.category),
                  category: ForbidUnstructuredLoggingRule.category,
                  message: `${file}:${line + 1}: unstructured logging \`console.${methodName}\`; use structured logger`,
                });
              }
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
