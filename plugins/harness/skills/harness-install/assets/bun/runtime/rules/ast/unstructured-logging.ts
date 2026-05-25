#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  forEachChild,
  isCallExpression,
  isIdentifier,
  isPropertyAccessExpression,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Forbid unstructured logging (console.log, console.error, etc.).
 */
export const unstructuredLoggingRule: HarnessCheckRule = {
  category: "unstructuredLogging",
  applies(ctx: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx
      .stackSources("unstructuredLogging")
      .map((file) => ({ file, text: ctx.read(file) }))
      .filter(({ text }) => text !== "")
      .flatMap(({ file, text }) => {
        const sourceFile: SourceFile = createSourceFile(
          file,
          text,
          SyntaxKind.LatestVersion,
          true,
        );
        const findings: Finding[] = [];
        const logMethods = ["log", "error", "warn", "info", "debug"];
        const visit = (node: Node): void => {
          if (
            isCallExpression(node) &&
            isPropertyAccessExpression(node.expression) &&
            isIdentifier(node.expression.expression) &&
            node.expression.expression.text === "console"
          ) {
            const methodName = node.expression.name?.text;
            if (methodName && logMethods.includes(methodName)) {
              const start = sourceFile.getLineAndCharacterOfPosition(
                node.getStart(sourceFile),
              );
              const end = sourceFile.getLineAndCharacterOfPosition(
                node.getEnd(),
              );
              findings.push({
                severity: ctx.severityOf("unstructuredLogging"),
                category: "unstructuredLogging",
                message: `${file}:${start.line + 1}: unstructured logging \`console.${methodName}\`; use structured logger`,
                file,
                startLine: start.line + 1,
                startColumn: start.character + 1,
                endLine: end.line + 1,
                endColumn: end.character + 1,
                fix: {
                  description: `replace \`console.${methodName}\` with structured logging`,
                  safety: "unsafe",
                  edits: [],
                },
              });
            }
          }
          forEachChild(node, visit);
        };
        visit(sourceFile);
        return findings;
      });
  },
};
