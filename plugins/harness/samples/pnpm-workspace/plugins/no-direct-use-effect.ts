import type { CallExpression, Node } from "@oxc-project/types";

interface Binding {
  readonly defs: readonly {
    readonly type: string;
    readonly node: Node;
    readonly parent: Node | null;
  }[];
}

interface Scope {
  readonly upper: Scope | null;
  readonly variables: readonly (Binding & { readonly name: string })[];
}

interface RuleContext {
  readonly sourceCode: { readonly getScope: (node: Node) => Scope };
  readonly report: (diagnostic: {
    readonly messageId: string;
    readonly node: Node;
  }) => void;
}

interface Origin {
  readonly node: Node | null | undefined;
  readonly expected: "hook" | "namespace";
}

const nameOf = (node: Node): string | undefined => {
  if (node.type === "Identifier") {
    return node.name;
  }
  if (node.type === "Literal" && typeof node.value === "string") {
    return node.value;
  }
  return undefined;
};

const bindingFor = (
  node: Node,
  name: string,
  context: RuleContext
): Binding | undefined => {
  let scope: Scope | null = context.sourceCode.getScope(node);
  while (scope) {
    const binding = scope.variables.find((variable) => variable.name === name);
    if (binding) {
      return binding;
    }
    scope = scope.upper;
  }
  return undefined;
};

const matchesImport = (
  binding: Binding["defs"][number],
  expected: Origin["expected"]
): boolean => {
  const { node, parent } = binding;
  if (
    parent?.type !== "ImportDeclaration" ||
    parent.source.value !== "react" ||
    parent.importKind === "type"
  ) {
    return false;
  }
  return node.type === "ImportSpecifier"
    ? node.importKind !== "type" &&
        nameOf(node.imported) ===
          (expected === "hook" ? "useEffect" : "default")
    : expected === "namespace" &&
        (node.type === "ImportDefaultSpecifier" ||
          node.type === "ImportNamespaceSpecifier");
};

const variableOrigin = (
  binding: Binding["defs"][number],
  name: string,
  expected: Origin["expected"]
): Origin | false => {
  const { node, parent } = binding;
  if (
    binding.type !== "Variable" ||
    parent?.type !== "VariableDeclaration" ||
    parent.kind !== "const" ||
    node.type !== "VariableDeclarator"
  ) {
    return false;
  }
  if (node.id.type === "Identifier") {
    return { expected, node: node.init };
  }
  if (node.id.type === "ObjectPattern") {
    const property = node.id.properties.find(
      (candidate) =>
        candidate.type === "Property" &&
        candidate.value.type === "Identifier" &&
        candidate.value.name === name
    );
    if (
      expected === "hook" &&
      property?.type === "Property" &&
      (!property.computed || property.key.type === "Literal") &&
      nameOf(property.key) === "useEffect"
    ) {
      return { expected: "namespace", node: node.init };
    }
  }
  return false;
};

const bindingOrigin = (
  node: Extract<Node, { type: "Identifier" }>,
  expected: Origin["expected"],
  context: RuleContext
): Origin | boolean => {
  const definition = bindingFor(node, node.name, context)?.defs;
  const binding = definition?.length === 1 ? definition[0] : undefined;
  if (!binding) {
    return false;
  }
  return binding.type === "ImportBinding"
    ? matchesImport(binding, expected)
    : variableOrigin(binding, node.name, expected);
};

const isCommonJsNamespace = (node: Node, context: RuleContext): boolean =>
  node.type === "CallExpression" &&
  node.callee.type === "Identifier" &&
  node.callee.name === "require" &&
  (bindingFor(node.callee, "require", context)?.defs.length ?? 0) === 0 &&
  node.arguments.length === 1 &&
  node.arguments[0]?.type === "Literal" &&
  node.arguments[0].value === "react";

const isReactEffect = (callee: Node, context: RuleContext): boolean => {
  let current: Node | null | undefined = callee;
  let expected: Origin["expected"] = "hook";
  const visited = new Set<Node>();
  while (current && !visited.has(current)) {
    visited.add(current);
    if (
      current.type === "ChainExpression" ||
      current.type === "TSAsExpression" ||
      current.type === "TSTypeAssertion" ||
      current.type === "TSNonNullExpression" ||
      current.type === "TSSatisfiesExpression"
    ) {
      current = current.expression;
    } else if (current.type === "SequenceExpression") {
      current = current.expressions.at(-1);
    } else if (
      current.type === "MemberExpression" &&
      expected === "hook" &&
      (!current.computed || current.property.type === "Literal") &&
      nameOf(current.property) === "useEffect"
    ) {
      expected = "namespace";
      current = current.object;
    } else if (current.type === "Identifier") {
      const origin = bindingOrigin(current, expected, context);
      if (typeof origin === "boolean") {
        return origin;
      }
      ({ node: current, expected } = origin);
    } else {
      return expected === "namespace" && isCommonJsNamespace(current, context);
    }
  }
  return false;
};

const noDirectUseEffectRule = {
  create(context: RuleContext) {
    return {
      CallExpression(node: CallExpression) {
        if (isReactEffect(node.callee, context)) {
          context.report({ messageId: "noDirectUseEffect", node: node.callee });
        }
      }
    };
  },
  meta: {
    docs: {
      description:
        "Disallow direct calls to React's useEffect and require framework-provided effect hooks."
    },
    messages: {
      noDirectUseEffect:
        "Call a framework-provided effect hook instead of React's 'useEffect'."
    },
    type: "problem"
  } as const
};

/**
 * Diagnose source-proven React useEffect calls without rewriting lifecycle behavior.
 */
export default {
  meta: { name: "react-effects" },
  rules: { "no-direct-use-effect": noDirectUseEffectRule }
};
