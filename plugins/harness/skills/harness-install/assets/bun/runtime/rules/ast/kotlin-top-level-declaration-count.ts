#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require Kotlin files to have exactly one top-level declaration.
 */
export const kotlinTopLevelDeclarationCountRule: HarnessCheckRule = {
  category: "kotlinTopLevelDeclarationCount",
  applies(ctx: RuleContext): boolean {
    return false;
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const declRegex = /^(class|interface|enum class|data class|sealed class|object|abstract class|val|var|fun|typealias)\s/gm;
    return ctx
      .stackSources("kotlinTopLevelDeclarationCount")
      .flatMap((file) => {
        const text = ctx.read(file);
        const matches = Array.from(text.matchAll(declRegex));
        return matches.length !== 1
          ? [
              {
                severity: ctx.severityOf("kotlinTopLevelDeclarationCount"),
                category: "kotlinTopLevelDeclarationCount",
                message: `Kotlin file must have exactly 1 top-level declaration: ${file} (found ${matches.length})`,
                file,
                startLine: 1,
                startColumn: 1,
                endLine: 1,
                endColumn: 1,
                fix: {
                  description: "reorganize Kotlin file to have exactly one top-level declaration",
                  safety: "manual",
                  edits: [],
                },
              },
            ]
          : [];
      });
  },
};
