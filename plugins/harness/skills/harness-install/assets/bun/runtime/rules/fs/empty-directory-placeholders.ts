#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { readdirSync } from "node:fs";
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require .gitkeep placeholder or real files in empty directories.
 */
export const emptyDirectoryPlaceholdersRule: HarnessCheckRule = {
    category: "emptyDirectoryPlaceholders",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.emptyDirectoryPlaceholders;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        return (
            enabled !== false &&
            ctx.readStringArray(ctx.readJsonObject((section as Record<string, unknown>).parameters).directories)
                .length > 0
        );
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(
            ctx.readJsonObject(ctx.manifest.raw.emptyDirectoryPlaceholders).parameters,
        );
        const directories = ctx.readStringArray(parameters.directories);
        return directories
            .filter((dir) => ctx.isDirectory(dir))
            .flatMap((dir) => {
                const realFiles = readdirSync(ctx.pathOf(dir)).filter((e) => e !== ".gitkeep");
                if (realFiles.length > 0 || ctx.isFile(`${dir}/.gitkeep`)) {
                    return [];
                }
                const gitkeepPath = `${dir}/.gitkeep`;
                return [
                    {
                        severity: ctx.severityOf("emptyDirectoryPlaceholders"),
                        category: "emptyDirectoryPlaceholders",
                        message: `empty directory must keep placeholder or real files: ${dir}`,
                        file: gitkeepPath,
                        startLine: 1,
                        startColumn: 1,
                        endLine: 1,
                        endColumn: 1,
                        fix: {
                            description: `insert \`.gitkeep\` file in \`${dir}\``,
                            safety: "safe",
                            edits: [
                                {
                                    file: gitkeepPath,
                                    startLine: 1,
                                    startColumn: 1,
                                    endLine: 1,
                                    endColumn: 1,
                                    replacement: "",
                                },
                            ],
                        },
                    },
                ];
            });
    },
};
