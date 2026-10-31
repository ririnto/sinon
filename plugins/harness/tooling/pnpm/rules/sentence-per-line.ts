import type { Rule } from "markdownlint";

import {
  protectText,
  protectedDocument,
  proseBlocks,
  rebuildLine,
  segmentsByLine
} from "./markdown-prose.ts";

const terminatorPattern = /[.!?]/gu;
const whitespacePattern = /\s/u;
const semicolonPattern = /(?<semicolon>[;；;])/gu;
const abbreviationTailPattern =
  /(?:\b(?:a\.m|p\.m|e\.g|i\.e|etc|vs|cf|approx|Inc|Ltd|Co|Corp|Jr|Sr|Dr|Mr|Mrs|Ms|St|Fig|No|ed|al)|(?:[A-Z]\.)+[A-Z])$/u;
const initialNameContextPattern =
  /(?:^|\b(?:Ask|Contact|by|from|with|(?:Dr|Mr|Mrs|Ms)\.?)\s+)[A-Z]$/u;
const surnameStartPattern = /^[A-Z][a-z]+\b/u;
const entityTailPattern = /&(?:#\d+|#[\da-fA-F]+|[a-zA-Z][\d]{2,31})$/u;
const conjunctiveAdverbPattern =
  /^(?:however|therefore|thus|hence|instead|otherwise|besides|accordingly|consequently|furthermore|moreover|nevertheless|nonetheless|similarly|likewise|meanwhile|subsequently|additionally|alternatively)\b/iu;
const imperativeTailVerbs =
  "retry|rerun|resume|restart|return|repeat|replace|remove|restore|reset|rebuild|record|report|repoint|reject|require|request|reuse|run|write|use|set|send|keep|leave|copy|check|confirm|continue|verify|wait|add|apply|change|delete|drop|edit|fix|load|open|pin|scan|show|skip|test|treat|validate|view";
const finiteTailVerbs =
  "is|are|was|were|be|been|being|has|have|had|does|do|did|will|would|can|could|shall|should|may|might|must|stays?|remains?|starts?|ends?|fails?|passes?|follows?|returns?|owns?|moves?|loses?|gains?|keeps?|uses?|covers?|certif(?:ies|y)|validates?|loads?|ships?|runs?|exists?|" +
  `(?:${imperativeTailVerbs
    .split("|")
    .filter(
      (stem) => stem.endsWith("e") && stem !== "leave" && stem !== "write"
    )
    .map((stem) => `${stem}d`)
    .join(
      "|"
    )})(?=\\s+the\\s+(?!(?:most|least|worst|[\\p{L}\\p{N}\\p{M}'’-]*est)(?:[.!?;；]|$))[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}'’-]*(?:[.!?;；]|$))`;
const subjectLeads =
  "this|that|these|those|it|they|we|you|he|she|who|which|another|each|every|some|all|both|one|two|three|the|a|an";
const clauseStartPattern = new RegExp(
  `^(?:(?:${imperativeTailVerbs})\\b|(?!${subjectLeads}\\b)[a-z\\d][\\w'’-]*\\s+(?:${finiteTailVerbs})\\b|(?:this|that|these|those|it|they|we|you|he|she|who|which)\\s+(?:${finiteTailVerbs})\\b|(?:${subjectLeads})\\b(?:\\s+[a-z][\\w'’-]*){1,2}\\s+(?:${finiteTailVerbs})\\b)`,
  "iu"
);
const imperativeContinuationPattern = new RegExp(
  `^(?:${imperativeTailVerbs}|preserve|do)\\b`,
  "iu"
);
const sentenceStartPattern = /^[A-Z0-9\p{Script=Hangul}"'“([「『*]/u;
const wordBeforePattern = /[\w\p{Script=Hangul}"”’\])]/u;
const wordAfterPattern = /[\w"'“([/]/u;
const closingQuotePattern = /^["'”’]+/u;
interface Finding {
  column: number;
  length: number;
}

/**
 * Finds clear wrapped prose or unterminated instructions on adjacent source lines.
 */
const continuation = (
  previous: string | undefined,
  line: string
): Finding | undefined => {
  const start = line.trimStart();
  if (
    previous !== undefined &&
    previous !== "" &&
    /[a-z0-9]$/iu.test(previous.trimEnd()) &&
    (/^[a-z]/u.test(start) || imperativeContinuationPattern.test(start))
  ) {
    return { column: line.length - start.length + 1, length: 1 };
  }
  return undefined;
};

/**
 * Finds a terminator that ends one independent sentence and starts another.
 */
const joinedSentence = (line: string): Finding | undefined => {
  for (const match of line.matchAll(terminatorPattern)) {
    const index = match.index ?? 0;
    const after = line.slice(index + 1).replace(closingQuotePattern, "");
    const [next] = after;
    const before = line.slice(0, index);
    const remainder = after.trim();
    if (
      (next === undefined || whitespacePattern.test(next)) &&
      wordBeforePattern.test(line[index - 1] ?? "") &&
      (!abbreviationTailPattern.test(before) ||
        (/\b(?:etc|Inc|Ltd|Co|Corp)$/u.test(before) &&
          clauseStartPattern.test(remainder))) &&
      !(
        initialNameContextPattern.test(before.trimStart()) &&
        surnameStartPattern.test(remainder) &&
        !imperativeContinuationPattern.test(remainder)
      ) &&
      remainder !== "" &&
      sentenceStartPattern.test(remainder)
    ) {
      return { column: index + 1, length: 1 };
    }
  }
  return undefined;
};

/**
 * Finds a semicolon used to join independent prose on one line.
 */
const joinedSemicolon = (line: string): Finding | undefined => {
  for (const match of line.matchAll(semicolonPattern)) {
    const index = match.index ?? 0;
    const before = line.slice(0, index);
    const tail = line.slice(index + 1).trimStart();
    const previous = before.trimEnd().at(-1);
    const next = tail.at(0);
    if (
      !entityTailPattern.test(before) &&
      previous !== undefined &&
      next !== undefined &&
      wordBeforePattern.test(previous) &&
      wordAfterPattern.test(next) &&
      !/(?:^|[.!?]\s+)[^.!?]*:/u.test(before) &&
      (conjunctiveAdverbPattern.test(tail) || clauseStartPattern.test(tail))
    ) {
      return {
        column: index + 1,
        length: match.groups?.semicolon.length ?? 0
      };
    }
  }
  return undefined;
};

/**
 * Markdownlint rule requiring one prose sentence per source line.
 */
const sentencePerLine: Rule = {
  description: "Prose sentences must be separated by physical source lines",
  function: (params, onError) => {
    if (!protectedDocument(params)) {
      const reported = new Set<string>();
      for (const paragraph of proseBlocks(
        params.parsers.micromark?.tokens ?? []
      )) {
        const sourceLines = [...segmentsByLine(paragraph)];
        const protectedLines = protectText(
          sourceLines.map(([, segments]) => rebuildLine(segments)).join("\n")
        ).split("\n");
        for (const [index, [lineNumber]] of sourceLines.entries()) {
          const line = protectedLines[index] ?? "";
          const continuationFinding =
            sourceLines[index - 1]?.[0] === lineNumber - 1
              ? continuation(protectedLines[index - 1], line)
              : undefined;
          if (continuationFinding) {
            onError({
              context: (params.lines[lineNumber - 1] ?? "").trim(),
              detail: /^[a-z]/u.test(line.trimStart())
                ? "Sentence continues from the previous source line; keep the sentence on one line."
                : "End the previous instruction with a sentence terminator before this continuation.",
              lineNumber,
              range: [continuationFinding.column, continuationFinding.length]
            });
          }
          for (const [kind, finding] of [
            ["sentences", joinedSentence(line)],
            ["semicolon", joinedSemicolon(line)]
          ] as const) {
            if (finding) {
              const key = `${lineNumber}:${kind}`;
              if (!reported.has(key)) {
                reported.add(key);
                onError({
                  context: (params.lines[lineNumber - 1] ?? "").trim(),
                  detail:
                    kind === "sentences"
                      ? "Multiple sentences on one line; put each sentence on its own line."
                      : "Semicolon-joined sentences on one line; put each sentence on its own line.",
                  lineNumber,
                  range: [finding.column, finding.length]
                });
              }
            }
          }
        }
      }
    }
  },
  names: ["docs/sentence-per-line"],
  parser: "micromark",
  tags: ["docs", "sentences"]
};

/**
 * Provide the sentence-per-line Markdownlint rule.
 */
export default sentencePerLine;
