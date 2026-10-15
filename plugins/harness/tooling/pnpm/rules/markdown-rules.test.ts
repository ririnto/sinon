import { applyFixes } from "markdownlint";
import type { Rule } from "markdownlint";
import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";

import boxDrawing from "./no-box-drawing.js";
import {
  sentencePerLine,
  noEmDash,
  noRelativeSkillLinks
} from "./sentence-per-line.js";
import tableSeparators from "./table-separators.js";

interface Violation {
  readonly detail: string | null;
  readonly lineNumber: number | null;
  readonly range: number[] | null;
}

const violations = (
  rule: Rule,
  input: string,
  name = "input"
): { fixed: string; found: Violation[] } => {
  const [ruleName] = rule.names;
  const errors =
    lint({
      config: { default: false, [ruleName ?? ""]: true },
      customRules: [rule],
      strings: { [name]: input }
    })[name] ?? [];
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
    [
      "inline link labels",
      "See [First sentence. Second sentence.](https://example.com).\n"
    ],
    [
      "reference link labels",
      "See [First sentence. Second sentence.][target].\n\n[target]: https://example.com\n"
    ],
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
    [
      "prose before a link",
      "First sentence. Second sentence [label](https://example.com).\n",
      15
    ],
    [
      "prose after a link",
      "See [label](https://example.com) then stop. Second sentence.\n",
      43
    ],
    ["a list item", "- Item one. Item two follows.\n", 11],
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

  test("preserves exact blockquotes surrounding a protected table", () => {
    const input =
      "> First sentence. Second follows.\n>\n> | First sentence. Second follows. |\n> | --- |\n> | First stands; therefore second follows. |\n>\n> - First stands; therefore second follows.\n";
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: []
    });
  });

  test("checks authored GitHub alerts while preserving exact blockquotes", () => {
    const input = "> [!WARNING]\n>\n> Restart now. Verify status.\n";
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: [{ detail: multiple, lineNumber: 3, range: [14, 1] }]
    });
  });

  test("checks semicolon joins after a literal quotation", () => {
    const input = 'The value is "bad"; therefore retry.\n';
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: [{ detail: semicolon, lineNumber: 1, range: [19, 1] }]
    });
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

  test.each([
    [
      "numeric histogram observations",
      "- 50 observations are at or below 1. 20 observations are between 1 and 5.\n",
      36
    ],
    [
      "an explanation after an exact quotation",
      '- An empty `exp_alerts: []` means "this alert should not be firing at this eval time." This covers inactive states.\n',
      85
    ],
    [
      "UTF-16 coordinates after a quotation",
      'Read "😀 one. Two." Then continue.\n',
      18
    ],
    [
      "a sentence ending after a bare URL",
      "See https://example.com. Next sentence follows.\n",
      24
    ],
    [
      "a sentence ending after a link",
      "See [label](https://example.com). Next sentence follows.\n",
      33
    ],
    [
      "a sentence ending after inline code",
      "Run `tool`. Next sentence follows.\n",
      11
    ],
    [
      "a sentence ending with a single-letter value",
      "Use option A. Restart the service.\n",
      13
    ],
    [
      "a quotation with outer punctuation",
      'Choose "safe". Then continue.\n',
      14
    ]
  ])("reports packed %s without rewriting", (_name, input, column) => {
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: [{ detail: multiple, lineNumber: 1, range: [column, 1] }]
    });
  });

  test.each([
    [
      "wrapped list prose",
      "- Coordinates language and API design\n  decisions across multiple skills.\n",
      "Sentence continues from the previous source line; keep the sentence on one line.",
      3
    ],
    [
      "an instruction missing its terminator",
      "- Split predicates when chained filters preserve their meaning\n  Preserve order and side effects.\n",
      "End the previous instruction with a sentence terminator before this continuation.",
      3
    ]
  ])("reports %s without rewriting", (_name, input, detail, column) => {
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: [{ detail, lineNumber: 2, range: [column, 1] }]
    });
  });

  test.each([
    [
      "frontmatter",
      "---\ndescription: First sentence. Second sentence.\n---\n\nOnly one sentence.\n"
    ],
    [
      "literal quotation",
      'The source says "First sentence. Second sentence; Use it."\n'
    ],
    [
      "single quotation",
      "The source says 'First sentence. Second sentence.'\n"
    ],
    [
      "multiline quotation",
      'The source says "First sentence\ncontinues here. Second follows."\n'
    ],
    ["multiple literal labels", 'Use "First." and "Second." as labels.\n'],
    ["adjacent literal labels", 'Use "First." "Second." as labels.\n'],
    [
      "a straight quoted contraction",
      "The source says 'Don't stop. Keep going.'\n"
    ],
    [
      "a curly quoted contraction",
      "The source says ‘Don’t stop. Keep going.’\n"
    ],
    [
      "escaped quotation marks",
      'The source says "First \\"quoted\\" sentence. Second sentence."\n'
    ],
    ["contractions", "Don't change the writer's words.\n"],
    ["personal initials", "Ask J. Smith about the profile.\n"],
    ["an honorific and initial", "Ask Dr. J. Smith about the profile.\n"],
    [
      "a version followed by uppercase prose",
      "Use v1.2.3 with Node.js and Dr. Smith.\n"
    ],
    [
      "a URL containing punctuation",
      "See https://example.com/a;b?c=d then rest.\n"
    ],
    ["separate paragraphs", "A fragment\n\na lowercase fragment.\n"],
    ["separate list items", "- A fragment\n- another fragment\n"],
    [
      "correct list continuation",
      "- Split predicates when equivalent.\n  Preserve order and side effects.\n"
    ],
    ["a list introduction", "- Inputs:\n  use the selected profile.\n"],
    [
      "an HTML generated marker",
      "<!-- Generated file. Do not edit. -->\n\nFirst sentence. Second sentence.\n"
    ],
    [
      "generated frontmatter",
      "---\ngenerated: true\n---\n\nFirst sentence. Second sentence.\n"
    ]
  ])("preserves %s without diagnostics or fixes", (_name, input) => {
    expect(violations(sentencePerLine, input)).toEqual({
      fixed: input,
      found: []
    });
  });

  test.each([
    "LICENSE",
    "LICENSE.md",
    "plugins/sample/COPYING.md",
    "plugins\\sample\\LICENSE.txt"
  ])("preserves license text in %s", (name) => {
    const input = "First license sentence. Second license sentence.\n";
    expect(violations(sentencePerLine, input, name)).toEqual({
      fixed: input,
      found: []
    });
  });
});

describe("authored Markdown diagnostics", () => {
  test.each([
    [noEmDash, "Keep prose \u2014 clear.\n", 1],
    [
      noEmDash,
      "`code \u2014 text` and [URL](https://example.org/a\u2014b).\n",
      0
    ],
    [noEmDash, '> Exact \u2014 quotation.\n\n"Literal \u2014 quote."\n', 0],
    [noEmDash, "---\ntitle: Literal \u2014 metadata\n---\n\nProse.\n", 0],
    [noEmDash, "```text\ncode \u2014 text\n```\n", 0],
    [noRelativeSkillLinks, "Use [skill](../other/SKILL.md).\n", 1],
    [noRelativeSkillLinks, "See [skill][s].\n\n[s]: ../other/SKILL.md\n", 1],
    [noRelativeSkillLinks, "Use [guide][g].\n\n[g]: ../guide.md\n", 0],
    [noRelativeSkillLinks, "Use [skill](https://example.org/SKILL.md).\n", 0],
    [
      noRelativeSkillLinks,
      '`[skill](../SKILL.md)`\n\n> [skill](../SKILL.md)\n\n"[skill](../SKILL.md)"\n',
      0
    ],
    [
      noRelativeSkillLinks,
      "---\ntitle: '[skill](../SKILL.md)'\n---\n\n```md\n[skill](../SKILL.md)\n```\n",
      0
    ]
  ] as const)("checks %s with protected syntax", (rule, input, count) => {
    const result = violations(rule, input);
    expect(result.found).toHaveLength(count);
    expect(result.fixed).toBe(input);
  });
});
