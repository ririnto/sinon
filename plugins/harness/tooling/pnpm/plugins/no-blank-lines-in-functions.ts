import type { Node } from "@oxc-project/types";

import { isFunctionNode, siblingGaps } from "./style-support.ts";
import type { RuleContext, SourceRange } from "./style-support.ts";

/**
 * Report physical blank lines inside function bodies outside protected and registration ranges.
 */
export const noBlankLinesRule = {
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
