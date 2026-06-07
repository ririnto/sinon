import type {
  Class,
  ClassElement,
  ExportDefaultDeclaration,
  ExportNamedDeclaration,
  Function as OxcFunction,
  MethodDefinition,
  Node,
  PropertyKey,
  VariableDeclaration,
} from "@oxc-project/types";

/**
 * Oxlint comment token shape used by sourceCode comment lookup.
 */
interface Comment {
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
const propertyKeyName = (key: PropertyKey): string | undefined => {
  if ("name" in key && typeof key.name === "string") {
    return key.name;
  }
  if (
    "value" in key &&
    (typeof key.value === "string" ||
      typeof key.value === "number" ||
      typeof key.value === "boolean" ||
      typeof key.value === "bigint")
  ) {
    return String(key.value);
  }
  return undefined;
};

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
  if ("key" in node) {
    return propertyKeyName(node.key) ?? "[anonymous]";
  }
  return "[anonymous]";
};

/**
 * Narrow a class element to public method definitions requiring TSDoc.
 *
 * @param element Class body element to inspect.
 * @returns True when the element is a non-constructor public method.
 */
const isPublicClassElement = (
  element: ClassElement,
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
  node: Node,
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
  node: Node,
): node is Class & { type: "ClassDeclaration" } =>
  node.type === "ClassDeclaration";

/**
 * Extract the display name for the first declarator in a variable declaration.
 *
 * @param node Variable declaration node to inspect.
 * @returns The first binding identifier name, or a generic declaration label.
 */
const variableName = (node: VariableDeclaration): string => {
  if (
    node.declarations[0]?.id &&
    "name" in node.declarations[0].id &&
    typeof node.declarations[0].id.name === "string"
  ) {
    return node.declarations[0].id.name;
  }
  return "variable declaration";
};

/**
 * Require TSDoc on exported TypeScript public API declarations.
 */
const exportTsdocRule = {
  create(context: RuleContext) {
    const { filename, sourceCode } = context;
    const isTypeScript = /\.(?:ts|tsx)$/u.test(
      filename ?? context.getFilename?.() ?? "",
    );
    /**
     * Determine whether a node or its export wrapper has a TSDoc block.
     *
     * @param node AST node to inspect.
     * @returns True when a directly preceding block comment starts with `*`.
     */
    const hasTsdoc = (node: Node): boolean => {
      if (
        sourceCode
          .getCommentsBefore(node)
          .some(
            (comment) =>
              comment.type === "Block" && comment.value.startsWith("*"),
          )
      ) {
        return true;
      }
      if (node.parent?.type.startsWith("Export")) {
        return sourceCode
          .getCommentsBefore(node.parent)
          .some(
            (comment) =>
              comment.type === "Block" && comment.value.startsWith("*"),
          );
      }
      return false;
    };
    /**
     * Report a missing-TSDoc diagnostic unless the node is already documented.
     *
     * @param node AST node to attach the diagnostic to.
     * @param kind Human-readable declaration kind for the diagnostic message.
     * @param name Human-readable declaration name for the diagnostic message.
     * @returns Nothing.
     */
    const report = (node: Node, kind: string, name: string): void => {
      if (hasTsdoc(node)) {
        return;
      }
      context.report({
        data: { kind, name },
        messageId: "missingTsdoc",
        node,
      });
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
      node: Node | null | undefined,
    ): void => {
      if (!node) {
        return;
      }
      if (isFunctionDeclaration(node)) {
        report(node, "function", nodeName(node));
        return;
      }
      if (isVariableDeclaration(node)) {
        report(node, "variable", variableName(node));
        return;
      }
      if (isClassDeclaration(node)) {
        validateExportedClass(node);
      }
    };
    if (!isTypeScript) {
      return {};
    }
    return {
      ExportDefaultDeclaration(node: ExportDefaultDeclaration) {
        validateExportedDeclaration(node.declaration);
      },
      ExportNamedDeclaration(node: ExportNamedDeclaration) {
        validateExportedDeclaration(node.declaration);
      },
    };
  },
  meta: {
    docs: {
      description:
        "Require TSDoc on exported TypeScript public API declarations.",
    },
    messages: {
      missingTsdoc:
        'Missing TSDoc for exported public API {{kind}} "{{name}}".',
    },
    type: "suggestion",
  } as const,
};

/**
 * Oxlint JS plugin exporting custom TSDoc rules.
 */
const plugin = {
  meta: { name: "tsdoc" },
  rules: {
    "require-export-tsdoc": exportTsdocRule,
  },
};

export default plugin;
