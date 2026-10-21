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

interface Scope {
  readonly upper: Scope | null;
  readonly variables: readonly {
    readonly name: string;
    readonly defs: readonly {
      readonly type: string;
      readonly node: Node;
      readonly parent: Node | null;
    }[];
  }[];
}

interface RuleContext {
  readonly sourceCode: {
    readonly getLocFromIndex: (offset: number) => {
      line: number;
      column: number;
    };
    readonly getScope: (node: Node) => Scope;
    readonly getCommentsInside: (node: Node) => readonly Comment[];
    readonly getTokens: (node: Node) => readonly SourceRange[];
    readonly text: string;
  };
  readonly report: (diagnostic: {
    readonly loc?: {
      readonly start: { readonly line: number; readonly column: number };
    };
    readonly messageId: string;
    readonly node: Node;
  }) => void;
}

type FunctionNode =
  | (OxcFunction & {
      readonly type: "FunctionDeclaration" | "FunctionExpression";
    })
  | ArrowFunctionExpression;

/**
 * Narrow parser nodes to the function forms inspected by this rule.
 */
const isFunctionNode = (node: Node): node is FunctionNode =>
  node.type === "FunctionDeclaration" ||
  node.type === "FunctionExpression" ||
  node.type === "ArrowFunctionExpression";

const registrations = new Set([
  "describe",
  "test",
  "it",
  "beforeEach",
  "afterEach",
  "beforeAll",
  "afterAll"
]);
const frameworks = new Set(["vitest", "@jest/globals", "bun:test"]);

const registrationCallee = (node: Node): Node | undefined => {
  if (node.type === "CallExpression") {
    let { callee } = node;
    if (
      callee.type === "CallExpression" &&
      callee.callee.type === "MemberExpression" &&
      !callee.callee.computed &&
      callee.callee.property.type === "Identifier" &&
      callee.callee.property.name === "each"
    ) {
      callee = callee.callee.object;
    }
    while (
      callee.type === "MemberExpression" &&
      !callee.computed &&
      callee.property.type === "Identifier" &&
      ["only", "skip", "concurrent", "sequential"].includes(
        callee.property.name
      )
    ) {
      callee = callee.object;
    }
    return callee;
  }
  return undefined;
};

const registration = (node: Node, context: RuleContext): string | undefined => {
  const callee = registrationCallee(node);
  if (callee?.type === "Identifier") {
    let scope: Scope | null = context.sourceCode.getScope(callee);
    while (scope) {
      const variable = scope.variables.find(({ name }) => name === callee.name);
      if (variable) {
        const definition =
          variable.defs.length === 1 ? variable.defs[0] : undefined;
        const specifier = definition?.node;
        const source = definition?.parent;
        if (
          definition?.type === "ImportBinding" &&
          specifier?.type === "ImportSpecifier" &&
          source?.type === "ImportDeclaration" &&
          frameworks.has(source.source.value) &&
          specifier.imported.type === "Identifier" &&
          registrations.has(specifier.imported.name)
        ) {
          return specifier.imported.name;
        }
        return undefined;
      }
      scope = scope.upper;
    }
  }
  return undefined;
};

const siblingGaps = (node: Node, context: RuleContext): SourceRange[] => {
  const { parent } = node;
  const call = parent?.parent;
  const suite =
    node.type === "BlockStatement" &&
    parent !== undefined &&
    parent !== null &&
    isFunctionNode(parent) &&
    call?.type === "CallExpression" &&
    call.arguments.includes(parent) &&
    registration(call, context) === "describe";
  const gaps: SourceRange[] = [];
  if (node.type === "Program" || (node.type === "BlockStatement" && suite)) {
    for (const [index, statement] of node.body.entries()) {
      const previous = node.body[index - 1];
      if (
        previous?.type === "ExpressionStatement" &&
        statement.type === "ExpressionStatement" &&
        registration(previous.expression, context) !== undefined &&
        registration(statement.expression, context) !== undefined
      ) {
        gaps.push({ end: statement.start, start: previous.end });
      }
    }
  }
  return gaps;
};

const noBlankLinesRule = {
  create(context: RuleContext) {
    const reportedOffsets = new Set<number>();
    const registrationGaps: SourceRange[] = [];
    const inspectFunction = (node: Node): void => {
      if (isFunctionNode(node) && node.body?.type === "BlockStatement") {
        const { body } = node;
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
            (index) =>
              !(bodyText[index - 1] === "\r" && bodyText[index] === "\n")
          )
          .map((index) => index + body.start)
          .filter(
            (offset) =>
              !isProtected(offset) &&
              !isExempted(offset) &&
              !registrationGaps.some(
                ({ start, end }) => start <= offset && offset < end
              )
          );
        for (const offset of blankLineOffsets) {
          if (!reportedOffsets.has(offset)) {
            reportedOffsets.add(offset);
            context.report({
              loc: { start: context.sourceCode.getLocFromIndex(offset) },
              messageId: "noBlankLines",
              node
            });
          }
        }
      }
    };
    return {
      "ArrowFunctionExpression:exit": inspectFunction,
      BlockStatement: (node: Node) => {
        registrationGaps.push(...siblingGaps(node, context));
      },
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

const plugin = {
  meta: { name: "style" },
  rules: { "no-blank-lines-in-functions": noBlankLinesRule }
};

/**
 * Provide the native Oxlint plugin for function-body blank-line checks.
 */
export default plugin;
