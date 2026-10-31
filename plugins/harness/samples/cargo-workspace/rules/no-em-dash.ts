import type { Rule } from "markdownlint";

import {
  protectText,
  protectedDocument,
  proseBlocks,
  rebuildLine,
  segmentsByLine,
  selectedDocument
} from "./markdown-prose.ts";

/**
 * Report authored prose punctuation without rewriting source or quotations.
 */
const noEmDash: Rule = {
  description: "Avoid em dashes in authored prose",
  function: (params, onError) => {
    if (!protectedDocument(params) && selectedDocument(params)) {
      for (const paragraph of proseBlocks(
        params.parsers.micromark?.tokens ?? []
      )) {
        const sourceLines = [...segmentsByLine(paragraph)];
        const protectedLines = protectText(
          sourceLines.map(([, segments]) => rebuildLine(segments)).join("\n")
        ).split("\n");
        for (const [index, [lineNumber]] of sourceLines.entries()) {
          for (const match of (protectedLines[index] ?? "").matchAll(
            /\u2014/gu
          )) {
            onError({ lineNumber, range: [(match.index ?? 0) + 1, 1] });
          }
        }
      }
    }
  },
  names: ["docs/no-em-dash"],
  parser: "micromark",
  tags: ["docs", "prose"]
};

/**
 * Provide the no-em-dash Markdownlint rule.
 */
export default noEmDash;
