import { applyFixes } from "markdownlint";
import type { Rule } from "markdownlint";
import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";

import boxDrawing from "./no-box-drawing.js";
import sentencePerLine from "./sentence-per-line.js";
import tableSeparators from "./table-separators.js";

interface Violation {
  readonly detail: string | null;
  readonly lineNumber: number | null;
  readonly range: number[] | null;
}

const violations = (
  rule: Rule,
  input: string
): { fixed: string; found: Violation[] } => {
  const [name] = rule.names;
  const errors =
    lint({
      config: { default: false, [name ?? ""]: true },
      customRules: [rule],
      strings: { input }
    }).input ?? [];
  return {
    fixed: applyFixes(input, errors),
    found: errors.map((error) => ({
      detail: error.errorDetail,
      lineNumber: error.lineNumber,
      range: error.errorRange
    }))
  };
};

describe("docs/no-box-drawing", () => {
  const detail = "Use ASCII tree markers such as +-- and | instead.";
  test("accepts ASCII tree markers", () => {
    expect(
      violations(boxDrawing, "Plain text with +-- and | markers.\n").found
    ).toEqual([]);
  });
  test("reports the first box drawing character with its column", () => {
    expect(
      violations(boxDrawing, "Plain box: ─── and │ text.\n").found
    ).toEqual([{ detail, lineNumber: 1, range: [12, 1] }]);
  });
  test("reports one violation per line, including fenced code", () => {
    expect(
      violations(boxDrawing, "```text\n├── a\n└── b\n```\n").found
    ).toEqual([
      { detail, lineNumber: 2, range: [1, 1] },
      { detail, lineNumber: 3, range: [1, 1] }
    ]);
  });
});

const separatorDetail = (cell: number): string =>
  `Separator cell ${cell} must use exactly ---.`;

describe("docs/table-separators", () => {
  test("accepts exact separators and alignment markers", () => {
    expect(
      violations(
        tableSeparators,
        "| A | B | C | D |\n| --- | :--- | ---: | :---: |\n| 1 | 2 | 3 | 4 |\n"
      ).found
    ).toEqual([]);
  });
  test("reports short, long, and misaligned separator cells", () => {
    const { found, fixed } = violations(
      tableSeparators,
      "| A | B | C | D |\n| -- | ----- | :-: | ---: |\n| 1 | 2 | 3 | 4 |\n"
    );
    expect(found).toEqual([
      { detail: separatorDetail(1), lineNumber: 2, range: [3, 2] },
      { detail: separatorDetail(2), lineNumber: 2, range: [8, 5] },
      { detail: separatorDetail(3), lineNumber: 2, range: [17, 1] }
    ]);
    expect(fixed).toBe(
      "| A | B | C | D |\n| --- | --- | :---: | ---: |\n| 1 | 2 | 3 | 4 |\n"
    );
  });
  test("ignores data rows that only contain hyphens", () => {
    expect(
      violations(
        tableSeparators,
        "| A | B |\n| --- | --- |\n| - | - |\n| -- | ---- |\n"
      ).found
    ).toEqual([]);
  });
  test("ignores lines that look like tables inside fenced code", () => {
    expect(
      violations(tableSeparators, "```markdown\n| A |\n| - |\n```\n").found
    ).toEqual([]);
  });
  test("checks pipeless tables", () => {
    expect(
      violations(tableSeparators, "A | B\n-- | ---\n1 | 2\n").found
    ).toEqual([{ detail: separatorDetail(1), lineNumber: 2, range: [1, 2] }]);
  });
  test("checks tables nested in a blockquote", () => {
    expect(
      violations(tableSeparators, "> | A |\n> | - |\n> | 1 |\n").found
    ).toEqual([{ detail: separatorDetail(1), lineNumber: 2, range: [5, 1] }]);
  });
});

describe("docs/sentence-per-line", () => {
  const multiple =
    "Multiple sentences on one line; put each sentence on its own line.";
  const semicolon =
    "Semicolon-joined sentences on one line; put each sentence on its own line.";
  test.each([
    ["one sentence per line", "First stands alone.\nSecond follows.\n"],
    ["a single sentence", "Only one sentence here.\n"],
    [
      "a noun-phrase tail with a verb-like word",
      "Header uses key; value pairs.\n"
    ],
    [
      "a see-reference in parentheses",
      "Unchanged here (already done; see Step 2)\n"
    ],
    ["an article-led noun phrase", "Choose a field; the runs table.\n"],
    ["a lowercase example clause", "Set the mode; for example, use `safe`.\n"],
    ["fenced code", "```text\nSentence one. Sentence two; joined.\n```\n"],
    ["indented code", "    Sentence one. Sentence two; joined.\n"],
    ["headings", "# Heading one. Heading two; joined\n"],
    [
      "HTML blocks",
      "<details>\nSentence one. Sentence two; joined.\n</details>\n"
    ],
    ["inline code spans", "Run `init(); stop()` then rest.\n"],
    [
      "links and bare URLs",
      "See [one](https://example.com/a. b) and https://example.com/x. y now.\n"
    ],
    ["decimals and versions", "Pi is 3.14 and v1.2.3 ships today.\n"],
    ["abbreviations", "Use e.g. this and the U.S.A. flag now.\n"],
    ["HTML entities", "Use &amp; then continue onwards.\n"],
    ["list markers", "1. Numbered item stands alone\n"],
    [
      "table cells split across rows",
      "| Area | Evidence |\n| --- | --- |\n| Reasoning | First sentence stands alone. |\n| | - Second follows. |\n"
    ]
  ])("accepts %s", (_name, input) => {
    expect(violations(sentencePerLine, input).found).toEqual([]);
  });
  test.each([
    ["a paragraph", "First sentence. Second sentence follows.\n", 15],
    ["a list item", "- Item one. Item two follows.\n", 11],
    ["a blockquote", "> Quoted one. Quoted two follows.\n", 13],
    ["three sentences", "One here. Two here. Three here.\n", 9],
    ["astral prose before code", "😀 `code` First. Second.\n", 16]
  ])("rejects multiple sentences in %s", (_name, input, column) => {
    expect(violations(sentencePerLine, input).found).toEqual([
      { detail: multiple, lineNumber: 1, range: [column, 1] }
    ]);
  });
  test.each([
    [
      "table headers and cells",
      "| First sentence. Second follows. | Evidence; therefore check it. |\n| --- | --- |\n| First sentence. Second follows. | First stands; therefore second follows. |\n"
    ],
    [
      "pipeless tables",
      "First sentence. Second follows. | Evidence; therefore check it.\n--- | ---\nFirst sentence. Second follows. | First stands; therefore second follows.\n"
    ],
    [
      "blockquoted tables",
      "> | First sentence. Second follows. | Evidence; therefore check it. |\n> | --- | --- |\n> | First sentence. Second follows. | First stands; therefore second follows. |\n"
    ]
  ])("preserves %s without diagnostics or fixes", (_name, input) => {
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: []
    });
  });
  test("checks blockquote prose surrounding a protected table", () => {
    const input =
      "> First sentence. Second follows.\n>\n> | First sentence. Second follows. |\n> | --- |\n> | First stands; therefore second follows. |\n>\n> - First stands; therefore second follows.\n";
    expect(violations(sentencePerLine, input).found).toEqual([
      { detail: multiple, lineNumber: 1, range: [17, 1] },
      { detail: semicolon, lineNumber: 7, range: [17, 1] }
    ]);
  });
  test.each([
    [
      "a capitalized tail",
      "First stands alone; However, second follows here.\n",
      19
    ],
    [
      "a conjunctive adverb",
      "First stands alone; therefore second follows.\n",
      19
    ],
    [
      "a lowercase subject and finite verb",
      "First stands alone; second follows.\n",
      19
    ],
    ["a fullwidth semicolon", "The request failed；retry the request.\n", 19],
    [
      "a determiner-led subject",
      "Consumes nothing from other tasks; this task is first.\n",
      34
    ],
    [
      "a determiner starting with another",
      "First stands alone; another sentence follows.\n",
      19
    ],
    ["a list item", "- First stands; therefore second follows.\n", 15]
  ])("rejects a semicolon join with %s", (_name, input, column) => {
    expect(violations(sentencePerLine, input).found).toEqual([
      { detail: semicolon, lineNumber: 1, range: [column, 1] }
    ]);
  });
});
