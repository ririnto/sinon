#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint";

const datedExecPlanPattern =
  /(?<prefix>^|[[(<`"\s/])(?<filename>[0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9][a-z0-9-]*\.md)(?<suffix>[^a-zA-Z0-9_.-]|$)/u;

/**
 * Markdownlint rule rejecting durable links to dated execution-plan state files.
 */
const rule: Rule = {
  description:
    "Durable Markdown must not link to dated execution-plan state files",
  function: (params, onError) => {
    const name = params.name.replaceAll("\\", "/");
    if (
      name.startsWith("docs/exec-plans/") ||
      name.includes("/docs/exec-plans/")
    ) {
      return;
    }
    for (const [index, line] of params.lines.entries()) {
      const match = line.match(datedExecPlanPattern);
      const filename = match?.groups?.filename;
      if (filename === undefined || filename === "tech-debt-tracker.md") {
        continue;
      }
      onError({
        context: line.trim(),
        detail: `Reference ${filename} points to a removable exec-plan state file; link docs/exec-plans/tech-debt-tracker.md or durable design/product docs instead.`,
        lineNumber: index + 1
      });
    }
  },
  names: ["docs/exec-plans/links"],
  parser: "none",
  tags: ["links"]
};

export default rule;
