import { noBlankLinesRule } from "./no-blank-lines-in-functions.ts";
import { noInlineCommentsRule } from "./no-inline-comments-in-functions.ts";
import { siblingTestSpacingRule } from "./sibling-test-spacing.ts";

const style = {
  meta: { name: "style" },
  rules: {
    "no-blank-lines-in-functions": noBlankLinesRule,
    "no-inline-comments-in-functions": noInlineCommentsRule,
    "sibling-test-spacing": siblingTestSpacingRule
  }
};

/**
 * Provide the native custom source style rules.
 */
export default style;
