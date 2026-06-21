#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import type { Rule } from "markdownlint@0.41.0";

const boxDrawingPattern = /(?<boxDrawing>[\u2500-\u257F])/u;

/**
 * Markdownlint rule rejecting Unicode box drawing characters.
 */
const rule: Rule = {
  names: ["docs/no-box-drawing"],
  description: "Unicode box drawing characters are not allowed in Markdown",
  tags: ["docs", "unicode"],
  parser: "none",
  function: (params, onError) => {
    params.lines.forEach((line, index) => {
      const match = boxDrawingPattern.exec(line);
      if (match?.groups?.boxDrawing) {
        const character = match.groups.boxDrawing;
        onError({
          lineNumber: index + 1,
          detail: "Use ASCII tree markers such as +-- and | instead.",
          context: line.trim(),
          range: [match.index + 1, character.length],
        });
      }
    });
  },
};

export default rule;
