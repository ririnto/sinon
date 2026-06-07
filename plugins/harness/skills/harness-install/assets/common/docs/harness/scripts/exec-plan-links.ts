#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint@0.40.0";

const datedExecPlanPattern = /(^|[[(<`"\s/])([0-9]{4}-[0-9]{2}-[0-9]{2}-[a-z0-9][a-z0-9-]*\.md)([^a-zA-Z0-9_.-]|$)/;

/**
 * Markdownlint rule rejecting durable links to dated execution-plan state files.
 */
const rule: Rule = {
  names: ["docs/exec-plans/links"],
  description: "Durable Markdown must not link to dated execution-plan state files",
  tags: ["links"],
  parser: "none",
  function: (params, onError) => {
    const name = params.name.replaceAll("\\", "/");
    if (name.startsWith("docs/exec-plans/") || name.includes("/docs/exec-plans/")) {
      return;
    }
    params.lines.forEach((line, index) => {
      const match = line.match(datedExecPlanPattern);
      if (!match || match[2] === "tech-debt-tracker.md") {
        return;
      }
      onError({
        lineNumber: index + 1,
        detail: `Reference ${match[2]} points to a removable exec-plan state file; link docs/exec-plans/tech-debt-tracker.md or durable design/product docs instead.`,
        context: line.trim(),
      });
    });
  },
};

export default rule;
