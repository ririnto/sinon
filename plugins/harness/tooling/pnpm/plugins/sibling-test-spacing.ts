import type {
  ArrowFunctionExpression,
  Function as OxcFunction,
  Node
} from "@oxc-project/types";

interface SourceRange {
  readonly end: number;
  readonly start: number;
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
    readonly getScope: (node: Node) => Scope;
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

const siblingTestSpacingRule = {
  create(context: RuleContext) {
    const inspect = (node: Node): void => {
      for (const gap of siblingGaps(node, context)) {
        if (
          /^\s*$/u.test(context.sourceCode.text.slice(gap.start, gap.end)) &&
          context.sourceCode.text
            .slice(gap.start, gap.end)
            .split(/\r\n|[\n\r\u2028\u2029]/u).length !== 3
        ) {
          context.report({ messageId: "siblingSpacing", node });
        }
      }
    };
    return { BlockStatement: inspect, Program: inspect };
  },
  meta: {
    docs: {
      description:
        "Require one blank line between proven sibling test registrations."
    },
    messages: {
      siblingSpacing:
        "Separate sibling tests and lifecycle registrations with one blank line."
    },
    type: "problem"
  } as const
};

const plugin = {
  meta: { name: "style" },
  rules: { "sibling-test-spacing": siblingTestSpacingRule }
};

/**
 * Provide the native Oxlint plugin for test-registration spacing checks.
 */
export default plugin;
