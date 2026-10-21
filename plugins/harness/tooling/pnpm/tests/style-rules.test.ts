import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, test } from "vitest";

import { runUltracite } from "../../../tests/ultracite-fixture.js";
import noBlankLines from "../plugins/no-blank-lines-in-functions.js";
import noInlineComments from "../plugins/no-inline-comments-in-functions.js";

type Rule = (typeof noBlankLines.rules)["no-blank-lines-in-functions"];
type RuleContext = Parameters<Rule["create"]>[0] &
  Parameters<
    (typeof noInlineComments.rules)["no-inline-comments-in-functions"]["create"]
  >[0];
type Visitor = ReturnType<Rule["create"]>;
type VisitorNode = Parameters<
  NonNullable<Visitor["FunctionDeclaration:exit"]>
>[0];

interface Comment {
  readonly end: number;
  readonly start: number;
  readonly type: string;
  readonly value: string;
}

const makeNode = (body: string): VisitorNode =>
  ({
    body: {
      body: [],
      end: body.length,
      parent: undefined,
      start: 0,
      type: "BlockStatement"
    },
    end: body.length,
    id: null,
    parent: undefined,
    start: 0,
    type: "FunctionDeclaration"
  }) as unknown as VisitorNode;

const makeContext = (
  text: string,
  comments: readonly Comment[]
): { context: RuleContext; messages: string[] } => {
  const messages: string[] = [];
  const context = {
    report: ({ messageId }: { messageId: string }) => {
      messages.push(messageId);
    },
    sourceCode: {
      getCommentsBefore: () => [],
      getCommentsInside: () => comments,
      getLocFromIndex: () => ({ column: 0, line: 1 }),
      getTokens: () => [],
      text
    }
  } as unknown as RuleContext;
  return { context, messages };
};

describe("pnpm style rules", () => {
  test("reports a blank line inside a function body", () => {
    const text = "function invalid() {\n\n  return 1;\n}";
    const { context, messages } = makeContext(text, []);
    const visitor =
      noBlankLines.rules["no-blank-lines-in-functions"].create(context);
    const inspect = visitor["FunctionDeclaration:exit"];
    if (inspect) {
      inspect(makeNode(text));
    }
    expect(messages).toEqual(["noBlankLines"]);
  });

  test("reports a non-documentation comment inside a function body", () => {
    const text =
      "function invalid() {\n  // explain the return value\n  return 1;\n}";
    const comments = [
      { end: 47, start: 25, type: "Line", value: " explain the return value" }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    const inspect = visitor["FunctionDeclaration:exit"];
    if (inspect) {
      inspect(makeNode(text));
    }
    expect(messages).toEqual(["noInlineComments"]);
  });

  test.each([
    " @ts-expect-error reason",
    " @ts-ignore",
    " eslint-disable-next-line no-console",
    " oxlint-disable-next-line no-console -- reason",
    " oxfmt-ignore",
    " v8 ignore next"
  ])("allows the tool directive comment %s inside a function body", (value) => {
    const text = `function valid() {\n  //${value}\n  return 1;\n}`;
    const comments = [
      { end: text.indexOf("\n  return"), start: 21, type: "Line", value }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    visitor["FunctionDeclaration:exit"]?.(makeNode(text));
    expect(messages).toEqual([]);
  });

  test("reports a comment that only resembles a tool directive", () => {
    const value = " @ts-expect-errors are discouraged";
    const text = `function invalid() {\n  //${value}\n  return 1;\n}`;
    const comments = [
      { end: text.indexOf("\n  return"), start: 23, type: "Line", value }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    visitor["FunctionDeclaration:exit"]?.(makeNode(text));
    expect(messages).toEqual(["noInlineComments"]);
  });

  test("reports comments that mention legal markers without declaring notices", () => {
    const text =
      "function invalid() {\n  // This comment discusses copyright.\n  /* The @license marker appears here as prose. */\n  return 1;\n}";
    const comments = [
      {
        end: text.indexOf("\n", text.indexOf("//")),
        start: text.indexOf("//"),
        type: "Line",
        value: " This comment discusses copyright."
      },
      {
        end: text.indexOf("*/") + 2,
        start: text.indexOf("/*"),
        type: "Block",
        value: " The @license marker appears here as prose. "
      }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    visitor["FunctionDeclaration:exit"]?.(makeNode(text));
    expect(messages).toEqual(["noInlineComments", "noInlineComments"]);
  });

  test("preserves legal notices whose marker starts the comment", () => {
    const text =
      "function valid() {\n  /*\n   * Copyright 2026 Sinon contributors.\n   * SPDX-License-Identifier: MIT\n   */\n  return 1;\n}";
    const comments = [
      {
        end: text.indexOf("*/") + 2,
        start: text.indexOf("/*"),
        type: "Block",
        value:
          "\n   * Copyright 2026 Sinon contributors.\n   * SPDX-License-Identifier: MIT\n   "
      }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    visitor["FunctionDeclaration:exit"]?.(makeNode(text));
    expect(messages).toEqual([]);
  });

  test("preserves license notices in bang comments", () => {
    const text =
      "function valid() {\n  /*! @license MIT */\n  /*! Copyright 2026 Sinon contributors. */\n  return 1;\n}";
    const licenseStart = text.indexOf("/*!");
    const copyrightStart = text.indexOf("/*!", licenseStart + 3);
    const comments = [
      {
        end: text.indexOf("*/", licenseStart) + 2,
        start: licenseStart,
        type: "Block",
        value: "! @license MIT "
      },
      {
        end: text.indexOf("*/", copyrightStart) + 2,
        start: copyrightStart,
        type: "Block",
        value: "! Copyright 2026 Sinon contributors. "
      }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    visitor["FunctionDeclaration:exit"]?.(makeNode(text));
    expect(messages).toEqual([]);
  });

  test("reports unattached documentation inside a function body", () => {
    const text =
      "function valid() {\n  /**\n   * Document the return contract.\n   */\n  return 1;\n}";
    const comments = [
      {
        end: text.indexOf("*/") + 2,
        start: text.indexOf("/**"),
        type: "Block",
        value: "*\n   * Document the return contract.\n   "
      }
    ];
    const { context, messages } = makeContext(text, comments);
    const visitor =
      noInlineComments.rules["no-inline-comments-in-functions"].create(context);
    const inspect = visitor["FunctionDeclaration:exit"];
    if (inspect) {
      inspect(makeNode(text));
    }
    expect(messages).toEqual(["noInlineComments"]);
  });
});

const pluginDirectory = new URL("../plugins/", import.meta.url);

const writeRuleConfiguration = async (
  directory: string,
  pluginFile: string,
  ruleId: string
): Promise<void> => {
  await writeFile(
    path.join(directory, ".oxlintrc.json"),
    JSON.stringify({
      jsPlugins: [fileURLToPath(new URL(pluginFile, pluginDirectory))],
      rules: { [ruleId]: "error" }
    })
  );
};

test("blank-line checks preserve directive, comment, and literal gaps", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-blank-lines-"));
  try {
    await writeRuleConfiguration(
      directory,
      "no-blank-lines-in-functions.ts",
      "style/no-blank-lines-in-functions"
    );
    await writeFile(
      path.join(directory, "valid.ts"),
      "function valid() {\n'use strict';\n\n/**\n * Document the nested value.\n\n */\nconst text = `first\n\nlast`;\nreturn text;\n}\nvalid();"
    );
    await writeFile(
      path.join(directory, "invalid.ts"),
      [
        "function blank() {\n\nreturn 1;\n}",
        "function windows() {\r\n\r\nreturn 1;\r\n}",
        "function unicode() {\u2028\u2028return 1;\u2028}",
        "function nested() {\nconst inner = () => {\n// Explain the return value\nreturn 1;\n};\nreturn inner();\n}",
        "blank(); windows(); unicode(); nested();"
      ].join("\n")
    );
    expect(runUltracite(directory, "valid.ts", "fix").status).toBe(0);
    const valid = runUltracite(directory, "valid.ts");
    expect(valid.status).toBe(0);
    expect(
      valid.diagnostics.filter(
        ({ code }) => code === "style(no-blank-lines-in-functions)"
      )
    ).toEqual([]);
    const invalid = runUltracite(directory, "invalid.ts");
    expect(invalid.status).toBe(1);
    expect(
      invalid.diagnostics
        .filter(({ code }) => code?.startsWith("style("))
        .map(({ code }) => code)
    ).toEqual([
      "style(no-blank-lines-in-functions)",
      "style(no-blank-lines-in-functions)",
      "style(no-blank-lines-in-functions)"
    ]);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

const commentFixtures = [
  {
    expected: 1,
    filename: "nested.ts",
    source:
      "function nested() {\nconst inner = () => {\n// Explain the return value\nreturn 1;\n};\nreturn inner();\n}\nnested();"
  },
  {
    expected: 0,
    filename: "directives.tsx",
    source: [
      "export function view(value: string) {",
      "// @ts-expect-error intentional mismatch",
      "const mismatch: number = value;",
      "/* oxlint-disable-next-line no-console */",
      "console.log(mismatch);",
      "return <div>{/* eslint-disable-next-line react/no-danger */}{value}</div>;",
      "}"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "tool-directives.ts",
    source: [
      "function valid() {",
      "  // @ts-expect-error reason",
      "  // @ts-ignore",
      "  // eslint-disable-next-line no-console",
      "  // oxlint-disable-next-line no-console -- reason",
      "  // oxfmt-ignore",
      "  // v8 ignore next",
      "  return 1;",
      "}",
      "valid();"
    ].join("\n")
  },
  {
    expected: 5,
    filename: "comment-boundaries.ts",
    source: [
      "function explanatory() {",
      "  // explain the return value",
      "  return 1;",
      "}",
      "function fakeDirective() {",
      "  // @ts-expect-errors are discouraged",
      "  return 1;",
      "}",
      "function legalProse() {",
      "  // This comment discusses copyright.",
      "  /* The @license marker appears here as prose. */",
      "  return 1;",
      "}",
      "function legalNotices() {",
      "  /*",
      "   * Copyright 2026 Sinon contributors.",
      "   * SPDX-License-Identifier: MIT",
      "   */",
      "  /*! @license MIT */",
      "  /*! Copyright 2026 Sinon contributors. */",
      "  return 1;",
      "}",
      "function unattachedDocumentation() {",
      "  /**",
      "   * Document the return contract.",
      "   */",
      "  return 1;",
      "}",
      "explanatory();",
      "fakeDirective();",
      "legalProse();",
      "legalNotices();",
      "unattachedDocumentation();"
    ].join("\n")
  },
  {
    expected: 0,
    filename: "shebang.ts",
    source:
      '#!/usr/bin/env node\n/// <reference types="node" />\n/* #__PURE__ */\nconst result = work();\nconsume(result);\n'
  },
  {
    expected: 0,
    filename: "docs.ts",
    source: [
      "/** First declaration contract. */",
      "/** Declaration contract. */",
      "const value = 1;",
      "/* @license MIT */",
      "function valid() {",
      "// @ts-expect-error intentional fixture",
      "consume(value);",
      "}"
    ].join("\n")
  },
  {
    expected: 2,
    filename: "comments.tsx",
    source: [
      "export function view(value: string) {",
      "/* plain */",
      "return <div>{/* note */}{value}</div>;",
      "}"
    ].join("\n")
  },
  {
    expected: 2,
    filename: "plain.ts",
    source: [
      "// explanatory top-level comment",
      "function invalid() {",
      "/** Not declaration documentation. */",
      "return 1;",
      "}"
    ].join("\n")
  }
] as const;

test("inline-comment checks preserve directives and license notices", async () => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "sinon-inline-comments-")
  );
  try {
    await writeRuleConfiguration(
      directory,
      "no-inline-comments-in-functions.ts",
      "style/no-inline-comments-in-functions"
    );
    await Promise.all(
      commentFixtures.map(({ filename, source }) =>
        writeFile(path.join(directory, filename), source)
      )
    );
    for (const { filename, expected } of commentFixtures) {
      if (expected === 0) {
        const result = runUltracite(directory, filename, "fix");
        expect(result.status, JSON.stringify(result.diagnostics)).toBe(0);
      }
      const { diagnostics, status } = runUltracite(directory, filename);
      const comments = diagnostics.filter(
        ({ code }) => code === "style(no-inline-comments-in-functions)"
      );
      expect(comments, filename).toHaveLength(expected);
      expect(status, filename).toBe(expected === 0 ? 0 : 1);
    }
    runUltracite(directory, "plain.ts", "fix");
    const fixed = await readFile(path.join(directory, "plain.ts"), "utf-8");
    expect(fixed).toContain("// explanatory top-level comment");
    expect(fixed).toContain("/** Not declaration documentation. */");
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

const registrationCases = [
  [
    "valid.ts",
    'import { describe as suite, test as example, beforeEach } from "vitest";\nsuite("group", () => {\nbeforeEach(() => {});\n\nexample("first", () => {});\n\nexample.skip("second", () => {});\n});',
    0,
    0
  ],
  [
    "missing.ts",
    'import { describe, test, afterEach } from "@jest/globals";\ndescribe("group", () => {\ntest("first", () => {});\nafterEach(() => {});\n});',
    1,
    0
  ],
  [
    "extra.ts",
    'import { test, afterAll } from "bun:test";\ntest("first", () => {});\n\n\nafterAll(() => {});',
    1,
    0
  ],
  [
    "shadow.ts",
    'import { describe, test } from "vitest";\nfunction ordinary(describe: Function) {\ndescribe("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});\n}',
    0,
    1
  ],
  [
    "callback.ts",
    'import { describe, test } from "vitest";\ndescribe("group", () => {\ntest("first", () => {\nfirst();\n\nsecond();\n});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "global.ts",
    'describe("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "dynamic.ts",
    'import { describe, test } from "vitest";\ndescribe.factory([[1]])("group", () => {\ntest("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    1
  ],
  [
    "shebang.ts",
    '#!/usr/bin/env node\n/// <reference types="node" />\n/* #__PURE__ */\nconst result = work();\nconsume(result);\n',
    0,
    0
  ],
  [
    "docs.ts",
    "/** First declaration contract. */\n/** Declaration contract. */\nconst value = 1;\n/* @license MIT */\nfunction valid() {\n// @ts-expect-error intentional fixture\nconsume(value);\n}",
    0,
    0
  ],
  [
    "plain.ts",
    "// explanatory top-level comment\nfunction invalid() {\n/** Not declaration documentation. */\nreturn 1;\n}",
    0,
    0
  ],
  [
    "directive-gap.ts",
    'import { describe, test } from "vitest";\ndescribe("group", () => {\ntest("first", () => {});\n\n// @ts-expect-error intentional directive\ntest("second", () => {});\n});',
    0,
    0
  ],
  [
    "each.ts",
    'import { describe, test, beforeEach } from "vitest";\ndescribe.each([[1]])("group", () => {\nbeforeEach(() => {});\n\ntest.each([[1]])("first", () => {});\n\ntest("second", () => {});\n});',
    0,
    0
  ]
] as const;

type RegistrationCase = (typeof registrationCases)[number];

test.each([
  {
    expectedCount: (testCase: RegistrationCase) => testCase[2],
    pluginFile: "sibling-test-spacing.ts",
    ruleCode: "style(sibling-test-spacing)",
    ruleId: "style/sibling-test-spacing"
  },
  {
    expectedCount: (testCase: RegistrationCase) => testCase[3],
    pluginFile: "no-blank-lines-in-functions.ts",
    ruleCode: "style(no-blank-lines-in-functions)",
    ruleId: "style/no-blank-lines-in-functions"
  },
  {
    expectedCount: (testCase: RegistrationCase) =>
      testCase[0] === "plain.ts" ? 2 : 0,
    pluginFile: "no-inline-comments-in-functions.ts",
    ruleCode: "style(no-inline-comments-in-functions)",
    ruleId: "style/no-inline-comments-in-functions"
  }
])(
  "$ruleCode preserves registration-boundary behavior",
  async ({ pluginFile, ruleId, ruleCode, expectedCount }) => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-registration-style-")
    );
    try {
      await writeRuleConfiguration(directory, pluginFile, ruleId);
      await Promise.all(
        registrationCases.map(([filename, source]) =>
          writeFile(path.join(directory, filename), source)
        )
      );
      for (const testCase of registrationCases) {
        const { diagnostics } = runUltracite(directory, testCase[0]);
        expect(
          diagnostics
            .filter(({ code }) => code?.startsWith("style("))
            .map(({ code }) => code),
          testCase[0]
        ).toEqual(
          Array.from({ length: expectedCount(testCase) }, () => ruleCode)
        );
      }
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  },
  60_000
);
