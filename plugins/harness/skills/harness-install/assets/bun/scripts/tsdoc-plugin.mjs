const nodeName = function nodeName(node) {
  if (node.id?.name) {
    return node.id.name;
  }
  if (node.key?.name) {
    return node.key.name;
  }
  if (node.key?.value) {
    return String(node.key.value);
  }
  return "[anonymous]";
};

const isPublicClassElement = function isPublicClassElement(element) {
  return (
    element.type === "MethodDefinition" &&
    element.kind !== "constructor" &&
    element.accessibility !== "private" &&
    element.accessibility !== "protected"
  );
};

const exportTsdocRule = {
  create(context) {
    const { filename, sourceCode } = context;
    const targetFilename = filename ?? context.getFilename?.() ?? "";
    const isTypeScript = /\.(?:ts|tsx)$/u.test(targetFilename);
    const hasTsdoc = function hasTsdoc(node) {
      const hasNodeTsdoc = sourceCode
        .getCommentsBefore(node)
        .some(
          (comment) => comment.type === "Block" && comment.value.startsWith("*")
        );
      if (hasNodeTsdoc) {
        return true;
      }
      if (node.parent?.type?.startsWith("Export")) {
        return sourceCode
          .getCommentsBefore(node.parent)
          .some(
            (comment) =>
              comment.type === "Block" && comment.value.startsWith("*")
          );
      }
      return false;
    };
    const report = function report(node, kind, name) {
      if (hasTsdoc(node)) {
        return;
      }
      context.report({
        data: { kind, name },
        messageId: "missingTsdoc",
        node,
      });
    };
    const validateExportedClass = function validateExportedClass(node) {
      report(node, "class", nodeName(node));
      for (const element of node.body.body) {
        if (isPublicClassElement(element)) {
          report(element, "method", nodeName(element));
        }
      }
    };
    const validateExportedDeclaration = function validateExportedDeclaration(
      node
    ) {
      if (!node) {
        return;
      }
      if (node.type === "FunctionDeclaration") {
        report(node, "function", nodeName(node));
        return;
      }
      if (node.type === "VariableDeclaration") {
        const [declaration] = node.declarations;
        report(
          node,
          "variable",
          declaration?.id?.name ?? "variable declaration"
        );
        return;
      }
      if (node.type === "ClassDeclaration") {
        validateExportedClass(node);
      }
    };
    if (!isTypeScript) {
      return {};
    }
    return {
      ExportDefaultDeclaration(node) {
        validateExportedDeclaration(node.declaration);
      },
      ExportNamedDeclaration(node) {
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
  },
};

const plugin = {
  meta: {
    name: "tsdoc",
  },
  rules: {
    "require-export-tsdoc": exportTsdocRule,
  },
};

export default plugin;
