import type {
  Class,
  ClassElement,
  ExportDefaultDeclaration,
  ExportNamedDeclaration,
  MethodDefinition,
  Node,
  PropertyDefinition,
  TSExportAssignment,
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
 * Extract a printable name from an identifier, literal, or qualified name node.
 *
 * @param value Candidate AST node or nullish value.
 * @returns The printable name, or undefined when the node carries none.
 */
const nameOf = (value: unknown): string | undefined => {
  let name: string | undefined;
  if (typeof value === "object" && value !== null) {
    if ("name" in value && typeof value.name === "string") {
      ({ name } = value);
    } else if (
      "value" in value &&
      (typeof value.value === "string" ||
        typeof value.value === "number" ||
        typeof value.value === "boolean" ||
        typeof value.value === "bigint")
    ) {
      name = String(value.value);
    } else if ("left" in value && "right" in value) {
      const left = nameOf(value.left);
      const right = nameOf(value.right);
      name =
        left !== undefined && right !== undefined
          ? `${left}.${right}`
          : undefined;
    }
  }
  return name;
};

/**
 * Extract the display name for a declaration, method, or property node.
 *
 * @param node Declaration, method, or property node to name for diagnostics.
 * @returns The declared name or "[anonymous]" when no stable name exists.
 */
const nodeName = (node: Node): string =>
  nameOf("id" in node ? node.id : undefined) ??
  nameOf("key" in node ? node.key : undefined) ??
  "[anonymous]";

/**
 * Narrow a class element to public members that require TSDoc.
 *
 * @param element Class body element to inspect.
 * @returns True for non-constructor public methods, including abstract ones,
 *   and for public properties initialized with a function.
 */
const isPublicClassElement = (
  element: ClassElement
): element is MethodDefinition | PropertyDefinition =>
  (element.type === "MethodDefinition" ||
    element.type === "TSAbstractMethodDefinition" ||
    ((element.type === "PropertyDefinition" ||
      element.type === "TSAbstractPropertyDefinition") &&
      (element.value?.type === "ArrowFunctionExpression" ||
        element.value?.type === "FunctionExpression"))) &&
  !("kind" in element && element.kind === "constructor") &&
  element.key.type !== "PrivateIdentifier" &&
  element.accessibility !== "private" &&
  element.accessibility !== "protected";

/**
 * Diagnostic labels for exported declaration node types.
 */
const declarationKinds: Readonly<Record<string, string>> = {
  ClassDeclaration: "class",
  FunctionDeclaration: "function",
  TSDeclareFunction: "function",
  TSEnumDeclaration: "enum",
  TSImportEqualsDeclaration: "import alias",
  TSInterfaceDeclaration: "interface",
  TSModuleDeclaration: "namespace",
  TSTypeAliasDeclaration: "type alias",
  VariableDeclaration: "variable"
};

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
  nameOf(node.declarations[0]?.id) ?? "variable declaration";

/**
 * Check TSDoc on exported TypeScript public API declarations.
 */
const exportTsdocRule = {
  create(context: RuleContext) {
    const { sourceCode } = context;
    const checkedClasses = new WeakSet<Class>();
    const checkedDeclarations = new WeakSet<Node>();
    const checkedVariableDeclarators = new WeakSet<Node>();
    const checkedVariableComments = new WeakSet<Node>();
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
     * @param requirePresence Whether a missing comment is a diagnostic.
     * @returns Nothing.
     */
    const report = (
      node: Node,
      kind: string,
      name: string,
      requirePresence = true
    ): void => {
      const comments = tsdocComments(node);
      if (
        (comments.length === 0 && requirePresence) ||
        (comments.length > 0 &&
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
          ))
      ) {
        context.report({
          data: { kind, name },
          messageId: comments.length === 0 ? "missingTsdoc" : "multilineTsdoc",
          node
        });
      }
    };
    /**
     * Validate an exposed class's public members once, regardless of export count.
     *
     * @param node Exposed class declaration node to validate.
     * @returns Nothing.
     */
    const validateClassMembers = (node: Class): void => {
      if (!checkedClasses.has(node)) {
        checkedClasses.add(node);
        for (const element of node.body.body) {
          if (isPublicClassElement(element)) {
            report(
              element,
              element.type.endsWith("PropertyDefinition")
                ? "property"
                : "method",
              nodeName(element)
            );
          }
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
      exportNode?: Node
    ): void => {
      if (node && !checkedDeclarations.has(node)) {
        checkedDeclarations.add(node);
        const kind = declarationKinds[node.type];
        const documentationNode =
          tsdocComments(node).length > 0 ? node : (exportNode ?? node);
        if (isClassDeclaration(node)) {
          report(documentationNode, "class", nodeName(node));
          validateClassMembers(node);
        } else if (kind !== undefined) {
          report(
            documentationNode,
            kind,
            node.type === "VariableDeclaration"
              ? variableName(node)
              : nodeName(node),
            node.type !== "TSTypeAliasDeclaration" &&
              (node.type !== "VariableDeclaration" ||
                node.declarations.some(
                  ({ init }) =>
                    init?.type === "ArrowFunctionExpression" ||
                    init?.type === "FunctionExpression" ||
                    init?.type === "ClassExpression"
                ))
          );
          if (node.type === "VariableDeclaration") {
            for (const declaration of node.declarations) {
              if (declaration.init?.type === "ClassExpression") {
                validateClassMembers(declaration.init);
              }
            }
          }
        }
      }
    };
    const validateLocalVariable = (
      declaration: VariableDeclaration,
      node: Node,
      name: string
    ): void => {
      for (const declarator of declaration.declarations) {
        if (
          declarator.id.type === "Identifier" &&
          declarator.id.name === name
        ) {
          const requirePresence =
            declarator.init?.type === "ArrowFunctionExpression" ||
            declarator.init?.type === "FunctionExpression" ||
            declarator.init?.type === "ClassExpression";
          if (
            !checkedDeclarations.has(declaration) &&
            !checkedVariableDeclarators.has(declarator)
          ) {
            checkedVariableDeclarators.add(declarator);
            const documentationNode =
              tsdocComments(declaration).length > 0 ? declaration : node;
            if (!checkedVariableComments.has(documentationNode)) {
              if (tsdocComments(documentationNode).length > 0) {
                checkedVariableComments.add(documentationNode);
              }
              report(documentationNode, "variable", name, requirePresence);
            }
          }
          if (declarator.init?.type === "ClassExpression") {
            validateClassMembers(declarator.init);
          }
        }
      }
    };
    const validateLocalDeclaration = (
      node: Node,
      name: string | undefined
    ): void => {
      const scope = node.parent;
      if (
        name !== undefined &&
        scope &&
        "body" in scope &&
        Array.isArray(scope.body)
      ) {
        for (const statement of scope.body) {
          const declaration =
            statement.type === "ExportNamedDeclaration"
              ? statement.declaration
              : statement;
          if (
            declaration &&
            "id" in declaration &&
            nameOf(declaration.id) === name
          ) {
            validateExportedDeclaration(declaration, node);
          } else if (declaration?.type === "VariableDeclaration") {
            validateLocalVariable(declaration, node, name);
          }
        }
      }
    };
    return /\.(?:[cm]?ts|tsx)$/u.test(
      context.filename ?? context.getFilename?.() ?? ""
    )
      ? {
          ExportDefaultDeclaration(node: ExportDefaultDeclaration) {
            if (node.declaration.type in declarationKinds) {
              validateExportedDeclaration(node.declaration);
            } else if (node.declaration.type === "Identifier") {
              validateLocalDeclaration(node, node.declaration.name);
            }
          },
          ExportNamedDeclaration(node: ExportNamedDeclaration) {
            if (node.declaration) {
              validateExportedDeclaration(node.declaration);
            } else if (!node.source) {
              for (const specifier of node.specifiers) {
                validateLocalDeclaration(node, nameOf(specifier.local));
              }
            }
          },
          TSExportAssignment(node: TSExportAssignment) {
            if (node.expression.type === "Identifier") {
              validateLocalDeclaration(node, node.expression.name);
            }
          }
        }
      : {};
  },
  meta: {
    docs: {
      description: "Check TSDoc on exported TypeScript public API declarations."
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
