import { describe, expect, test } from "bun:test";

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

describe("Bun style rules", () => {
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
    const text = "function valid() { /** documentation */ return 1; }";
    const comments = [
      { end: 41, start: 21, type: "Block", value: "* documentation " }
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
