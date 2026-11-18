import path from "node:path";

import type { Rule } from "markdownlint";
import { isAlias, isMap, LineCounter, parseDocument, visit } from "yaml";

/**
 * Check the YAML mapping used to load packaged skills without rewriting metadata.
 */
export default {
  description: "Packaged skills must start with a valid YAML mapping",
  function: ({ name, frontMatterLines }, onError) => {
    if (
      !path.matchesGlob(
        path.relative(process.cwd(), name),
        "plugins/*/skills/*/SKILL.md"
      )
    ) {
      return;
    }
    if (frontMatterLines[0]?.trimEnd() !== "---") {
      onError({
        detail: "Expected YAML frontmatter.",
        frontMatter: frontMatterLines.length > 0,
        lineNumber: 1
      });
      return;
    }
    const lineCounter = new LineCounter();
    const document = parseDocument(
      frontMatterLines
        .slice(
          0,
          frontMatterLines.findLastIndex((line) => line.trimEnd() !== "")
        )
        .join("\n"),
      { lineCounter, prettyErrors: false }
    );
    if (document.errors.length > 0) {
      for (const error of document.errors) {
        onError({
          detail: error.message,
          frontMatter: true,
          lineNumber: lineCounter.linePos(error.pos[0]).line
        });
      }
    } else if (isMap(document.contents)) {
      const anchors = new Set<string>();
      visit(document, {
        Node: (_key, node) => {
          if (node.anchor !== undefined) {
            anchors.add(node.anchor);
          } else if (isAlias(node) && !anchors.has(node.source)) {
            onError({
              detail: `No preceding anchor for *${node.source}.`,
              frontMatter: true,
              lineNumber: lineCounter.linePos(node.range?.[0] ?? 0).line
            });
          }
        }
      });
    } else {
      onError({
        detail: "Expected a YAML mapping.",
        frontMatter: true,
        lineNumber: 1
      });
    }
  },
  names: ["docs/skill-frontmatter"],
  parser: "none",
  tags: ["skills"]
} satisfies Rule;
