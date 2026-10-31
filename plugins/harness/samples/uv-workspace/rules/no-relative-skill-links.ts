import type { MicromarkToken, Rule } from "markdownlint";

import {
  alertPattern,
  protectText,
  protectedDocument,
  selectedDocument,
  skippedContainerTypes
} from "./markdown-prose.ts";

/**
 * Diagnose relative skill-file destinations without resolving or rewriting links.
 */
const noRelativeSkillLinks: Rule = {
  description: "Use skill names instead of relative skill-file links",
  function: (params, onError) => {
    if (!protectedDocument(params) && selectedDocument(params)) {
      const visit = (
        token: MicromarkToken,
        quoted = new Map<number, string>()
      ): void => {
        let protectedLines = quoted;
        if (token.type === "paragraph") {
          protectedLines = new Map(
            protectText(token.text)
              .split(/\r\n|\r|\n/u)
              .map((line, index) => [
                token.startLine + index,
                `${" ".repeat(index === 0 ? token.startColumn - 1 : 0)}${line}`
              ])
          );
        }
        if (
          token.type === "resourceDestination" ||
          token.type === "definitionDestination"
        ) {
          const destination = token.text.replaceAll(/^<|>$/gu, "");
          const comparable = destination
            .replace(/[?#][\s\S]*$/u, "")
            .replaceAll(/%[\da-fA-F]{2}/gu, (escape) => {
              const decoded = String.fromCodePoint(
                Number.parseInt(escape.slice(1), 16)
              );
              return /^[\w.~-]$/u.test(decoded) ? decoded : escape;
            });
          const visible = protectedLines
            .get(token.startLine)
            ?.slice(token.startColumn - 1, token.endColumn - 1);
          if (
            (visible === undefined || visible.trim() !== "") &&
            !/^(?:[a-z][a-z\d+.-]*:|\/|#)/iu.test(destination) &&
            /(?:^|\/)SKILL\.md$/u.test(comparable)
          ) {
            onError({
              lineNumber: token.startLine,
              range: [
                token.startColumn,
                Math.max(1, token.endColumn - token.startColumn)
              ]
            });
          }
        } else if (
          !skippedContainerTypes.has(token.type) &&
          token.type !== "codeText" &&
          (token.type !== "blockQuote" || alertPattern.test(token.text))
        ) {
          for (const child of token.children) {
            visit(child, protectedLines);
          }
        }
      };
      for (const token of params.parsers.micromark?.tokens ?? []) {
        visit(token);
      }
    }
  },
  names: ["docs/no-relative-skill-links"],
  parser: "micromark",
  tags: ["docs", "links"]
};

/**
 * Provide the no-relative-skill-links Markdownlint rule.
 */
export default noRelativeSkillLinks;
