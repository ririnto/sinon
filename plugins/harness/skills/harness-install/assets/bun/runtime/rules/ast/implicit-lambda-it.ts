#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Forbid implicit `it` lambda parameters in Kotlin.
 */
export const implicitLambdaItRule: HarnessCheckRule = {
  category: "implicitLambdaIt",
  applies(ctx: RuleContext): boolean {
    return false;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    return ctx.stackSources("implicitLambdaIt").flatMap((file) =>
      ctx
        .read(file)
        .split(/\r?\n/)
        .flatMap((line, index) => {
          const stripped = line
            .replace(/"[^"\\]*(?:\\.[^"\\]*)*"/g, "")
            .replace(/\/\/.*$/, "");
          return /\bit\b\s*\./.test(stripped) ||
            /\bit\b\s*\}/.test(stripped) ||
            /->\s*it\b/.test(stripped)
            ? [
                {
                  severity: ctx.severityOf("implicitLambdaIt"),
                  category: "implicitLambdaIt",
                  message: `Kotlin file ${file} uses implicit \`it\` lambda parameter at line ${index + 1}; use an explicit name`,
                  file,
                  startLine: index + 1,
                  startColumn: 1,
                  endLine: index + 1,
                  endColumn: line.length + 1,
                  fix: {
                    description: "replace implicit `it` with an explicit lambda parameter name",
                    safety: "unsafe",
                    edits: [],
                  },
                },
              ]
            : [];
        }),
    );
  },
};
