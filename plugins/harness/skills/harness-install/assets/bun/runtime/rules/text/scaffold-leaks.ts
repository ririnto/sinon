#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Remove Markdown code blocks and inline code spans before prose-level checks.
 */
const stripMarkdownCode = (text: string): string => {
  const lines: string[] = [];
  let inFence = false;
  let fenceMarker = "";
  for (const line of text.split(/\r?\n/)) {
    const fenceMatch = /^( {0,3})(`{3,}|~{3,})/.exec(line);
    if (fenceMatch) {
      const marker = fenceMatch[2]?.charAt(0) ?? "";
      if (!inFence) {
        inFence = true;
        fenceMarker = marker;
        lines.push("");
      } else if (marker === fenceMarker) {
        inFence = false;
        lines.push("");
      }
    } else if (inFence) {
      lines.push("");
    } else {
      lines.push(line.replace(/`+[^`\n]*`+/g, ""));
    }
  }
  return lines.join("\n");
};

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
 * Scaffold leak regex paired with its reporting label.
 */
interface ScaffoldPattern {
  readonly regex: RegExp;
  readonly label: string;
}

/**
 * Forbid scaffold/placeholder patterns in active assets.
 */
export const scaffoldLeaksRule: HarnessCheckRule = {
  category: "scaffoldLeaks",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.scaffoldLeaks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readStringArray(
        ctx.readJsonObject(
          ctx.readJsonObject((section as Record<string, unknown>).parameters)
            .scope,
        ).bases,
      ).length > 0
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.scaffoldLeaks).parameters,
    );
    const scope = ctx.readJsonObject(parameters.scope);
    const bases = ctx.readStringArray(scope.bases);
    const excludedSubtrees = ctx.readStringArray(scope.excludedSubtrees);
    const extensions = ctx.readStringArray(scope.extensions);
    const patterns: readonly ScaffoldPattern[] = Array.isArray(
      parameters.patterns,
    )
      ? (parameters.patterns as unknown[])
          .filter(
            (item): item is Record<string, unknown> =>
              typeof item === "object" && item !== null,
          )
          .map((obj) => ({
            patternStr: typeof obj.pattern === "string" ? obj.pattern : "",
            labelStr: typeof obj.label === "string" ? obj.label : "",
          }))
          .filter(
            ({ patternStr, labelStr }) => patternStr !== "" && labelStr !== "",
          )
          .map(
            ({ patternStr, labelStr }) =>
              ({ regex: compilePortableRegex(patternStr), label: labelStr }),
          )
      : [];
    return bases.flatMap((base) => {
      const [files, warnings] = ctx.collectFilesUnder(base);
      return warnings.concat(
        files
          .filter((file) => {
            const isExcluded = excludedSubtrees.some(
              (subtree) => file === subtree || file.startsWith(`${subtree}/`),
            );
            const ext = /\.([a-z0-9]+)$/.exec(file)?.[1] ?? "";
            return !isExcluded && extensions.includes(ext);
          })
          .flatMap((file) => {
            const text = ctx.read(file);
            return patterns
              .filter((patternEntry) => patternEntry.regex.test(stripMarkdownCode(text)))
              .flatMap((patternEntry) => {
                const match = patternEntry.regex.exec(stripMarkdownCode(text));
                const positions = match
                  ? (() => {
                      const before = text.slice(0, match.index);
                      const startLinVal = before.split("\n").length;
                      const startColVal = match.index - (before.lastIndexOf("\n") + 1) + 1;
                      const matchLines = match[0].split("\n");
                      const endLinVal = startLinVal + matchLines.length - 1;
                      const endColVal = matchLines.length === 1
                        ? startColVal + match[0].length
                        : (matchLines[matchLines.length - 1]?.length ?? 0) + 1;
                      return {
                        startLine: startLinVal,
                        startColumn: startColVal,
                        endLine: endLinVal,
                        endColumn: endColVal,
                      };
                    })()
                  : { startLine: 1, startColumn: 1, endLine: 1, endColumn: 1 };
                return [
                  {
                    severity: ctx.severityOf("scaffoldLeaks"),
                    category: "scaffoldLeaks",
                    message: `${patternEntry.label} in active asset: ${file}`,
                    file,
                    ...positions,
                    fix: {
                      description: `replace scaffold pattern: ${patternEntry.label}`,
                      safety: "manual",
                    },
                  },
                ];
              });
          }),
      );
    });
  },
};
