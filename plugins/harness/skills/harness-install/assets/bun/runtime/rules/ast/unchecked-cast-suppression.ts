#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
  createSourceFile,
  SyntaxKind,
} from "typescript@6.0.3";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Forbid @ts-ignore, @ts-expect-error, @ts-nocheck, and eslint-disable suppressions.
 *
 * Detects:
 * - // @ts-ignore
 * - // @ts-expect-error
 * - // @ts-nocheck
 * - // eslint-disable-line ...
 * - // eslint-disable-next-line ...
 * - /* eslint-disable ... */
 *
 * Configurable via parameters.forbiddenSuppressions (defaults to ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"]).
 */
export const uncheckedCastSuppressionRule: HarnessCheckRule = {
  category: "uncheckedCastSuppression",
  applies(ctx: RuleContext): boolean {
    return true;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const forbidden = resolveForbiddenSuppressions(ctx);
    return ctx.stackSources("uncheckedCastSuppression").flatMap((file) => {
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

      return text.split("\n").flatMap((line, index) => {
        const trimmed = line.trim();
        for (const token of forbidden) {
          if (trimmed.includes(token)) {
            return [{
              severity: ctx.severityOf("uncheckedCastSuppression"),
              category: "uncheckedCastSuppression",
              message: `${file}:${index + 1}: avoid suppression of forbidden tokens (\`${trimmed}\`); refactor to explicit handling`,
              file,
            }];
          }
        }
        return [];
      });
    });
  },
};

/**
 * Resolves forbiddenSuppressions from manifest parameters.
 *
 * Reads parameters.forbiddenSuppressions from the manifest section,
 * defaulting to ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"] when missing.
 */
function resolveForbiddenSuppressions(ctx: RuleContext): string[] {
  const manifest = ctx.manifest();
  const section = manifest["uncheckedCastSuppression"] as any;
  if (!section) {
    return ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"];
  }
  const params = section["parameters"] as any;
  if (!params) {
    return ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"];
  }
  const tokens = params["forbiddenSuppressions"] as string[] | undefined;
  if (!Array.isArray(tokens) || tokens.length === 0) {
    return ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"];
  }
  return tokens;
}

export const HARNESS_CHECK_RULES = [uncheckedCastSuppressionRule];
