#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Node, SourceFile } from "typescript@6.0.3";
import {
    createSourceFile,
    forEachChild,
    getLeadingCommentRanges,
    getTrailingCommentRanges,
    SyntaxKind,
} from "typescript@6.0.3";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Forbid TypeScript directive and eslint-disable suppressions.
 *
 * Detects:
 * - TypeScript ignore directives
 * - TypeScript expect-error directives
 * - TypeScript nocheck directives
 * - // eslint-disable-line ...
 * - // eslint-disable-next-line ...
 * - block eslint-disable directives
 *
 * Configurable via parameters.forbiddenSuppressions and parameters.allowedSuppressions.
 */
export const uncheckedCastSuppressionRule: HarnessCheckRule = {
    category: "uncheckedCastSuppression",
    applies(_: RuleContext): boolean {
        return true;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const forbidden = resolveForbiddenSuppressions(ctx);
        const allowed = resolveAllowedSuppressions(ctx);
        return ctx.stackSources("uncheckedCastSuppression").flatMap((file) => {
            const text = ctx.read(file);
            if (!text) {
                return [];
            }

            const sourceFile: SourceFile = createSourceFile(file, text, SyntaxKind.LatestVersion, true);
            const findings: Finding[] = [];
            const visitedComments = new Set<string>();
            const inspectComment = (pos: number, end: number): void => {
                const key = `${pos}:${end}`;
                if (visitedComments.has(key)) {
                    return;
                }
                visitedComments.add(key);
                const commentText = text.slice(pos, end);
                for (const token of forbidden) {
                    if (!allowed.includes(token) && matchesSuppressionDirective(commentText, token)) {
                        const start = sourceFile.getLineAndCharacterOfPosition(pos);
                        findings.push({
                            severity: ctx.severityOf("uncheckedCastSuppression"),
                            category: "uncheckedCastSuppression",
                            message: `avoid suppression of forbidden tokens (\`${commentText.trim()}\`); refactor to explicit handling`,
                            file,
                            startLine: start.line + 1,
                            startColumn: start.character + 1,
                        });
                    }
                }
            };
            const inspectRanges = (ranges: readonly { pos: number; end: number }[] | undefined): void => {
                ranges?.forEach((range) => {
                    inspectComment(range.pos, range.end);
                });
            };
            const visit = (node: Node): void => {
                inspectRanges(getLeadingCommentRanges(text, node.pos));
                inspectRanges(getTrailingCommentRanges(text, node.end));
                forEachChild(node, visit);
            };
            visit(sourceFile);
            return findings;
        });
    },
};

/**
 * Resolves forbiddenSuppressions from manifest parameters.
 *
 * Reads parameters.forbiddenSuppressions from the manifest section,
 * defaulting to TypeScript ignore, expect-error, and nocheck directives when missing.
 */
function resolveForbiddenSuppressions(ctx: RuleContext): string[] {
    const params = ctx.readJsonObject(ctx.categoryObject("uncheckedCastSuppression").parameters);
    const tokens = ctx.readStringArray(params.forbiddenSuppressions);
    if (tokens.length === 0) {
        return ["@ts-ignore", "@ts-expect-error", "@ts-nocheck"];
    }
    return Array.from(tokens);
}

/**
 * Resolves allowedSuppressions from manifest parameters.
 */
function resolveAllowedSuppressions(ctx: RuleContext): string[] {
    const params = ctx.readJsonObject(ctx.categoryObject("uncheckedCastSuppression").parameters);
    return Array.from(ctx.readStringArray(params.allowedSuppressions));
}

/**
 * Returns whether a comment starts with a configured suppression directive.
 */
function matchesSuppressionDirective(commentText: string, token: string): boolean {
    return directiveLines(commentText).some((line) => {
        if (!line.startsWith(token)) {
            return false;
        }
        const next = line.slice(token.length, token.length + 1);
        return next === "" || /\s|:/.test(next);
    });
}

/**
 * Extracts directive-bearing logical lines from TypeScript comments.
 */
function directiveLines(commentText: string): string[] {
    return commentText
        .replace(/^\/\//, "")
        .replace(/^\/\*/, "")
        .replace(/\*\/$/, "")
        .split("\n")
        .map((line) => line.replace(/^\s*\*/, "").trim())
        .filter((line) => line !== "");
}

export const HARNESS_CHECK_RULES = [uncheckedCastSuppressionRule];
