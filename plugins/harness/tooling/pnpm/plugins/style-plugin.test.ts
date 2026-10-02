import { spawnSync } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, test } from "vitest";

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
      getCommentsInside: () => comments,
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
  test("allows documentation comments inside a function body", () => {
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
    expect(messages).toEqual([]);
  });
});

test("native Oxlint checks physical function body style", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-function-style-"));
  try {
    await writeFile(
      path.join(directory, "oxlint.json"),
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
    for (const [filename, diagnosticCount] of [
      ["valid.ts", 0],
      ["invalid.ts", 4]
    ] as const) {
      const result = spawnSync(
        process.execPath,
        [
          path.join(
            path.dirname(
              createRequire(import.meta.url).resolve("oxlint/package.json")
            ),
            "bin/oxlint"
          ),
          "--config",
          path.join(directory, "oxlint.json"),
          "--format",
          "json",
          path.join(directory, filename)
        ],
        { cwd: directory }
      );
      expect(result.error).toBeUndefined();
      expect(result.status).toBe(diagnosticCount === 0 ? 0 : 1);
      const {
        diagnostics
      }: { readonly diagnostics: readonly { readonly code?: string }[] } =
        JSON.parse(result.stdout.toString());
      expect(
        diagnostics.filter(({ code }) => code?.startsWith("style("))
      ).toHaveLength(diagnosticCount);
    }
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});
