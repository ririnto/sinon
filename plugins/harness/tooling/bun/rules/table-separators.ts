import type { Rule } from "markdownlint";

const tableSeparatorCandidatePattern = /^:?-+:?$/u;

/**
 * Markdownlint rule requiring exactly three hyphens in each table separator cell.
 */
const rule: Rule = {
  description: "Markdown table separators must use exactly three hyphens",
  function: (params, onError) => {
    for (const [index, line] of params.lines.entries()) {
      const trimmed = line.trim();
      const cells =
        trimmed.startsWith("|") && trimmed.endsWith("|")
          ? trimmed
              .slice(1, -1)
              .split("|")
              .map((cell) => cell.trim())
          : [];
      const isSeparatorRow =
        cells.length > 0 &&
        cells.every((cell) => tableSeparatorCandidatePattern.test(cell));
      const invalidCell = isSeparatorRow
        ? cells.findIndex((cell) => cell !== "---")
        : -1;
      if (invalidCell !== -1) {
        onError({
          context: trimmed,
          detail: `Separator cell ${invalidCell + 1} must be exactly ---.`,
          lineNumber: index + 1
        });
      }
    }
  },
  names: ["docs/table-separators"],
  parser: "none",
  tags: ["docs", "tables"]
};

export default rule;
