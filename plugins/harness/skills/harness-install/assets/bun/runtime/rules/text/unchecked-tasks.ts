#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Compile a pattern that may carry a Python-style inline flag prefix (e.g. `(?m)`)
 * into a JavaScript RegExp by lifting the flags into the constructor argument.
 */
const compilePortableRegex = (pattern: string): RegExp => {
  const inlineFlagMatch = /^\(\?([a-z]+)\)/.exec(pattern);
  if (inlineFlagMatch?.[1]) {
    return new RegExp(
      pattern.slice(inlineFlagMatch[0].length),
      inlineFlagMatch[1],
    );
  }
  return new RegExp(pattern);
};

/**
 * Forbid unchecked task lists in completed plans.
 */
export const uncheckedTasksRule: HarnessCheckRule = {
  category: "uncheckedTasks",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.uncheckedTasks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      typeof ctx.readJsonObject((section as Record<string, unknown>).parameters)
        .directory === "string"
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.uncheckedTasks).parameters,
    );
    const directory =
      typeof parameters.directory === "string" ? parameters.directory : "";
    const patternStr =
      typeof parameters.uncheckedTaskPattern === "string"
        ? parameters.uncheckedTaskPattern
        : "";
    if (!directory || !ctx.isDirectory(directory) || !patternStr) {
      return [];
    }
    const pattern: RegExp = compilePortableRegex(patternStr);
    const [files, warnings] = ctx.walkDirectory(directory);
    return warnings.concat(
      files
        .filter((file) => file.endsWith(".md"))
        .flatMap((file) => {
          const text = ctx.read(file);
          const match = pattern.exec(text);
          if (!match) {
            return [];
          }
          const before = text.slice(0, match.index);
          const startLine = before.split("\n").length;
          const startColumn = match.index - (before.lastIndexOf("\n") + 1) + 1;
          const matchLines = match[0].split("\n");
          return [
            {
              severity: ctx.severityOf("uncheckedTasks"),
              category: "uncheckedTasks",
              message: `completed plan has unchecked tasks: ${file}`,
              file,
              startLine,
              startColumn,
              endLine: startLine + matchLines.length - 1,
              endColumn: matchLines.length === 1 ? startColumn + match[0].length : (matchLines[matchLines.length - 1]?.length ?? 0) + 1,
              fix: {
                description: "check off all remaining tasks",
                safety: "manual",
              },
            },
          ];
        }),
    );
  },
};
