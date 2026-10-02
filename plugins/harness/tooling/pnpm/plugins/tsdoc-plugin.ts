import type {
  Class,
  ClassElement,
  ExportDefaultDeclaration,
  ExportNamedDeclaration,
  Function as OxcFunction,
  MethodDefinition,
  Node,
  PropertyKey,
  VariableDeclaration
} from "@oxc-project/types";

/**
 * Oxlint comment token shape used by sourceCode comment lookup.
 */
interface Comment {
  end: number;
  start: number;
  type: string;
  value: string;
}

/**
 * Minimal Oxlint-compatible rule context surface used by this plugin.
 */
interface RuleContext {
  filename?: string;
  getFilename?: () => string;
  sourceCode: {
    text: string;
    getCommentsBefore: (node: Node) => Comment[];
  };
  report: (diagnostic: {
    data?: Record<string, string | undefined>;
    messageId: string;
    node: Node;
  }) => void;
}

/**
 * Extract a printable property key name from an OXC AST property key.
 *
 * @param key Property key node from a class method definition.
 * @returns The identifier or literal key name, or undefined for computed keys.
 */
const literalKeyName = (key: PropertyKey): string | undefined =>
  "value" in key &&
  (typeof key.value === "string" ||
    typeof key.value === "number" ||
    typeof key.value === "boolean" ||
    typeof key.value === "bigint")
    ? String(key.value)
    : undefined;

const propertyKeyName = (key: PropertyKey): string | undefined =>
  "name" in key && typeof key.name === "string"
    ? key.name
    : literalKeyName(key);

/**
 * Extract the display name for a declaration or method node.
 *
 * @param node Class, function, or method node to name for diagnostics.
 * @returns The declared name or "[anonymous]" when no stable name exists.
 */
const nodeName = (node: Class | MethodDefinition | OxcFunction): string => {
  if ("id" in node && node.id?.name) {
    return node.id.name;
  }
  return "key" in node
    ? (propertyKeyName(node.key) ?? "[anonymous]")
    : "[anonymous]";
};

/**
 * Narrow a class element to public method definitions requiring TSDoc.
 *
 * @param element Class body element to inspect.
 * @returns True when the element is a non-constructor public method.
 */
const isPublicClassElement = (
  element: ClassElement
): element is MethodDefinition =>
  element.type === "MethodDefinition" &&
  element.kind !== "constructor" &&
  element.key.type !== "PrivateIdentifier" &&
  element.accessibility !== "private" &&
  element.accessibility !== "protected";

/**
 * Narrow an AST node to a function declaration.
 *
 * @param node AST node to inspect.
 * @returns True when the node is a function declaration.
 */
const isFunctionDeclaration = (
  node: Node
): node is OxcFunction & { type: "FunctionDeclaration" } =>
  node.type === "FunctionDeclaration";

/**
 * Narrow an AST node to a variable declaration.
 *
 * @param node AST node to inspect.
 * @returns True when the node is a variable declaration.
 */
const isVariableDeclaration = (node: Node): node is VariableDeclaration =>
  node.type === "VariableDeclaration";

/**
 * Narrow an AST node to a class declaration.
 *
 * @param node AST node to inspect.
 * @returns True when the node is a class declaration.
 */
const isClassDeclaration = (
  node: Node
): node is Class & { type: "ClassDeclaration" } =>
  node.type === "ClassDeclaration";

/**
 * Extract the display name for the first declarator in a variable declaration.
 *
 * @param node Variable declaration node to inspect.
 * @returns The first binding identifier name, or a generic declaration label.
 */
const variableName = (node: VariableDeclaration): string =>
  node.declarations[0]?.id &&
  "name" in node.declarations[0].id &&
  typeof node.declarations[0].id.name === "string"
    ? node.declarations[0].id.name
    : "variable declaration";

/**
 * Require multiline TSDoc on exported TypeScript public API declarations.
 */
const exportTsdocRule = {
  create(context: RuleContext) {
    const { sourceCode } = context;
    /**
     * Find TSDoc blocks before a node or its export wrapper.
     *
     * @param node AST node to inspect.
     * @returns Documentation comment tokens without their block delimiters.
     */
    const tsdocComments = (node: Node): Comment[] =>
      [
        ...sourceCode.getCommentsBefore(node),
        ...(node.parent?.type.startsWith("Export") === true
          ? sourceCode.getCommentsBefore(node.parent)
          : [])
      ].filter(
        (comment) => comment.type === "Block" && comment.value.startsWith("*")
      );
    /**
     * Report missing TSDoc or delimiters that share a documentation line.
     *
     * @param node AST node to attach the diagnostic to.
     * @param kind Human-readable declaration kind for the diagnostic message.
     * @param name Human-readable declaration name for the diagnostic message.
     * @returns Nothing.
     */
    const report = (node: Node, kind: string, name: string): void => {
      const comments = tsdocComments(node);
      if (
        comments.length === 0 ||
        !comments.every(
          (comment) =>
            /^\*[ \t]*\r?\n[\s\S]*\r?\n[ \t]*$/u.test(comment.value) &&
            /^[ \t]*$/u.test(
              sourceCode.text.slice(
                sourceCode.text.lastIndexOf("\n", comment.start - 1) + 1,
                comment.start
              )
            ) &&
            /^[ \t\r]*$/u.test(
              sourceCode.text.slice(comment.end).split("\n", 1)[0] ?? ""
            )
        )
      ) {
        context.report({
          data: { kind, name },
          messageId: comments.length === 0 ? "missingTsdoc" : "multilineTsdoc",
          node
        });
      }
    };
    /**
     * Validate TSDoc on an exported class and its public methods.
     *
     * @param node Exported class declaration node to validate.
     * @returns Nothing.
     */
    const validateExportedClass = (node: Class): void => {
      report(node, "class", nodeName(node));
      for (const element of node.body.body) {
        if (isPublicClassElement(element)) {
          report(element, "method", nodeName(element));
        }
      }
    };
    /**
     * Validate TSDoc on a supported exported declaration node.
     *
     * @param node Exported declaration node from an export wrapper.
     * @returns Nothing.
     */
    const validateExportedDeclaration = (
      node: Node | null | undefined
    ): void => {
      if (!node) {
        return;
      }
      if (isFunctionDeclaration(node)) {
        report(node, "function", nodeName(node));
      } else if (isVariableDeclaration(node)) {
        report(node, "variable", variableName(node));
      } else if (isClassDeclaration(node)) {
        validateExportedClass(node);
      }
    };
    if (
      !/\.(?:ts|tsx)$/u.test(context.filename ?? context.getFilename?.() ?? "")
    ) {
      return {};
    }
    return {
      ExportDefaultDeclaration(node: ExportDefaultDeclaration) {
        validateExportedDeclaration(node.declaration);
      },
      ExportNamedDeclaration(node: ExportNamedDeclaration) {
        validateExportedDeclaration(node.declaration);
      }
    };
  },
  meta: {
    docs: {
      description:
        "Require multiline TSDoc on exported TypeScript public API declarations."
    },
    messages: {
      missingTsdoc:
        'Missing TSDoc for exported public API {{kind}} "{{name}}".',
      multilineTsdoc:
        'Use multiline TSDoc with opening and closing delimiters on separate lines for exported public API {{kind}} "{{name}}".'
    },
    type: "suggestion"
  } as const
};

/**
 * Oxlint JS plugin exporting custom TSDoc rules.
 */
const plugin = {
  meta: { name: "tsdoc" },
  rules: {
    "require-export-tsdoc": exportTsdocRule
  }
};

/**
 * Provide the native rule for exported TypeScript API documentation.
 */
export default plugin;
