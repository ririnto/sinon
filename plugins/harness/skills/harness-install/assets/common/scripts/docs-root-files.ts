#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint@0.41.0";

const allowedDocsRootFiles = new Set([
  "docs/DESIGN.md",
  "docs/FRONTEND.md",
  "docs/PLANS.md",
  "docs/PRODUCT_SENSE.md",
  "docs/QUALITY_SCORE.md",
  "docs/RELIABILITY.md",
  "docs/SECURITY.md",
]);

const allowedDocsDirectories = [
  "docs/design-docs",
  "docs/exec-plans",
  "docs/generated",
  "docs/product-specs",
  "docs/references",
  "docs/templates",
] as const;

/**
 * Markdownlint rule limiting Markdown paths under docs/.
 */
const rule: Rule = {
  names: ["docs/root-files"],
  description: "Docs allows only approved root files and subdirectories",
  tags: ["docs"],
  parser: "none",
  function: (params, onError) => {
    const name = params.name.replaceAll("\\", "/");
    if (!name.startsWith("docs/")) {
      return;
    }
    if (/^docs\/[^/]+\.md$/.test(name)) {
      if (allowedDocsRootFiles.has(name)) {
        return;
      }
      onError({
        lineNumber: 1,
        detail: `${name} belongs under an approved docs/ subdirectory.`,
      });
      return;
    }
    if (
      allowedDocsDirectories.some((directory) => name.startsWith(`${directory}/`))
    ) {
      return;
    }
    onError({
      lineNumber: 1,
      detail: `${name} is not under an approved docs/ directory.`,
    });
  },
};

export default rule;
