import type { ESTree, Comment, Context, Plugin, Rule } from "@oxlint/plugins";

const emDash = "—";

const noEmDashRule = {
  create(context: Context) {
    const inspectComment = (comment: Comment, program: ESTree.Node): void => {
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
      JSXText: (node: ESTree.Node): void => {
        if (
          node.type === "JSXText" &&
          node.value.trim() !== emDash &&
          node.value.trim().includes(emDash)
        ) {
          context.report({ messageId: "noEmDash", node });
        }
      },
      Literal: (node: ESTree.Node): void => {
        if (
          node.type === "Literal" &&
          typeof node.value === "string" &&
          node.value !== emDash &&
          node.value.includes(emDash)
        ) {
          context.report({ messageId: "noEmDash", node });
        }
      },
      Program: (node: ESTree.Node): void => {
        for (const comment of context.sourceCode.getAllComments()) {
          inspectComment(comment, node);
        }
      },
      TemplateLiteral: (node: ESTree.Node): void => {
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
} satisfies Rule;

/**
 * Provide the native Oxlint plugin for em-dash prose checks.
 */
const plugin = {
  meta: { name: "style" },
  rules: { "no-em-dash": noEmDashRule }
} satisfies Plugin;

export default plugin;
