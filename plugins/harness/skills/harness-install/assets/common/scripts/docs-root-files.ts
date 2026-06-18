#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint@0.40.0";

const allowedDocsRootFiles = new Set([
  "docs/DESIGN.md",
  "docs/FRONTEND.md",
  "docs/PLANS.md",
  "docs/PRODUCT_SENSE.md",
  "docs/QUALITY_SCORE.md",
  "docs/RELIABILITY.md",
  "docs/SECURITY.md",
]);

/**
 * Markdownlint rule limiting direct Markdown children under docs/.
 */
const rule: Rule = {
  names: ["docs/root-files"],
  description: "Docs root allows only approved direct Markdown files",
  tags: ["docs"],
  parser: "none",
  function: (params, onError) => {
    const name = params.name.replaceAll("\\", "/");
    if (!/^docs\/[^/]+\.md$/.test(name) || allowedDocsRootFiles.has(name)) {
      return;
    }
    onError({
      lineNumber: 1,
      detail: `${name} belongs under an approved docs/ subdirectory.`,
    });
  },
};

export default rule;
