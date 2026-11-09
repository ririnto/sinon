import type { Node } from "@oxc-project/types";

interface Comment {
  readonly start: number;
  readonly type: string;
  readonly value: string;
}

interface SourcePosition {
  readonly column: number;
  readonly line: number;
}

interface RuleContext {
  readonly sourceCode: {
    readonly getAllComments: () => readonly Comment[];
    readonly getLocFromIndex: (offset: number) => SourcePosition;
  };
  readonly report: (diagnostic: {
    readonly loc?: { readonly start: SourcePosition };
    readonly messageId: string;
    readonly node: Node;
  }) => void;
}

const emDash = "—";

const noEmDashRule = {
  create(context: RuleContext) {
    const inspectComment = (comment: Comment, program: Node): void => {
      if (comment.type === "Shebang") {
        return;
      }
      let offset = comment.value.indexOf(emDash);
      while (offset !== -1) {
        context.report({
          loc: {
            start: context.sourceCode.getLocFromIndex(
              comment.start + 2 + offset
            )
          },
          messageId: "noEmDash",
          node: program
        });
        offset = comment.value.indexOf(emDash, offset + 1);
      }
    };
    return {
      JSXText: (node: Node): void => {
        if (
          node.type === "JSXText" &&
          node.value.trim() !== emDash &&
          node.value.trim().includes(emDash)
        ) {
          context.report({ messageId: "noEmDash", node });
        }
      },
      Literal: (node: Node): void => {
        if (
          node.type === "Literal" &&
          typeof node.value === "string" &&
          node.value !== emDash &&
          node.value.includes(emDash)
        ) {
          context.report({ messageId: "noEmDash", node });
        }
      },
      Program: (node: Node): void => {
        for (const comment of context.sourceCode.getAllComments()) {
          inspectComment(comment, node);
        }
      },
      TemplateLiteral: (node: Node): void => {
        if (
          node.type === "TemplateLiteral" &&
          !(
            node.quasis.length === 1 && node.quasis[0]?.value.cooked === emDash
          ) &&
          node.quasis.some(({ value }) =>
            (value.cooked ?? value.raw).includes(emDash)
          )
        ) {
          context.report({ messageId: "noEmDash", node });
        }
      }
    };
  },
  meta: {
    docs: {
      description: "Disallow em dashes in comments and string or JSX prose."
    },
    messages: {
      noEmDash:
        "Replace the em dash with a comma, colon, or a separate sentence."
    },
    type: "problem"
  }
};

/**
 * Provide the native Oxlint plugin for em-dash prose checks.
 */
const plugin = {
  meta: { name: "style" },
  rules: { "no-em-dash": noEmDashRule }
};

export default plugin;
