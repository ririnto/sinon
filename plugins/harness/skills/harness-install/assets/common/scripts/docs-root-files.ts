#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint";

const allowedDocsRootFiles = new Set([
  "docs/DESIGN.md",
  "docs/FRONTEND.md",
  "docs/PLANS.md",
  "docs/PRODUCT_SENSE.md",
  "docs/QUALITY_SCORE.md",
  "docs/RELIABILITY.md",
  "docs/SECURITY.md"
]);

const allowedDocsDirectories = [
  "docs/design-docs",
  "docs/exec-plans",
  "docs/generated",
  "docs/product-specs",
  "docs/references",
  "docs/templates"
] as const;

/**
 * Markdownlint rule limiting Markdown paths under docs/.
 */
const rule: Rule = {
  description: "Docs allows only approved root files and subdirectories",
  function: (params, onError) => {
    const name = params.name.replaceAll("\\", "/");
    if (!name.startsWith("docs/")) {
      return;
    }
    if (/^docs\/[^/]+\.md$/u.test(name)) {
      if (allowedDocsRootFiles.has(name)) {
        return;
      }
      onError({
        detail: `${name} belongs under an approved docs/ subdirectory.`,
        lineNumber: 1
      });
      return;
    }
    if (
      allowedDocsDirectories.some((directory) =>
        name.startsWith(`${directory}/`)
      )
    ) {
      return;
    }
    onError({
      detail: `${name} is not under an approved docs/ directory.`,
      lineNumber: 1
    });
  },
  names: ["docs/root-files"],
  parser: "none",
  tags: ["docs"]
};

export default rule;
