import type { Rule } from "markdownlint";

/**
 * Report the first Unicode box-drawing character on each Markdown line without a fix.
 */
export default {
  description: "Unicode box drawing characters are not allowed in Markdown",
  function({ lines }, onError) {
    for (const [index, line] of lines.entries()) {
      const match = /(?<character>[\u2500-\u257F])/u.exec(line);
      if (match?.groups?.character !== undefined) {
        onError({
          context: line.trim(),
          detail: "Use ASCII tree markers such as +-- and | instead.",
          lineNumber: index + 1,
          range: [match.index + 1, match.groups.character.length]
        });
      }
    }
  },
  names: ["docs/no-box-drawing"],
  parser: "none",
  tags: ["docs", "unicode"]
} satisfies Rule;
