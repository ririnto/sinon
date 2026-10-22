import { applyFixes } from "markdownlint";
import type { Rule } from "markdownlint";
import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";

import rootSentenceRules from "../../../../../rules/sentence-per-line.js";
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
    [
      "the reported regular past-tense clause",
      "The configuration is stale; reviewers changed the endpoint.\n",
      [27, 1]
    ],
    [
      "a pronoun subject",
      "A status is stale; They changed the endpoint.\n",
      [18, 1]
    ],
    [
      "a determiner-led subject",
      "A status is stale; The reviewers changed the endpoint.\n",
      [18, 1]
    ],
    [
      "a regular past form derived from remove",
      "A status is stale; reviewers removed the record.\n",
      [18, 1]
    ],
    [
      "a regular past form derived from restore",
      "A status is stale; reviewers restored the guide.\n",
      [18, 1]
    ],
    [
      "a regular past form derived from validate",
      "A status is stale; reviewers validated the settings.\n",
      [18, 1]
    ],
    [
      "an auxiliary clause with an inline-code object",
      "A status is stale; reviewers have changed the `endpoint`.\n",
      [18, 1]
    ],
    [
      "an auxiliary clause with a link object",
      "A status is stale; reviewers have changed the [endpoint](https://example.com).\n",
      [18, 1]
    ],
    [
      "an auxiliary clause with a quoted object",
      'A status is stale; reviewers have changed the "endpoint".\n',
      [18, 1]
    ],
    [
      "an object with a combining mark",
      "A status is stale; reviewers changed the e\u0301ndpoint.\n",
      [18, 1]
    ],
    [
      "a non-Latin object",
      "A status is stale; reviewers changed the 配置.\n",
      [18, 1]
    ],
    [
      "a visible object after a quoted label and astral prose",
      '😀 "label" A status is stale; reviewers changed the endpoint.\n',
      [29, 1]
    ],
    [
      "a fullwidth semicolon",
      "A status is stale； reviewers changed the endpoint.\n",
      [18, 1]
    ],
    [
      "the same clause on a later physical line",
      "First stands alone.\nA status is stale; reviewers changed the endpoint.\n",
      [18, 1],
      2
    ]
  ])(
    "reports a regular past-tense clause after %s",
    (_name, input, range, lineNumber = 1) => {
      const errors =
        lint({
          config: { default: false, "docs/sentence-per-line": true },
          customRules: rootSentenceRules,
          strings: { input }
        }).input ?? [];
      expect(
        errors.map((error) => ({
          errorRange: error.errorRange,
          fixInfo: error.fixInfo,
          lineNumber: error.lineNumber,
          ruleNames: error.ruleNames
        }))
      ).toEqual([
        {
          errorRange: range,
          fixInfo: null,
          lineNumber,
          ruleNames: ["docs/sentence-per-line"]
        }
      ]);
      expect(applyFixes(input, errors)).toBe(input);
    }
  );

  test("keeps the shared abbreviation check and one diagnostic per kind", () => {
    const input =
      "The vendor is Acme Inc. Reviewers changed the endpoint; reviewers restored the guide; reviewers removed the record.\n";
    const errors =
      lint({
        config: { default: false, "docs/sentence-per-line": true },
        customRules: rootSentenceRules,
        strings: { input }
      }).input ?? [];
    expect(
      errors.map((error) => ({
        errorRange: error.errorRange,
        fixInfo: error.fixInfo,
        lineNumber: error.lineNumber,
        ruleNames: error.ruleNames
      }))
    ).toEqual([
      {
        errorRange: [23, 1],
        fixInfo: null,
        lineNumber: 1,
        ruleNames: ["docs/sentence-per-line"]
      },
      {
        errorRange: [55, 1],
        fixInfo: null,
        lineNumber: 1,
        ruleNames: ["docs/sentence-per-line"]
      }
    ]);
    expect(applyFixes(input, errors)).toBe(input);
  });

  test("reports the semicolon after a degree list", () => {
    const input =
      "The kit contains tools used the least; tools used the most; reviewers changed the endpoint.\n";
    const errors =
      lint({
        config: { default: false, "docs/sentence-per-line": true },
        customRules: rootSentenceRules,
        strings: { input }
      }).input ?? [];
    expect(
      errors.map((error) => ({
        errorRange: error.errorRange,
        fixInfo: error.fixInfo,
        lineNumber: error.lineNumber,
        ruleNames: error.ruleNames
      }))
    ).toEqual([
      {
        errorRange: [59, 1],
        fixInfo: null,
        lineNumber: 1,
        ruleNames: ["docs/sentence-per-line"]
      }
    ]);
    expect(applyFixes(input, errors)).toBe(input);
  });

  test.each([
    [
      "the apps, services, and workers list",
      "The components are apps named the controller; services named the gateway; and workers named the queue.\n"
    ],
    [
      "the document, record, and note list",
      "The inventory includes a document titled The Guide; a record named the index; and a note labeled the summary.\n"
    ],
    [
      "the copied and changed temporal list",
      "The archive contains documents filed the previous day; files copied the same day; and records changed the next day.\n"
    ],
    [
      "the removed temporal list",
      "The archive contains documents removed the previous day; files removed the same day.\n"
    ],
    [
      "a temporal link object",
      "The archive contains documents removed the previous day; files removed the [same day](https://example.com).\n"
    ],
    [
      "a temporal inline-code object",
      "The archive contains documents removed the previous day; files removed the `same day`.\n"
    ],
    [
      "a temporal quoted object",
      'The archive contains documents removed the previous day; files removed the "same day".\n'
    ],
    [
      "a degree list from least to most",
      "The kit contains tools used the least; tools used the most.\n"
    ],
    [
      "a partly linked degree word",
      "The kit contains tools used the least; tools used the mo[st](https://example.com).\n"
    ],
    [
      "a partly code-formatted degree word",
      "The kit contains tools used the least; tools used the mo`st`.\n"
    ],
    [
      "a degree list from most to least",
      "The archive contains documents changed the most; files changed the least.\n"
    ],
    [
      "a degree list from shortest to longest",
      "The kit contains tools used the shortest; tools used the longest.\n"
    ],
    [
      "a degree list from worst to best",
      "The kit contains tools used the worst; tools used the best.\n"
    ],
    [
      "a degree list from best to worst",
      "The kit contains tools used the best; tools used the worst.\n"
    ],
    [
      "a degree list from slowest to fastest",
      "The kit contains tools changed the slowest; tools changed the fastest.\n"
    ],
    [
      "a degree list from fastest to furthest",
      "The kit contains tools changed the fastest; tools changed the furthest.\n"
    ],
    [
      "week and day references",
      "The archive contains documents changed this week; files changed that day.\n"
    ],
    [
      "day and week age references",
      "The archive contains documents removed a day ago; files removed a week ago.\n"
    ],
    [
      "an object marker without a word",
      "A status is stale; reviewers changed the.\n"
    ],
    [
      "a punctuation-only object",
      "A status is stale; reviewers changed the -.\n"
    ]
  ])("preserves %s without diagnostics or fixes", (_name, input) => {
    const errors =
      lint({
        config: { default: false, "docs/sentence-per-line": true },
        customRules: rootSentenceRules,
        strings: { input }
      }).input ?? [];
    expect(errors).toEqual([]);
    expect(applyFixes(input, errors)).toBe(input);
  });

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
      "a complex list with capitalized names",
      "The contributors are Alice, editor; Bob, reviewer; Carol, maintainer.\n"
    ],
    [
      "an adverb inside a complex list item",
      "The process has three stages: an input stage; a validation stage, which is therefore mandatory; and a reporting stage.\n"
    ],
    [
      "an ambiguous imperative word in a complex list",
      "The lifecycle has three phases: setup; restart, if needed; and cleanup.\n"
    ],
    [
      "an organization abbreviation within a sentence",
      "The vendor is Acme Inc. and will restart the server.\n"
    ],
    [
      "an enumeration abbreviation within a sentence",
      "Include lint, tests, etc. before restarting the build.\n"
    ],
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
      "an organization abbreviation before an instruction",
      "The vendor is Acme Inc. Restart the server.\n",
      23
    ],
    [
      "an organization abbreviation before an independent clause",
      "The vendor is Acme Ltd. The server is ready.\n",
      23
    ],
    [
      "an enumeration abbreviation before an instruction",
      "Include lint, tests, etc. Restart the build.\n",
      25
    ],
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
      "an imperative with an object",
      "Setup is complete; restart the service.\n",
      18
    ],
    [
      "an imperative followed by a comma",
      "Setup is complete; restart, if needed.\n",
      18
    ],
    [
      "an instruction after a colon without a later list join",
      "Before release: all checks passed; restart the service, if needed.\n",
      34
    ],
    ["a capitalized subject", "First stands alone; This is ready.\n", 19],
    ["a lowercase subject", "First stands alone; this is ready.\n", 19],
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
    ["../other/%53KILL.md", 19],
    ["../other/SKILL%2Emd", 19],
    ["../other/SKILL%2emd", 19],
    ["../other/%53%4b%49%4c%4c%2e%6d%64", 33],
    ["../other/SKILL%2emd?view=1#intro", 32],
    ["<../other/SKILL%2emd>", 21],
    ["../other/SKILL.md?view=%0Afoo", 29],
    ["../other/SKILL.md?view=%0Dfoo", 29],
    ["../other/SKILL.md#%0Aintro", 26],
    ["../other/SKILL%2Emd?%0Aview=1", 29],
    ["h%74tps:/other/SKILL.md", 23],
    ["h%74tp:target/SKILL.md", 22]
  ] as const)(
    "reports encoded skill destination %s at its source range",
    (target, length) => {
      const input = `Use [skill](${target}).\n`;
      expect(violations(noRelativeSkillLinks, input)).toEqual({
        fixed: input,
        found: [{ detail: null, lineNumber: 1, range: [13, length] }]
      });
    }
  );

  test.each([
    ["../other/%53KILL.md", 19],
    ["../other/SKILL%2emd?view=%0Afoo", 31]
  ] as const)(
    "reports encoded reference destination %s at its source range",
    (target, length) => {
      const input = `Use [skill][s].\n\n[s]: ${target}\n`;
      expect(violations(noRelativeSkillLinks, input)).toEqual({
        fixed: input,
        found: [{ detail: null, lineNumber: 3, range: [6, length] }]
      });
    }
  );

  test.each([
    "Use [skill](https://example.org/SKILL%2emd).\n",
    "Use [skill](/other/SKILL%2emd).\n",
    "Use [skill](//example.org/SKILL%2emd).\n",
    "Use [guide](../guide%2emd).\n",
    "Use [guide](../other/page?next=../SKILL%2Emd).\n",
    "Use [guide](../other/page#../SKILL%2Emd).\n",
    "Use [guide](../other/page?next=../SKILL.md).\n",
    "Use [guide](../other/page#../SKILL.md).\n",
    "Use [skill](../other/SKILL%ZZ.md).\n",
    "Use [skill](../other/%2553KILL.md).\n",
    "Use [skill](../other%2fSKILL.md).\n",
    "Use [skill](../other/SKILL.md%3Fview=1).\n",
    "`[skill](../other/SKILL%2emd)`\n",
    '"[skill](../other/SKILL%2emd)"\n',
    "```md\n[skill](../other/SKILL%2emd)\n```\n"
  ])("preserves encoded non-target or protected syntax %s", (input) => {
    expect(violations(noRelativeSkillLinks, input)).toEqual({
      fixed: input,
      found: []
    });
  });

  test("keeps literal skill detection after a malformed prefix escape", () => {
    const input = "Use [skill](../%ZZ/SKILL.md).\n";
    expect(violations(noRelativeSkillLinks, input)).toEqual({
      fixed: input,
      found: [{ detail: null, lineNumber: 1, range: [13, 15] }]
    });
  });

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
