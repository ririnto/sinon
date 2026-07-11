// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import {
  markdownStructureErrors,
  parseFencedCodeBlocks,
  parseMarkdownHeadings
} from "./markdown-structure.js";

test("structural Markdown validation accepts renamed and reordered sections", () => {
  const document = `# C# Workflow ##

## Delivery

Run the required gate.

## Intake

Record the request.
`;

  expect(
    parseMarkdownHeadings(document).map((heading) => heading.title)
  ).toEqual(["C# Workflow", "Delivery", "Intake"]);
  expect(markdownStructureErrors(document, { kind: "full" })).toEqual([]);
});

test("structural Markdown validation ignores fenced fake headings", () => {
  const document = `# Workflow

\`\`\`md
# Fake title
## Fake section
\`\`\`

## Actual section

Keep this content.
`;

  expect(
    parseMarkdownHeadings(document).map((heading) => heading.title)
  ).toEqual(["Workflow", "Actual section"]);
  expect(markdownStructureErrors(document, { kind: "full" })).toEqual([]);
});

test("fenced code parsing preserves the language and executable content", () => {
  // Given
  const document = `# Workflow

\`\`\`sh
git status --short
\`\`\`
`;

  // When
  const codeBlocks = parseFencedCodeBlocks(document);

  // Then
  expect(codeBlocks).toEqual([
    { content: "git status --short", language: "sh" }
  ]);
});

test("structural Markdown validation ignores YAML frontmatter", () => {
  const document = `---
name: example
# Audience
description: A skill whose YAML uses hash-prefixed comments.
---

# Title

## Audience

Skill body.
`;

  expect(
    parseMarkdownHeadings(document).map((heading) => heading.title)
  ).toEqual(["Title", "Audience"]);
  expect(markdownStructureErrors(document, { kind: "full" })).toEqual([]);
});

test("structural Markdown validation rejects empty and ambiguous sections", () => {
  expect(
    markdownStructureErrors("# Workflow\n\n## Empty\n", { kind: "full" })
  ).toContain("H2 section Empty is empty");
  expect(
    markdownStructureErrors("## One\n\nReady.\n\n## Two\n\nAlso ready.\n", {
      kind: "addendum"
    })
  ).toContain("host addendum must contain exactly one H2 section");
});
