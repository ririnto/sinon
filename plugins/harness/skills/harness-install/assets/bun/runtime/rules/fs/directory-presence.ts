#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require specified directories to exist.
 */
export const directoryPresenceRule: HarnessCheckRule = {
    category: "directoryPresence",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.directoryPresence;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        if (enabled === false) {
            return false;
        }
        const entry = ctx.readJsonObject((section as Record<string, unknown>).parameters);
        return ctx.readStringArray(entry.paths).length > 0;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const paths = ctx.readStringArray(
            ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.directoryPresence).parameters).paths,
        );
        const severity = ctx.severityOf("directoryPresence");
        return paths.flatMap((path) => {
            if (ctx.isSymlink(path)) {
                return [
                    {
                        severity,
                        category: "directoryPresence",
                        message: `symlink directory is not allowed: ${path}`,
                        file: path,
                        startLine: 1,
                        startColumn: 1,
                        endLine: 1,
                        endColumn: 1,
                    },
                ];
            }
            if (ctx.isDirectory(path)) {
                return [];
            }
            return [
                {
                    severity,
                    category: "directoryPresence",
                    message: `missing directory: ${path}`,
                    file: path,
                    startLine: 1,
                    startColumn: 1,
                    endLine: 1,
                    endColumn: 1,
                },
            ];
        });
    },
};
