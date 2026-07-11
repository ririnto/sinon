// -*- coding: utf-8 -*-

export type MarkdownHeading = Readonly<{
  level: number;
  line: number;
  title: string;
}>;

export type MarkdownStructure = Readonly<{
  kind: "addendum" | "full";
}>;

export type MarkdownCodeBlock = Readonly<{
  content: string;
  language: string;
}>;

type Fence = Readonly<{
  character: "`" | "~";
  language: string;
  length: number;
}>;

const atxHeading = (
  line: string
): Omit<MarkdownHeading, "line"> | undefined => {
  const match =
    /^ {0,3}(?<marks>#{1,6})(?:(?:[ \t]+)(?<title>.*)|[ \t]*)$/u.exec(line);
  const marks = match?.groups?.["marks"];
  if (marks === undefined) {
    return undefined;
  }
  const title = (match?.groups?.["title"] ?? "")
    .replace(/[ \t]+#+[ \t]*$/u, "")
    .trim();
  return { level: marks.length, title };
};

const openingFence = (line: string): Fence | undefined => {
  const match = /^ {0,3}(?<marker>`{3,}|~{3,})(?<info>.*)$/u.exec(line);
  const marker = match?.groups?.["marker"];
  if (marker === undefined) {
    return undefined;
  }
  const [language = ""] = (match?.groups?.["info"] ?? "")
    .trim()
    .split(/[ \t]+/u);
  return {
    character: marker[0] === "`" ? "`" : "~",
    language,
    length: marker.length
  };
};

const closesFence = (line: string, fence: Fence): boolean => {
  const marker = /^ {0,3}(?<marker>`{3,}|~{3,})[ \t]*$/u.exec(line)?.groups?.[
    "marker"
  ];
  return (
    marker !== undefined &&
    marker[0] === fence.character &&
    marker.length >= fence.length
  );
};

/** Parse ATX headings while excluding frontmatter and fenced code blocks. */
export const parseMarkdownHeadings = (
  source: string
): readonly MarkdownHeading[] => {
  const headings: MarkdownHeading[] = [];
  let fence: Fence | undefined;
  const lines = source.split(/\r?\n/u);
  let start = 0;
  if (lines[0] === "---") {
    const end = lines.indexOf("---", 1);
    if (end !== -1) {
      start = end + 1;
    }
  }
  for (let index = start; index < lines.length; index += 1) {
    const text = lines[index] ?? "";
    if (fence !== undefined) {
      if (closesFence(text, fence)) {
        fence = undefined;
      }
      continue;
    }
    const opening = openingFence(text);
    if (opening !== undefined) {
      fence = opening;
      continue;
    }
    const heading = atxHeading(text);
    if (heading !== undefined) {
      headings.push({ ...heading, line: index });
    }
  }
  return headings;
};

/** Parse fenced code blocks with their declared language and inner content. */
export const parseFencedCodeBlocks = (
  source: string
): readonly MarkdownCodeBlock[] => {
  const codeBlocks: MarkdownCodeBlock[] = [];
  let fence: Fence | undefined;
  let content: string[] = [];
  for (const line of source.split(/\r?\n/u)) {
    if (fence !== undefined) {
      if (closesFence(line, fence)) {
        codeBlocks.push({
          content: content.join("\n"),
          language: fence.language
        });
        fence = undefined;
        content = [];
        continue;
      }
      content.push(line);
      continue;
    }
    const opening = openingFence(line);
    if (opening !== undefined) {
      fence = opening;
    }
  }
  return codeBlocks;
};

const hasSectionContent = (
  sourceLines: readonly string[],
  heading: MarkdownHeading,
  headings: readonly MarkdownHeading[]
): boolean => {
  const nextHeading = headings.find(
    (candidate) =>
      candidate.line > heading.line && candidate.level <= heading.level
  );
  const end = nextHeading?.line ?? sourceLines.length;
  return sourceLines
    .slice(heading.line + 1, end)
    .some((line) => line.trim() !== "" && atxHeading(line) === undefined);
};

const duplicateHeadingErrors = (
  headings: readonly MarkdownHeading[]
): readonly string[] => {
  const errors: string[] = [];
  const h1Titles = new Set<string>();
  const h2TitlesByParent = new Map<number, Set<string>>();
  let parent = 0;
  for (const heading of headings) {
    if (heading.level === 1) {
      parent += 1;
      if (h1Titles.has(heading.title)) {
        errors.push(`duplicate H1 heading ${heading.title}`);
      }
      h1Titles.add(heading.title);
      continue;
    }
    if (heading.level !== 2) {
      continue;
    }
    const titles = h2TitlesByParent.get(parent) ?? new Set<string>();
    if (titles.has(heading.title)) {
      errors.push(`duplicate H2 heading ${heading.title}`);
    }
    titles.add(heading.title);
    h2TitlesByParent.set(parent, titles);
  }
  return errors;
};

/** Return structural document errors without coupling checks to heading names or order. */
export const markdownStructureErrors = (
  source: string,
  structure: MarkdownStructure
): readonly string[] => {
  const headings = parseMarkdownHeadings(source);
  const h1 = headings.filter((heading) => heading.level === 1);
  const h2 = headings.filter((heading) => heading.level === 2);
  const errors = [...duplicateHeadingErrors(headings)];
  for (const heading of [...h1, ...h2]) {
    if (heading.title === "") {
      errors.push(`H${heading.level} heading must not be empty`);
    }
  }
  if (structure.kind === "full" && h1.length !== 1) {
    errors.push("full document must contain exactly one H1 heading");
  }
  if (structure.kind === "addendum" && h1.length > 0) {
    errors.push("host addendum must not contain an H1 heading");
  }
  if (structure.kind === "addendum" && h2.length !== 1) {
    errors.push("host addendum must contain exactly one H2 section");
  }
  if (structure.kind === "full" && h2.length === 0) {
    errors.push("full document must contain at least one H2 section");
  }
  const lines = source.split(/\r?\n/u);
  for (const heading of h2) {
    if (!hasSectionContent(lines, heading, headings)) {
      errors.push(`H2 section ${heading.title || "(untitled)"} is empty`);
    }
  }
  return errors;
};
