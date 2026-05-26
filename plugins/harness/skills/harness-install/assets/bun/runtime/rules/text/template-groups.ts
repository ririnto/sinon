#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type { Finding, HarnessCheckRule, RuleContext } from "../harness-check-rule";

/**
 * Require specified template groups to exist.
 */
export const templateGroupsRule: HarnessCheckRule = {
    category: "templateGroups",
    applies(ctx: RuleContext): boolean {
        const section = ctx.manifest.raw.templateGroups;
        if (typeof section !== "object" || section === null) {
            return false;
        }
        const enabled = (section as { enabled?: unknown }).enabled;
        if (enabled === false) {
            return false;
        }
        const entry = ctx.readJsonObject((section as Record<string, unknown>).parameters);
        return ctx.readStringArray(entry.groups).length > 0;
    },

    validate(ctx: RuleContext): readonly Finding[] {
        const parameters = ctx.readJsonObject(ctx.readJsonObject(ctx.manifest.raw.templateGroups).parameters);
        const targetRoot = typeof parameters.targetRoot === "string" ? parameters.targetRoot : "";
        const groups = ctx.readStringArray(parameters.groups);
        return groups.flatMap((group) => {
            const path = `${targetRoot}/${group}`;
            return ctx.isDirectory(path)
                ? []
                : [
                      {
                          severity: ctx.severityOf("templateGroups"),
                          category: "templateGroups",
                          message: `missing template group: ${path}`,
                          file: path,
                          startLine: 1,
                          startColumn: 1,
                          endLine: 1,
                          endColumn: 1,
                          fix: {
                              description: `create template group directory: ${path}`,
                              safety: "manual",
                          },
                      },
                  ];
        });
    },
};
