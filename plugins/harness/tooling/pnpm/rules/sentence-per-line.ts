import type { MicromarkToken, Rule } from "markdownlint";

const terminatorPattern = /[.!?]/gu;
const whitespacePattern = /\s/u;
const semicolonPattern = /[;；;]/gu;
const abbreviationTailPattern =
  /(?:\b(?:e\.g|i\.e|etc|vs|cf|approx|Inc|Ltd|Co|Corp|Jr|Sr|Dr|Mr|Mrs|Ms|St|Fig|No|ed|al)|(?:[A-Z]\.)+[A-Z])$/u;
const entityTailPattern = /&(?:#\d+|#[\da-fA-F]+|[a-zA-Z][\d]{2,31})$/u;
const conjunctiveAdverbPattern =
  /\b(?:however|therefore|thus|hence|instead|otherwise|besides|accordingly|consequently|furthermore|moreover|nevertheless|nonetheless|similarly|likewise|meanwhile|subsequently|additionally|alternatively)\b/iu;
const imperativeTailVerbs =
  "retry|rerun|resume|restart|return|repeat|replace|remove|restore|reset|rebuild|record|report|repoint|reject|require|request|reuse|run|write|use|set|send|keep|leave|copy|check|confirm|continue|verify|wait|add|apply|change|delete|drop|edit|fix|load|open|pin|scan|show|skip|test|treat|validate|view";
const finiteTailVerbs =
  "is|are|was|were|be|been|being|has|have|had|does|do|did|will|would|can|could|shall|should|may|might|must|stays?|remains?|starts?|ends?|fails?|passes?|follows?|returns?|owns?|moves?|loses?|gains?|keeps?|uses?|covers?|certif(?:ies|y)|validates?|loads?|ships?|runs?|exists?";
const subjectLeads =
  "this|that|these|those|it|they|we|you|he|she|who|which|another|each|every|some|all|both|one|two|three|the|a|an";
const lowercaseClauseStartPattern = new RegExp(
  `^(?:(?:${imperativeTailVerbs})\\b|(?!${subjectLeads}\\b)[a-z][\\w'’-]*\\s+(?:${finiteTailVerbs})\\b|(?:${subjectLeads})\\b(?:\\s+[a-z][\\w'’-]*){1,2}\\s+(?:${finiteTailVerbs})\\b)`,
  "u"
);
const sentenceLikeStartPattern = /^[A-Z0-9]/u;
const sentenceStartPattern = /^[A-Z0-9"'“([「『*]/u;
const wordBeforePattern = /[\w"”’\])]/u;
const wordAfterPattern = /[\w"'“([/]/u;
const skippedContainerTypes = new Set([
  "htmlFlow",
  "codeFenced",
  "codeIndented",
  "htmlFlowData"
]);

interface Segment {
  startColumn: number;
  text: string;
}

interface Finding {
  column: number;
  length: number;
}

/**
 * Walks token children depth-first and collects prose blocks outside excluded containers.
 */
const proseBlocks = (tokens: readonly MicromarkToken[]): MicromarkToken[] => {
  const blocks: MicromarkToken[] = [];
  const visit = (token: MicromarkToken) => {
    if (token.type === "paragraph") {
      blocks.push(token);
      return;
    }
    if (skippedContainerTypes.has(token.type)) {
      return;
    }
    for (const child of token.children) {
      visit(child);
    }
  };
  for (const token of tokens) {
    visit(token);
  }
  return blocks;
};

/**
 * Collects prose text segments of one paragraph grouped by physical line.
 */
const segmentsByLine = (paragraph: MicromarkToken): Map<number, Segment[]> => {
  const byLine = new Map<number, Segment[]>();
  const visit = (token: MicromarkToken) => {
    if (token.type === "link") {
      return;
    }
    if (token.type === "data" && token.text.trim() !== "") {
      const lines = token.text.split(/\r\n|\r|\n/u);
      for (const [index, text] of lines.entries()) {
        if (text.trim() !== "") {
          const lineNumber = token.startLine + index;
          const startColumn = index === 0 ? token.startColumn : 1;
          const bucket = byLine.get(lineNumber) ?? [];
          bucket.push({ startColumn, text });
          byLine.set(lineNumber, bucket);
        }
      }
    }
    for (const child of token.children) {
      visit(child);
    }
  };
  visit(paragraph);
  return byLine;
};

/**
 * Rebuilds one physical line from prose segments, keeping UTF-16 source columns.
 */
const rebuildLine = (segments: readonly Segment[]): string => {
  const units: (string | undefined)[] = [];
  for (const segment of segments) {
    const offset = segment.startColumn - 1;
    for (let index = 0; index < segment.text.length; index += 1) {
      units[offset + index] = segment.text[index];
    }
  }
  let line = "";
  for (const unit of units) {
    line += unit ?? " ";
  }
  return line;
};

/**
 * Finds a terminator that ends one independent sentence and starts another.
 */
const joinedSentence = (line: string): Finding | undefined => {
  for (const match of line.matchAll(terminatorPattern)) {
    const index = match.index ?? 0;
    const next = line[index + 1];
    const before = line.slice(0, index);
    const remainder = line.slice(index + 1).trim();
    if (
      (next === undefined || whitespacePattern.test(next)) &&
      wordBeforePattern.test(line[index - 1] ?? "") &&
      !abbreviationTailPattern.test(before) &&
      remainder !== "" &&
      sentenceStartPattern.test(remainder)
    ) {
      return { column: index + 1, length: 1 };
    }
  }
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
      wordAfterPattern.test(next)
    ) {
      const firstTailWord = tail.match(/^[\w'’-]+/u)?.[0] ?? "";
      const independent =
        sentenceLikeStartPattern.test(firstTailWord) ||
        conjunctiveAdverbPattern.test(tail) ||
        lowercaseClauseStartPattern.test(tail);
      if (independent) {
        return { column: index + 1, length: [...match[0]].length };
      }
    }
  }
};

/**
 * Markdownlint rule requiring one prose sentence per source line.
 */
const rule: Rule = {
  description: "Prose sentences must be separated by physical source lines",
  function: (params, onError) => {
    const tokens = params.parsers.micromark?.tokens ?? [];
    const reported = new Set<string>();
    for (const paragraph of proseBlocks(tokens)) {
      for (const [lineNumber, segments] of segmentsByLine(paragraph)) {
        const line = rebuildLine(segments);
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
  },
  names: ["docs/sentence-per-line"],
  parser: "micromark",
  tags: ["docs", "sentences"]
};

/**
 * Provide the one-sentence-per-line Markdownlint rule.
 */
export default rule;
