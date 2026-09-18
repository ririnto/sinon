import type { Rule } from "markdownlint";

const boxDrawingPattern = /[\u{2500}-\u{257F}]/u;

/**
 * Markdownlint rule rejecting Unicode box drawing characters (U+2500 to
 * U+257F) in Markdown documents.
 */
const rule: Rule = {
  description: "Unicode box drawing characters are not allowed in Markdown",
  function: (params, onError) => {
    for (const [index, line] of params.lines.entries()) {
      const match = boxDrawingPattern.exec(line);
      if (match) {
        onError({
          context: line.trim(),
          detail: "Use ASCII tree markers such as +-- and | instead.",
          lineNumber: index + 1,
          range: [match.index + 1, match[0].length]
        });
      }
    }
  },
  names: ["docs/no-box-drawing"],
  parser: "none",
  tags: ["docs", "unicode"]
};

export default rule;
