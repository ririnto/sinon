#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require CI configuration to match hook validation commands.
 */
export const ciHookCommandParityRule: HarnessCheckRule = {
    category: "ciHookCommandParity",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.ciHookCommandParity;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        return (
            enabled !== false &&
            typeof ctx.readJsonObject((section as Record<string, unknown>).parameters).referenceHook === "string"
        );
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.ciHookCommandParity).parameters);
        const referenceHook = typeof parameters.referenceHook === "string" ? parameters.referenceHook : "";
        const ciFiles = ctx.readStringArray(parameters.ciFiles);
        if (!ctx.isFile(referenceHook)) {
            return [];
        }
        const refCommand =
            ctx
                .read(referenceHook)
                .split(/\r?\n/)
                .find((line) => line.startsWith("# Harness validation command: "))
                ?.replace("# Harness validation command: ", "")
                .trim() ?? "";
        if (!refCommand) {
            return [];
        }
        return ciFiles
            .filter((ciFile) => ctx.isFile(ciFile) && !ctx.read(ciFile).includes(refCommand))
            .flatMap((ciFile) => [
                {
                    severity: ctx.severityOf("ciHookCommandParity"),
                    category: "ciHookCommandParity",
                    message: `${ciFile}: CI command mismatch — expected ${refCommand}`,
                    file: ciFile,
                    startLine: 1,
                    startColumn: 1,
                    endLine: 1,
                    endColumn: 1,
                },
            ]);
    },
};
