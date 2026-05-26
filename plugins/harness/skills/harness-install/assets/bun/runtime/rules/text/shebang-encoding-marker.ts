#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require shebang scripts under `parameters.sourceRoots` to declare a
 * language-appropriate UTF-8 encoding marker on the second line.
 *
 * The marker for each extension is configured via `parameters.markers`, an
 * object keyed by file extension (without the leading dot). When
 * `parameters.requireShebang` is true (default), files whose first line is
 * not a shebang are skipped silently. Additional shebang prefixes beyond
 * the default `#!` may be declared via `parameters.additionalShebangPrefixes`.
 */
export const shebangEncodingMarkerRule: HarnessCheckRule = {
    category: "shebangEncodingMarker",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.shebangEncodingMarker;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        if (enabled === false) {
            return false;
        }
        const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
        return ctx.readStringArray(parameters.sourceRoots).length > 0;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.shebangEncodingMarker).parameters);
        const markers = ctx.readJsonObject(parameters.markers);
        const messages = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.shebangEncodingMarker).messages);
        const defaultTemplate =
            typeof messages.default === "string"
                ? messages.default
                : "shebang script missing UTF-8 encoding marker; expected `{expected}` on line 2";
        const missingLineTemplate =
            typeof messages.missingLine === "string"
                ? messages.missingLine
                : "shebang script has no line 2; expected `{expected}`";
        const wrongMarkerTemplate =
            typeof messages.wrongMarker === "string"
                ? messages.wrongMarker
                : "shebang script declares wrong encoding marker; found `{actual}`, expected `{expected}`";
        const requireShebang = parameters.requireShebang !== false;
        const extraPrefixes = ctx.readStringArray(parameters.additionalShebangPrefixes);
        const shebangPrefixes: readonly string[] = ["#!"].concat(extraPrefixes);
        const severity = ctx.severityOf("shebangEncodingMarker");
        const category = "shebangEncodingMarker";
        return ctx.stackSources("shebangEncodingMarker").flatMap((file): readonly Finding[] => {
            const extension = file.slice(file.lastIndexOf(".") + 1);
            const expectedRaw = markers[extension];
            if (typeof expectedRaw !== "string" || expectedRaw.length === 0) {
                return [];
            }
            const expected = expectedRaw;
            const text = ctx.read(file);
            if (text.length === 0) {
                return [];
            }
            const lines = text.split(/\r?\n/);
            const first = lines[0] ?? "";
            const hasShebang = shebangPrefixes.some((prefix) => first.startsWith(prefix));
            if (!hasShebang && requireShebang) {
                return [];
            }
            if (lines.length < 2) {
                return [
                    {
                        severity,
                        category,
                        message: missingLineTemplate.replace("{expected}", expected),
                        file,
                        startLine: 2,
                        startColumn: 1,
                        endLine: 2,
                        endColumn: 1,
                        fix: {
                            description: `insert \`${expected}\` as line 2`,
                            safety: "safe",
                            edits: [
                                {
                                    file,
                                    startLine: 2,
                                    startColumn: 1,
                                    endLine: 2,
                                    endColumn: 1,
                                    replacement: `${expected}\n`,
                                },
                            ],
                        },
                    },
                ];
            }
            const actual = lines[1] ?? "";
            if (actual === expected) {
                return [];
            }
            const endColumn = Math.max(actual.length, 1) + 1;
            const messageTemplate = actual.trim().length === 0 ? defaultTemplate : wrongMarkerTemplate;
            return [
                {
                    severity,
                    category,
                    message: messageTemplate.replace("{actual}", actual).replace("{expected}", expected),
                    file,
                    startLine: 2,
                    startColumn: 1,
                    endLine: 2,
                    endColumn,
                    fix: {
                        description: `replace line 2 with \`${expected}\``,
                        safety: "safe",
                        edits: [
                            {
                                file,
                                startLine: 2,
                                startColumn: 1,
                                endLine: 2,
                                endColumn,
                                replacement: expected,
                            },
                        ],
                    },
                },
            ];
        });
    },
};
