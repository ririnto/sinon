#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require specified headings in documentation files.
 */
export const docHeadingsRule: HarnessCheckRule = {
  category: "docHeadings",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.docHeadings;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readStringArray(
        ctx.readJsonObject((section as Record<string, unknown>).parameters)
          .headings,
      ).length > 0
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.docHeadings).parameters,
    );
    const sourceCategory =
      typeof parameters.sourceFilesFromCategory === "string"
        ? parameters.sourceFilesFromCategory
        : "filePresence";
    const sourceFilter = ctx.readJsonObject(parameters.sourceFilter);
    const prefix =
      typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
    const suffix =
      typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
    const headings = ctx.readStringArray(parameters.headings);
    const filteredFiles = ctx
      .readStringArray(
        ctx.readJsonObject(
          ctx.readJsonObject(ctx.manifest.raw[sourceCategory]).parameters,
        ).paths,
      )
      .filter(
        (f) =>
          !prefix || (f.startsWith(prefix) && (!suffix || f.endsWith(suffix))),
      );
    return filteredFiles
      .filter((file) => ctx.isFile(file))
      .flatMap((file) => {
        const text = ctx.read(file);
        return headings
          .filter((heading) => !text.includes(heading))
          .flatMap((heading) => [
            {
              severity: ctx.severityOf("docHeadings"),
              category: "docHeadings",
              message: `doc missing ${heading}: ${file}`,
              file,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: 1,
              fix: {
                description: `add heading "${heading}"`,
                safety: "manual",
              },
            },
          ]);
      });
  },
};
