import type {
  ArrowFunctionExpression,
  Function as OxcFunction,
  Node
} from "@oxc-project/types";

interface SourceRange {
  readonly end: number;
  readonly start: number;
}

interface Comment extends SourceRange {
  readonly type: string;
  readonly value: string;
}

interface RuleContext {
  readonly sourceCode: {
    readonly getCommentsInside: (node: Node) => readonly Comment[];
    readonly getTokens: (node: Node) => readonly SourceRange[];
    readonly text: string;
  };
  readonly report: (diagnostic: {
    readonly messageId: string;
    readonly node: Node;
  }) => void;
}

type FunctionNode =
  | (OxcFunction & {
      readonly type: "FunctionDeclaration" | "FunctionExpression";
    })
  | ArrowFunctionExpression;

const isFunctionNode = (node: Node): node is FunctionNode =>
  node.type === "FunctionDeclaration" ||
  node.type === "FunctionExpression" ||
  node.type === "ArrowFunctionExpression";

const noInlineCommentsRule = {
  create(context: RuleContext) {
    const reportedOffsets = new Set<number>();
    const inspectFunction = (node: Node): void => {
      if (!isFunctionNode(node)) {
        return;
      }
      const { body } = node;
      if (!body || body.type !== "BlockStatement") {
        return;
      }
      const forbiddenComments = context.sourceCode
        .getCommentsInside(body)
        .filter(
          ({ type, value }) =>
            type === "Line" || (type === "Block" && !value.startsWith("*"))
        );
      for (const { start } of forbiddenComments) {
        if (reportedOffsets.has(start)) {
          continue;
        }
        reportedOffsets.add(start);
        context.report({ messageId: "noInlineComments", node });
      }
    };
    return {
      "ArrowFunctionExpression:exit": inspectFunction,
      "FunctionDeclaration:exit": inspectFunction,
      "FunctionExpression:exit": inspectFunction
    };
  },
  meta: {
    docs: {
      description: "Disallow non-documentation comments inside function bodies."
    },
    messages: {
      noInlineComments: "Remove inline comments from function bodies."
    },
    type: "problem"
  } as const
};

const noBlankLinesRule = {
  create(context: RuleContext) {
    const reportedOffsets = new Set<number>();
    const inspectFunction = (node: Node): void => {
      if (!isFunctionNode(node)) {
        return;
      }
      const { body } = node;
      if (!body || body.type !== "BlockStatement") {
        return;
      }
      const tokens = context.sourceCode.getTokens(body);
      const comments = context.sourceCode.getCommentsInside(body);
      const isProtected = (offset: number): boolean =>
        tokens.some(({ start, end }) => start <= offset && offset < end) ||
        comments.some(({ start, end }) => start <= offset && offset < end);
      const firstStatementIndex = body.body.findIndex(
        (element) =>
          !("directive" in element && typeof element.directive === "string")
      );
      const exemptionStart =
        firstStatementIndex > 0
          ? body.body[firstStatementIndex - 1]?.end
          : undefined;
      const exemptionEnd =
        firstStatementIndex > 0
          ? body.body[firstStatementIndex]?.start
          : undefined;
      const isExempted = (offset: number): boolean =>
        exemptionStart !== undefined &&
        exemptionEnd !== undefined &&
        exemptionStart <= offset &&
        offset < exemptionEnd;
      const bodyText = context.sourceCode.text.slice(body.start, body.end);
      const blankLineOffsets = Array.from(
        bodyText.matchAll(/^[^\S\r\n\u2028\u2029]*$/gmu),
        ({ index }) => index
      )
        .filter((index): index is number => index !== undefined)
        .filter(
          (index) => !(bodyText[index - 1] === "\r" && bodyText[index] === "\n")
        )
        .map((index) => index + body.start)
        .filter((offset) => !isProtected(offset) && !isExempted(offset));
      for (const offset of blankLineOffsets) {
        if (reportedOffsets.has(offset)) {
          continue;
        }
        reportedOffsets.add(offset);
        context.report({ messageId: "noBlankLines", node });
      }
    };
    return {
      "ArrowFunctionExpression:exit": inspectFunction,
      "FunctionDeclaration:exit": inspectFunction,
      "FunctionExpression:exit": inspectFunction
    };
  },
  meta: {
    docs: {
      description: "Disallow physical blank lines inside function bodies."
    },
    messages: {
      noBlankLines: "Remove blank lines from function bodies."
    },
    type: "problem"
  } as const
};

const style = {
  meta: { name: "style" },
  rules: {
    "no-blank-lines-in-functions": noBlankLinesRule,
    "no-inline-comments-in-functions": noInlineCommentsRule
  }
};

export default style;
