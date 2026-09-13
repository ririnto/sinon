import { describe, expect, test } from "bun:test";

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
  const context = {
    filename: "source.ts",
    report: (report: Report) => {
      reports.push(report);
    },
    sourceCode: {
      getCommentsBefore: () => commentsBefore
    }
  } as unknown as RuleContext;
  return { context, reports };
};

describe("Bun TSDoc rule", () => {
  test("reports an exported TypeScript function without TSDoc", () => {
    const { context, reports } = makeContext();
    const visitor = tsdoc.rules["require-export-tsdoc"].create(context);
    const inspect = visitor["ExportNamedDeclaration"];
    if (inspect) {
      inspect(makeExportedFunction());
    }
    expect(reports).toHaveLength(1);
    expect(reports[0]?.messageId).toBe("missingTsdoc");
    expect(reports[0]?.data).toEqual({ kind: "function", name: "parse" });
  });
  test("allows a documented export and ignores JavaScript files", () => {
    const documented = makeContext([
      { type: "Block", value: "* Parse input." }
    ]);
    const documentedVisitor = tsdoc.rules["require-export-tsdoc"].create(
      documented.context
    );
    const inspect = documentedVisitor["ExportNamedDeclaration"];
    if (inspect) {
      inspect(makeExportedFunction());
    }
    expect(documented.reports).toEqual([]);
    const javascript = makeContext();
    javascript.context.filename = "source.js";
    const javascriptVisitor = tsdoc.rules["require-export-tsdoc"].create(
      javascript.context
    );
    expect(Object.keys(javascriptVisitor)).toEqual([]);
  });
});
