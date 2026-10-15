import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, test } from "vitest";

import { runUltracite } from "../../../tests/ultracite-fixture.js";
import style from "./style-plugin.js";

type Rule = (typeof style.rules)["no-blank-lines-in-functions"];
type RuleContext = Parameters<Rule["create"]>[0];
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
    const visitor = style.rules["no-blank-lines-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
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
      style.rules["no-inline-comments-in-functions"].create(context);
    const inspect = visitor["FunctionDeclaration:exit"];
    if (inspect) {
      inspect(makeNode(text));
    }
    expect(messages).toEqual(["noInlineComments"]);
  });
});

test("Ultracite checks physical function body style", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-function-style-"));
  try {
    await writeFile(
      path.join(directory, ".oxlintrc.json"),
      JSON.stringify({
        jsPlugins: [fileURLToPath(new URL("style-plugin.ts", import.meta.url))],
        rules: {
          "style/no-blank-lines-in-functions": "error",
          "style/no-inline-comments-in-functions": "error"
        }
      })
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
    await writeFile(
      path.join(directory, "directives.tsx"),
      [
        "export function view(value: string) {",
        "// @ts-expect-error intentional mismatch",
        "const mismatch: number = value;",
        "/* oxlint-disable-next-line no-console */",
        "console.log(mismatch);",
        "return <div>{/* eslint-disable-next-line react/no-danger */}{value}</div>;",
        "}"
      ].join("\n")
    );
    await writeFile(
      path.join(directory, "comments.tsx"),
      [
        "export function view(value: string) {",
        "/* plain */",
        "return <div>{/* note */}{value}</div>;",
        "}"
      ].join("\n")
    );
    for (const [filename, diagnosticCount] of [
      ["valid.ts", 0],
      ["directives.tsx", 0],
      ["comments.tsx", 2],
      ["invalid.ts", 4]
    ] as const) {
      if (diagnosticCount === 0) {
        expect(runUltracite(directory, filename, "fix").status).toBe(0);
      }
      const { diagnostics, status } = runUltracite(directory, filename);
      expect(status).toBe(diagnosticCount === 0 ? 0 : 1);
      expect(
        diagnostics.filter(({ code }) => code?.startsWith("style("))
      ).toHaveLength(diagnosticCount);
    }
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);

test("Ultracite reconciles only proven sibling registration boundaries", async () => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "sinon-registration-style-")
  );
  try {
    await writeFile(
      path.join(directory, ".oxlintrc.json"),
      JSON.stringify({
        jsPlugins: [fileURLToPath(new URL("style-plugin.ts", import.meta.url))],
        rules: {
          "style/no-blank-lines-in-functions": "error",
          "style/no-inline-comments-in-functions": "error",
          "style/sibling-test-spacing": "error"
        }
      })
    );
    const cases = [
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
    await Promise.all(
      cases.map(async ([filename, text, spacing, blanks]) => {
        await writeFile(path.join(directory, filename), text);
        const { diagnostics } = runUltracite(directory, filename);
        expect(
          diagnostics.filter(
            ({ code }) => code === "style(sibling-test-spacing)"
          ),
          filename
        ).toHaveLength(spacing);
        expect(
          diagnostics.filter(
            ({ code }) => code === "style(no-blank-lines-in-functions)"
          ),
          filename
        ).toHaveLength(blanks);
        const comments = diagnostics.filter(
          ({ code }) => code === "style(no-inline-comments-in-functions)"
        );
        expect(comments, filename).toHaveLength(
          filename === "plain.ts" ? 2 : 0
        );
        if (filename === "plain.ts") {
          runUltracite(directory, filename, "fix");
          const fixed = await readFile(path.join(directory, filename), "utf-8");
          expect(fixed).toContain("// explanatory top-level comment");
          expect(fixed).toContain("/** Not declaration documentation. */");
        }
      })
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 60_000);
