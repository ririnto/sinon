#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  isCallExpression,
  isIdentifier,
  isNewExpression,
  isPropertyAccessExpression,
  SyntaxKind,
} from "typescript@6.0.3";
import { astChildrenOf } from "../../core/ast-traversal";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Forbid mutable collection constructors.
 */
export const mutableCollectionRule: HarnessCheckRule = {
  category: "mutableCollection",
  applies(_: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx.stackSources("mutableCollection").flatMap((file) => {
      const text = ctx.read(file);
      if (!text) {
        return [];
      }
      const sourceFile: SourceFile = createSourceFile(
        file,
        text,
        SyntaxKind.LatestVersion,
        true,
      );
      const parameters = ctx.readJsonObject(
        ctx.categoryObject("mutableCollection").parameters,
      );
      const forbiddenConstructors = configured(
        ctx,
        parameters.forbiddenConstructors,
        ["Array", "Map", "Set", "WeakMap", "WeakSet"],
      );
      const accumulationMethods = configured(
        ctx,
        parameters.accumulationMethods,
        ["push", "add", "set"],
      );
      const allowedOneShotPatterns = configured(
        ctx,
        parameters.allowedOneShotPatterns,
        [],
      );
      const visit = (node: Node): readonly Finding[] => [
        ...findingForNode(
          ctx,
          file,
          sourceFile,
          node,
          forbiddenConstructors,
          accumulationMethods,
          allowedOneShotPatterns,
        ),
        ...astChildrenOf(node).flatMap(visit),
      ];
      return visit(sourceFile);
    });
  },
};

function configured(
  ctx: RuleContext,
  value: unknown,
  defaults: readonly string[],
): readonly string[] {
  const configuredValues = ctx.readStringArray(value);
  return configuredValues.length > 0 ? configuredValues : defaults;
}

function findingForNode(
  ctx: RuleContext,
  file: string,
  sourceFile: SourceFile,
  node: Node,
  forbiddenConstructors: readonly string[],
  accumulationMethods: readonly string[],
  allowedOneShotPatterns: readonly string[],
): readonly Finding[] {
  if (isNewExpression(node) && isIdentifier(node.expression)) {
    const name = node.expression.text;
    if (
      forbiddenConstructors.includes(name) &&
      !isAllowedOneShot(node, allowedOneShotPatterns)
    ) {
      return [mutableFinding(ctx, file, sourceFile, node, `new ${name}`)];
    }
  }
  if (
    isCallExpression(node) &&
    isPropertyAccessExpression(node.expression) &&
    accumulationMethods.includes(node.expression.name.text)
  ) {
    return [
      mutableFinding(ctx, file, sourceFile, node, node.expression.name.text),
    ];
  }
  return [];
}

function isAllowedOneShot(
  node: Node,
  allowedOneShotPatterns: readonly string[],
): boolean {
  const text = node.parent?.getText() ?? "";
  return allowedOneShotPatterns.some((pattern) => {
    if (pattern === "Array.from(new Set(...))") {
      return text.startsWith("Array.from(new Set(");
    }
    if (pattern === "Array.from(new Map(...).values())") {
      return text.startsWith("Array.from(new Map(") && text.includes(".values()");
    }
    if (pattern === "new Set(readonlyArray)") {
      return node.getText().startsWith("new Set(");
    }
    return false;
  });
}

function mutableFinding(
  ctx: RuleContext,
  file: string,
  sourceFile: SourceFile,
  node: Node,
  name: string,
): Finding {
  const start = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
  const end = sourceFile.getLineAndCharacterOfPosition(node.getEnd());
  return {
    severity: ctx.severityOf("mutableCollection"),
    category: "mutableCollection",
    message: `${file}:${start.line + 1}: mutable collection construction \`${name}\`; use functional alternative`,
    file,
    startLine: start.line + 1,
    startColumn: start.character + 1,
    endLine: end.line + 1,
    endColumn: end.character + 1,
    fix: {
      description: `replace \`${name}\` with functional alternative`,
      safety: "unsafe",
      edits: [],
    },
  };
}
