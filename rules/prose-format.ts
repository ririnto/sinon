import type { Rule } from "markdownlint";

const proseContainers = new Set([
  "paragraph",
  "atxHeadingText",
  "setextHeadingText"
]);
const literalInlines = new Set([
  "codeText",
  "literalAutolink",
  "autolink",
  "link",
  "image",
  "htmlText",
  "characterReference",
  "characterEscape"
]);

/**
 * Check sentence joins in Markdown prose without editing the source.
 * Adapted from Workgraph. See prose-format.LICENSE for its license.
 */
export default {
  description: "Prose sentences must be separated by source lines",
  function: (params, onError) => {
    const pending = [...(params.parsers.micromark?.tokens ?? [])];
    for (
      let token = pending.pop();
      token !== undefined;
      token = pending.pop()
    ) {
      if (proseContainers.has(token.type)) {
        const lines = new Map<number, string>();
        const inline = [...token.children];
        for (
          let child = inline.pop();
          child !== undefined;
          child = inline.pop()
        ) {
          if (child.type === "data") {
            lines.set(
              child.startLine,
              (
                lines.get(child.startLine) ?? params.lines[child.startLine - 1]
              ).replace(/^\s*(?:\d+[.)]|[-+*])\s+/u, (prefix) =>
                " ".repeat(prefix.length)
              )
            );
          } else if (literalInlines.has(child.type)) {
            for (
              let lineNumber = child.startLine;
              lineNumber <= child.endLine;
              lineNumber += 1
            ) {
              const line =
                lines.get(lineNumber) ?? params.lines[lineNumber - 1];
              const start =
                lineNumber === child.startLine ? child.startColumn - 1 : 0;
              const end =
                lineNumber === child.endLine
                  ? child.endColumn - 1
                  : line.length;
              lines.set(
                lineNumber,
                `${line.slice(0, start)}${"x".repeat(end - start)}${line.slice(end)}`
              );
            }
          } else {
            for (const descendant of child.children) {
              inline.push(descendant);
            }
          }
        }
        for (const [lineNumber, line] of lines) {
          if (
            /[.!?]["')\]]*\s+(?:[A-Z][a-z]+\s+[a-z]|[A-Z][a-z]+[.!?])/u.test(
              line
            )
          ) {
            onError({
              detail: "Put each sentence on its own source line.",
              lineNumber
            });
          }
          if (
            /;\s+(?:(?:and|but|so|then)\s+(?:it|this|that|they|we|you|the\s+\w+)\s+|(?:however|therefore),?\s+|(?:it|this|that|they|we|you|the\s+\w+)\s+)(?:is|are|was|were|has|have|can|must|should|will|does|do)\b/iu.test(
              line
            )
          ) {
            onError({
              detail: "Do not join prose with semicolons.",
              lineNumber
            });
          }
        }
      } else {
        for (const child of token.children) {
          pending.push(child);
        }
      }
    }
  },
  names: ["docs/prose-format"],
  parser: "micromark",
  tags: ["docs"]
} satisfies Rule;
