import type { Node } from "@oxc-project/types";

import { siblingGaps } from "./style-support.ts";
import type { RuleContext } from "./style-support.ts";

/**
 * Report spacing violations between adjacent scope-proven test registrations.
 */
export const siblingTestSpacingRule = {
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
