import { spawnSync } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { describe, expect, test } from "vitest";

import tsdoc from "./tsdoc-plugin.js";

type Rule = (typeof tsdoc.rules)["require-export-tsdoc"];
type RuleContext = Parameters<Rule["create"]>[0];
type Visitor = ReturnType<Rule["create"]>;
type ExportNode = Parameters<NonNullable<Visitor["ExportNamedDeclaration"]>>[0];

interface Report {
  readonly data?: Record<string, string | undefined>;
  readonly messageId: string;
  readonly node: unknown;
}

interface Comment {
  readonly type: string;
  readonly value: string;
}

const makeExportedFunction = (): ExportNode =>
  ({
    declaration: {
      end: 20,
      id: { name: "parse", type: "Identifier" },
      parent: undefined,
      start: 0,
      type: "FunctionDeclaration"
    },
    parent: undefined,
    start: 0,
    type: "ExportNamedDeclaration"
  }) as unknown as ExportNode;

const makeContext = (
  commentsBefore: readonly Comment[] = []
): { context: RuleContext; reports: Report[] } => {
  const reports: Report[] = [];
  return {
    context: {
      filename: "source.ts",
      report: (report: Report) => {
        reports.push(report);
      },
      sourceCode: {
        getCommentsBefore: () =>
          commentsBefore.map((comment) => ({
            ...comment,
            end: comment.value.length + 4,
            start: 0
          })),
        text: `/*${commentsBefore[0]?.value ?? ""}*/\n`
      }
    } as unknown as RuleContext,
    reports
  };
};

describe("pnpm TSDoc rule", () => {
  test("reports an exported TypeScript function without TSDoc", () => {
    const { context, reports } = makeContext();
    tsdoc.rules["require-export-tsdoc"]
      .create(context)
      .ExportNamedDeclaration?.(makeExportedFunction());
    expect(reports).toHaveLength(1);
    expect(reports[0]?.messageId).toBe("missingTsdoc");
    expect(reports[0]?.data).toEqual({ kind: "function", name: "parse" });
  });
  test.each(["* Parse input. ", "* Parse input.\n ", "*\n * Parse input. "])(
    "reports documentation with a shared delimiter line: %s",
    (value) => {
      const { context, reports } = makeContext([{ type: "Block", value }]);
      tsdoc.rules["require-export-tsdoc"]
        .create(context)
        .ExportNamedDeclaration?.(makeExportedFunction());
      expect(reports[0]?.messageId).toBe("multilineTsdoc");
      expect(reports).toHaveLength(1);
    }
  );
  test("allows a documented export and ignores JavaScript files", () => {
    const documented = makeContext([
      { type: "Block", value: "*\n * Parse input.\n " }
    ]);
    tsdoc.rules["require-export-tsdoc"]
      .create(documented.context)
      .ExportNamedDeclaration?.(makeExportedFunction());
    expect(documented.reports).toEqual([]);
    const javascript = makeContext();
    javascript.context.filename = "source.js";
    expect(
      Object.keys(
        tsdoc.rules["require-export-tsdoc"].create(javascript.context)
      )
    ).toEqual([]);
  });
});

test("native Oxlint checks TSDoc delimiter lines on exports and methods", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-tsdoc-"));
  try {
    await writeFile(
      path.join(directory, "oxlint.json"),
      JSON.stringify({
        jsPlugins: [fileURLToPath(new URL("tsdoc-plugin.ts", import.meta.url))],
        rules: { "tsdoc/require-export-tsdoc": "error" }
      })
    );
    await writeFile(
      path.join(directory, "valid.ts"),
      'const marker = "\u{1F600}";\n' +
        "/**\n * Parse input.\n */\nexport function parse() { return marker; }\n" +
        "/**\r\n * Store values.\r\n */\r\nexport class Store {\r\n" +
        "  /**\r\n   * Read the stored value.\r\n   */\r\n  read() { return this.#internal(); }\r\n  private hidden() {}\r\n  protected inherited() {}\r\n  #internal() {}\r\n}\r\n" +
        "/**\n * Store a value.\n */\n/**\n * Keep it immutable.\n */\nexport const adjacentDocumented = 1;\n"
    );
    await writeFile(
      path.join(directory, "invalid.ts"),
      "/** Parse input. */\nexport function parse() { return 1; }\n" +
        "/** Store values.\n */\nexport class Store {\n" +
        "  /**\n   * Read the stored value. */\n  read() { return 1; }\n}\n" +
        "export const undocumented = 1;\n" +
        "/**\n * Return a value.\n */ export function closingCode() { return 1; }\n" +
        "const value = 1; /**\n * Return the stored value.\n */\nexport function openingCode() { return value; }\n" +
        "/**\n * First.\n */\n/** Second. */\nexport const adjacentInline = 1;\n" +
        "/** First. */\n/**\n * Second.\n */\nexport const adjacentMultiline = 1;\n"
    );
    await writeFile(
      path.join(directory, "source.js"),
      "export function parse() { return 1; }\n"
    );
    for (const [filename, exitCode, diagnosticCount] of [
      ["valid.ts", 0, 0],
      ["invalid.ts", 1, 8],
      ["source.js", 0, 0]
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
      expect(result.status).toBe(exitCode);
      const { diagnostics } = JSON.parse(result.stdout.toString());
      expect(diagnostics).toHaveLength(diagnosticCount);
      if (filename === "invalid.ts") {
        expect(
          diagnostics.filter((diagnostic: { message: string }) =>
            diagnostic.message.includes("Use multiline TSDoc")
          )
        ).toHaveLength(7);
        expect(
          diagnostics.filter((diagnostic: { message: string }) =>
            diagnostic.message.includes("Missing TSDoc")
          )
        ).toHaveLength(1);
      }
    }
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});
