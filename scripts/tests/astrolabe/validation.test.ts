import { expect, test } from "bun:test";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import path from "node:path";

import { repositoryPaths } from "../../test-support/paths.js";

const pluginRoot = repositoryPaths.astrolabeRoot;
const repositoryRoot = path.resolve(pluginRoot, "../..");
const pluginFileCache = new Map<string, string>();
const readPluginFile = (relativePath: string): string => {
  const cached = pluginFileCache.get(relativePath);
  if (cached !== undefined) {
    return cached;
  }
  const contents = readFileSync(path.join(pluginRoot, relativePath), "utf-8");
  pluginFileCache.set(relativePath, contents);
  return contents;
};
const collectFiles = (directory: string): string[] =>
  readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    return entry.isDirectory() ? collectFiles(entryPath) : [entryPath];
  });
const normalizeRelativePath = (filePath: string): string =>
  filePath.split(path.sep).join("/");
const packageAssetPaths = collectFiles(pluginRoot)
  .map((filePath) => normalizeRelativePath(path.relative(pluginRoot, filePath)))
  .toSorted();
const expectedAssetPaths = [
  ".claude-plugin/plugin.json",
  ".codex-plugin/plugin.json",
  "ATTRIBUTION.txt",
  "LICENSE",
  "README.md",
  "hooks/astrolabe-hook.mjs",
  "hooks/hooks.json",
  "skills/authoring-code/SKILL.md",
  "skills/authoring-code/references/language-rules.md",
  "skills/authoring-instructions/SKILL.md",
  "skills/authoring-instructions/references/portable-artifacts.md",
  "skills/building-linter-adapters/SKILL.md",
  "skills/building-linter-adapters/references/linter-conventions.md",
  "skills/executing-delegated-work/SKILL.md",
  "skills/orchestrating-work/SKILL.md",
  "skills/orchestrating-work/references/context-graph.md",
  "skills/orchestrating-work/references/recovering-work.md",
  "skills/using-workflow-tools/SKILL.md",
  "skills/using-workflow-tools/references/tool-selection.md",
  "skills/validating-observability-tools/SKILL.md",
  "skills/validating-observability-tools/references/observability-facts.md"
] as const;
const canonicalSkillRoots = expectedAssetPaths
  .filter((relativePath) => relativePath.endsWith("/SKILL.md"))
  .map((relativePath) => relativePath.slice(0, -"/SKILL.md".length));
interface SkillDocument {
  readonly body: string;
  readonly frontmatter: Readonly<Record<string, string>>;
}
interface SkillSemanticContract {
  readonly requiredPhrases: readonly string[];
  readonly requiredReferences: readonly string[];
}
const parseSkillDocument = (relativePath: string): SkillDocument => {
  const source = readPluginFile(relativePath);
  const match = source.match(
    /^---\n(?<frontmatter>[\s\S]*?)\n---\n(?<body>[\s\S]+)$/u
  );
  if (!match?.groups) {
    throw new Error(`${relativePath} must start with YAML frontmatter`);
  }
  const entries = match.groups.frontmatter.split("\n").map((line) => {
    const separator = line.indexOf(":");
    if (separator < 1 || line.slice(separator + 1).trim().length === 0) {
      throw new Error(`${relativePath} contains invalid frontmatter`);
    }
    return [
      line.slice(0, separator),
      line.slice(separator + 1).trim()
    ] as const;
  });
  const keys = entries.map(([key]) => key);
  if (new Set(keys).size !== keys.length) {
    throw new Error(`${relativePath} contains duplicate frontmatter fields`);
  }
  return {
    body: match.groups.body,
    frontmatter: Object.fromEntries(entries)
  };
};
const skillSemanticContracts: Readonly<
  Record<(typeof canonicalSkillRoots)[number], SkillSemanticContract>
> = {
  "skills/authoring-code": {
    requiredPhrases: [
      "native tool",
      "concrete types",
      "Apply these rules to the package or plugin being authored, including its source, manifests, hooks, skills, references, and documentation.",
      "prose-only"
    ],
    requiredReferences: ["language-rules.md"]
  },
  "skills/authoring-instructions": {
    requiredPhrases: [
      "canonical owner",
      "progressive disclosure",
      "Resolve contradictions",
      "package-relative paths"
    ],
    requiredReferences: []
  },
  "skills/building-linter-adapters": {
    requiredPhrases: [
      "ecosystem linter and formatter",
      "native exit semantics",
      "Expose only controls the rule actually supports",
      "Mirror the shipped manifest selectors",
      "Implement a fixer only when the native API supports a correct fix"
    ],
    requiredReferences: ["linter-conventions.md"]
  },
  "skills/executing-delegated-work": {
    requiredPhrases: [
      "Apply this skill to read-only Explore, research, analysis, implementation, and validation assignments.",
      "self-contained",
      "Goal",
      "Scope",
      "AcceptanceCriteria",
      "RequiredEvidence",
      "AuthorityBoundary",
      "If mutation is assigned",
      "Do not delegate further",
      "Do not run `git commit`",
      "BLOCKED",
      "Status",
      "Files",
      "Signatures",
      "Breaking*",
      "Decisions",
      "Summary",
      "EvidenceRefs",
      "Blockers",
      "FindingsOrDispositions"
    ],
    requiredReferences: []
  },
  "skills/orchestrating-work": {
    requiredPhrases: [
      "Identify dependencies",
      "one owner",
      "mutable resource",
      "Do not dispatch overlapping writers",
      "Status",
      "EvidenceRefs"
    ],
    requiredReferences: ["context-graph.md", "recovering-work.md"]
  },
  "skills/using-workflow-tools": {
    requiredPhrases: [
      "exact text search",
      "bounded structural navigation",
      "output-reduction"
    ],
    requiredReferences: ["tool-selection.md"]
  },
  "skills/validating-observability-tools": {
    requiredPhrases: [
      "checked-in facts",
      "authoritative source",
      "JSON manifests",
      "prohibited runtime behavior"
    ],
    requiredReferences: ["observability-facts.md"]
  }
};
const exactRootIntegrationSnippets = {
  ".claude-plugin/marketplace.json":
    '"name": "astrolabe",\n      "source": "./plugins/astrolabe"',
  "README.md": "- [astrolabe](./plugins/astrolabe/README.md)",
  "package.json": '"check:astrolabe": "bun test scripts/tests/astrolabe"',
  "scripts/test-support/paths.ts":
    'const astrolabeRoot = path.join(repositoryRoot, "plugins/astrolabe");'
} as const;
const forbiddenNames = ["local-instructions", "lodestar"];
const forbiddenPackageTerms = [
  "source-disposition",
  "MEMORY.md",
  "runtime Memory",
  "global memory"
];
const forbiddenModelTerms = [
  "fable",
  "opus",
  "sonnet",
  "haiku",
  "gpt",
  "codex-cli",
  "0.146.0"
];
const forbiddenLifecyclePattern = /\b(?<lifecycle>resume|fork)\b/iu;
const prohibitedActivityNameTokens = [
  "final",
  "round",
  "wave",
  "converged",
  "last"
] as const;
interface ProseBlock {
  readonly block: string;
  readonly relativePath: string;
}
interface ComparableProseBlock extends ProseBlock {
  readonly normalized: string;
  readonly wordCount: number;
  readonly tokens: ReadonlySet<string>;
}
interface FenceState {
  readonly marker: "`" | "~";
  readonly length: number;
}
const normalizeProseBlock = (block: string): string =>
  block.replaceAll(/\s+/gu, " ").trim().toLowerCase();
const isAtxHeading = (line: string): boolean =>
  /^\s{0,3}#{1,6}(?:\s+|$)/u.test(line);
const isFieldListLine = (line: string): boolean =>
  /^\s*:[A-Za-z][A-Za-z0-9_-]*(?:\s+[^:\n]+)?:\s*.*$/u.test(line);
const isOneLineIdentifierOrLabel = (block: string): boolean =>
  /^\s*(?:[-*+]\s+)?`?[A-Za-z][A-Za-z0-9_-]*`?\s*(?::[^\n]*)?$/u.test(block);
const isMeaningfulProseBlock = (block: string): boolean => {
  const words = block.match(/[a-z0-9]+(?:['-][a-z0-9]+)*/giu) ?? [];
  return words.length >= 5 && !isOneLineIdentifierOrLabel(block);
};
const stripLeadingYamlFrontmatter = (source: string): string =>
  source.replace(/^---\r?\n[\s\S]*?\r?\n---(?:\r?\n|$)/u, "");
const extractProseBlocks = (
  source: string,
  relativePath: string
): readonly ProseBlock[] => {
  const blocks: string[] = [];
  let currentLines: string[] = [];
  let fence: FenceState | undefined;
  const flushParagraph = (): void => {
    if (currentLines.length === 0) {
      return;
    }
    const block = currentLines.join(" ").trim();
    if (isMeaningfulProseBlock(block)) {
      blocks.push(normalizeProseBlock(block));
    }
    currentLines = [];
  };
  for (const line of stripLeadingYamlFrontmatter(source).split(/\r?\n/u)) {
    const closingFence = line.match(/^\s{0,3}(?<marker>`{3,}|~{3,})\s*$/u)
      ?.groups?.marker;
    if (fence !== undefined) {
      if (
        closingFence !== undefined &&
        closingFence[0] === fence.marker &&
        closingFence.length >= fence.length
      ) {
        fence = undefined;
      }
      continue;
    }
    const openingFence = line.match(/^\s{0,3}(?<marker>`{3,}|~{3,})(?:.*)$/u)
      ?.groups?.marker;
    if (openingFence !== undefined) {
      flushParagraph();
      fence = {
        length: openingFence.length,
        marker: openingFence[0] === "`" ? "`" : "~"
      };
      continue;
    }
    if (
      line.trim().length === 0 ||
      isAtxHeading(line) ||
      /^(?: {4}|\t)/u.test(line) ||
      isFieldListLine(line)
    ) {
      flushParagraph();
      continue;
    }
    currentLines.push(line.trim());
  }
  flushParagraph();
  return blocks.map((block) => ({ block, relativePath }));
};
const proseBlocks = (): readonly ProseBlock[] =>
  packageAssetPaths
    .filter((relativePath) => relativePath.endsWith(".md"))
    .flatMap((relativePath) =>
      extractProseBlocks(readPluginFile(relativePath), relativePath)
    );
const contentTokens = (block: string): ReadonlySet<string> =>
  new Set(block.toLowerCase().match(/[a-z0-9]+/gu));
const contentSimilarity = (
  leftTokens: ReadonlySet<string>,
  rightTokens: ReadonlySet<string>
): number => {
  const smallerSize = Math.min(leftTokens.size, rightTokens.size);
  const sharedSize = [...leftTokens].filter((token) =>
    rightTokens.has(token)
  ).length;
  return smallerSize === 0 ? 0 : sharedSize / smallerSize;
};
const comparableProseBlock = ({
  block,
  relativePath
}: ProseBlock): ComparableProseBlock => ({
  block,
  normalized: normalizeProseBlock(block),
  relativePath,
  tokens: contentTokens(block),
  wordCount: block.match(/[a-z0-9]+(?:['-][a-z0-9]+)*/giu)?.length ?? 0
});
const findDuplicateBlocks = (
  blocks: readonly ProseBlock[]
): readonly string[] => {
  const comparableBlocks = blocks.map(comparableProseBlock);
  const duplicateBlocks = new Set<string>();
  for (const [leftIndex, left] of comparableBlocks.entries()) {
    for (const right of comparableBlocks.slice(leftIndex + 1)) {
      if (left.relativePath === right.relativePath) {
        continue;
      }
      const exactMatch = left.normalized === right.normalized;
      const fuzzyMatch =
        left.wordCount >= 35 &&
        right.wordCount >= 35 &&
        contentSimilarity(left.tokens, right.tokens) >= 0.92;
      if (exactMatch || fuzzyMatch) {
        duplicateBlocks.add(`${left.relativePath} <> ${right.relativePath}`);
      }
    }
  }
  return [...duplicateBlocks].toSorted();
};

test("package assets exactly match the final shared skill inventory", () => {
  expect(packageAssetPaths).toEqual([...expectedAssetPaths].toSorted());
  for (const relativePath of packageAssetPaths) {
    expect(readPluginFile(relativePath)).not.toHaveLength(0);
  }
  expect(packageAssetPaths).not.toContain("references");
  expect(
    packageAssetPaths.some((filePath) => filePath.startsWith("codex/skills/"))
  ).toBe(false);
  const skillRootPaths = packageAssetPaths
    .filter((relativePath) => relativePath.endsWith("/SKILL.md"))
    .map((relativePath) => relativePath.slice(0, -"/SKILL.md".length))
    .toSorted();
  expect(skillRootPaths).toEqual([...canonicalSkillRoots].toSorted());
  for (const skillRoot of canonicalSkillRoots) {
    const relativePath = `${skillRoot}/SKILL.md`;
    const { body, frontmatter } = parseSkillDocument(relativePath);
    expect(Object.keys(frontmatter).toSorted()).toEqual([
      "description",
      "name"
    ]);
    expect(frontmatter.name).toBe(skillRoot.slice("skills/".length));
    expect(frontmatter.description.trim()).not.toHaveLength(0);
    expect(body.trim()).not.toHaveLength(0);
    expect(body.split(/\s+/u).length).toBeGreaterThan(100);
    expect(
      body.match(/^#{1,6}\s+\S.*$/gmu)?.length ?? 0
    ).toBeGreaterThanOrEqual(4);
    const contract = skillSemanticContracts[skillRoot];
    for (const phrase of contract.requiredPhrases) {
      expect(body).toContain(phrase);
    }
    for (const referenceName of contract.requiredReferences) {
      expect(body).toMatch(
        new RegExp(`\\]\\(references/${referenceName}(?:#[^)]+)?\\)`, "u")
      );
    }
  }
});

test("references stay inside their owning skills", () => {
  const referencePaths = packageAssetPaths.filter((relativePath) =>
    relativePath.includes("/references/")
  );
  const linkedReferencePaths = new Set<string>();
  expect(referencePaths.length).toBeGreaterThan(0);
  for (const relativePath of referencePaths) {
    const [skillRoot] = relativePath.split("/references/");
    expect(skillRoot).toMatch(/^skills\//u);
    expect(existsSync(path.join(pluginRoot, `${skillRoot}/SKILL.md`))).toBe(
      true
    );
  }
  for (const skillRoot of canonicalSkillRoots) {
    const skillText = readPluginFile(`${skillRoot}/SKILL.md`);
    for (const match of skillText.matchAll(
      /\[[^\]]+\]\((?<target>references\/[^)#]+)(?:#[^)]+)?\)/gu
    )) {
      const target = match.groups?.target;
      if (target === undefined) {
        continue;
      }
      const relativePath = `${skillRoot}/${target}`;
      expect(referencePaths).toContain(relativePath);
      expect(existsSync(path.join(pluginRoot, relativePath))).toBe(true);
      linkedReferencePaths.add(relativePath);
    }
  }
  expect([...linkedReferencePaths].toSorted()).toEqual(
    [...referencePaths].toSorted()
  );
});

test("Codex declares the shared skill tree and no lifecycle hooks", () => {
  const codexManifest = JSON.parse(
    readPluginFile(".codex-plugin/plugin.json")
  ) as Record<string, unknown>;
  expect(codexManifest).toEqual({
    author: { name: "ririnto" },
    description:
      "Portable engineering guidance for delegation, instruction authoring, code conventions, linter adapters, workflow tools, and observability validation.",
    homepage: "https://github.com/ririnto/sinon/tree/main/plugins/astrolabe",
    hooks: {},
    keywords: ["codex", "engineering", "instructions", "skills"],
    license: "Apache-2.0",
    name: "astrolabe",
    repository: "https://github.com/ririnto/sinon",
    skills: "./skills/"
  });
  expect(codexManifest).not.toHaveProperty("version");
  expect(readPluginFile("README.md")).toContain(
    "Both runtimes use the canonical shared skill tree under `skills/`."
  );
});

test("manifests and exact root integration snippets use Astrolabe", () => {
  const pluginManifest = JSON.parse(
    readPluginFile(".claude-plugin/plugin.json")
  ) as Record<string, unknown>;
  const codexManifest = JSON.parse(
    readPluginFile(".codex-plugin/plugin.json")
  ) as Record<string, unknown>;
  const marketplace = JSON.parse(
    readFileSync(
      path.join(repositoryRoot, ".claude-plugin/marketplace.json"),
      "utf-8"
    )
  ) as {
    plugins: Record<string, unknown>[];
  };
  expect(pluginManifest.name).toBe("astrolabe");
  expect(codexManifest.name).toBe("astrolabe");
  expect(pluginManifest).not.toHaveProperty("version");
  expect(codexManifest).not.toHaveProperty("version");
  expect(marketplace.plugins).toContainEqual({
    author: { name: "ririnto" },
    category: "development",
    description:
      "Portable engineering guidance for delegation, instruction authoring, code conventions, linter adapters, workflow tools, and observability validation.",
    license: "Apache-2.0",
    name: "astrolabe",
    source: "./plugins/astrolabe"
  });
  expect(
    marketplace.plugins.every((plugin) => !Object.hasOwn(plugin, "version"))
  ).toBe(true);
  for (const [relativePath, snippet] of Object.entries(
    exactRootIntegrationSnippets
  )) {
    expect(
      readFileSync(path.join(repositoryRoot, relativePath), "utf-8")
    ).toContain(snippet);
  }
});

test("Astrolabe package content excludes forbidden terms and lifecycle language", () => {
  const packageContent = packageAssetPaths
    .filter((relativePath) => relativePath !== "LICENSE")
    .map((relativePath) => readPluginFile(relativePath))
    .join("\n");
  const integrationContent = Object.values(exactRootIntegrationSnippets).join(
    "\n"
  );
  for (const term of [
    ...forbiddenNames,
    ...forbiddenPackageTerms,
    ...forbiddenModelTerms
  ]) {
    expect(
      `${packageContent}\n${integrationContent}`.toLowerCase()
    ).not.toContain(term.toLowerCase());
  }
  expect(packageContent).not.toMatch(forbiddenLifecyclePattern);
});

test("activity name examples reject prohibited tokens only within extracted names", () => {
  const source = readPluginFile("skills/orchestrating-work/SKILL.md");
  const match = source.match(
    /Give each activity a concrete name such as (?<names>[^.]+)\./u
  );
  const names = [
    ...(match?.groups?.names ?? "").matchAll(/`(?<name>[^`]+)`/gu)
  ].flatMap(({ groups }) => (groups?.name === undefined ? [] : [groups.name]));
  expect(names).toEqual([
    "inspect-hook-contracts",
    "implement-lifecycle-recovery",
    "verify-completion-checks",
    "reconcile-findings"
  ]);
  for (const name of names) {
    for (const token of prohibitedActivityNameTokens) {
      expect(name.split("-")).not.toContain(token);
    }
  }
  expect(source).toContain("final validation");
});

test("executing worker skill keeps its mode and terminal result contract", () => {
  const source = readPluginFile("skills/executing-delegated-work/SKILL.md");
  expect(source).toContain(
    "Apply this skill to read-only Explore, research, analysis, implementation, and validation assignments."
  );
  expect(source).toContain("If mutation is assigned");
  expect(source).toContain("Do not delegate further");
  expect(source).toContain("Do not run `git commit`");
  for (const field of [
    "Status",
    "Files",
    "Signatures",
    "Breaking*",
    "Decisions",
    "Summary",
    "EvidenceRefs",
    "Blockers",
    "FindingsOrDispositions"
  ]) {
    expect(source).toContain(field);
  }
});

test("duplicate extraction compares wrapped short prose and ignores fragments", () => {
  const wrappedSource = [
    "Workers report evidence before releasing",
    "owned resources safely."
  ].join("\n");
  const blocks = [
    ...extractProseBlocks(wrappedSource, "synthetic-a.md"),
    ...extractProseBlocks(
      "Workers report evidence before releasing owned resources safely.",
      "synthetic-b.md"
    ),
    ...extractProseBlocks("One two three four.", "generic.md"),
    ...extractProseBlocks("Label:", "label.md")
  ];
  expect(findDuplicateBlocks(blocks)).toEqual([
    "synthetic-a.md <> synthetic-b.md"
  ]);
});

test("duplicate extraction ignores Markdown structure and metadata", () => {
  const ignoredSource = [
    "---",
    "name: repeated",
    "description: repeated prose ignored",
    "---",
    "# Repeated heading prose ignored",
    "```ts",
    "Repeated fenced code prose ignored entirely.",
    "```",
    "~~~text",
    "Repeated tilde fenced prose ignored entirely.",
    "~~~",
    "    Repeated indented code prose ignored entirely.",
    ":param name: Repeated field-list prose ignored.",
    ":returns: Repeated field-list prose ignored.",
    ":raises Type: Repeated field-list prose ignored.",
    "`identifier`",
    "- label",
    ""
  ].join("\n");
  const blocks = extractProseBlocks(ignoredSource, "ignored-a.md");
  expect(blocks).toEqual([]);
  expect(
    findDuplicateBlocks([
      ...blocks,
      ...extractProseBlocks(ignoredSource, "ignored-b.md")
    ])
  ).toEqual([]);
});

test("duplicate extraction keeps long near-identical prose on fuzzy comparison", () => {
  const left = [
    "Workers inspect the assigned source and preserve ownership boundaries before making any change.",
    "They run required checks against final inputs and report exact evidence for every decision made.",
    "They release owned resources only after the required result fields and checks are complete."
  ].join(" ");
  const right = [
    "Workers inspect the assigned source and preserve ownership boundaries before making any change.",
    "They run required checks against final inputs and report exact evidence for each decision made.",
    "They release owned resources only after the required result fields and checks are complete."
  ].join(" ");
  expect(
    findDuplicateBlocks([
      { block: left, relativePath: "fuzzy-a.md" },
      { block: right, relativePath: "fuzzy-b.md" }
    ])
  ).toEqual(["fuzzy-a.md <> fuzzy-b.md"]);
});

test("duplicate extraction does not report same-file repeats", () => {
  const repeatedBlock =
    "Workers report evidence before releasing owned resources safely.";
  expect(
    findDuplicateBlocks([
      { block: repeatedBlock, relativePath: "same.md" },
      { block: repeatedBlock, relativePath: "same.md" }
    ])
  ).toEqual([]);
});

test("Astrolabe package follows its own source conventions", () => {
  const hookSource = readPluginFile("hooks/astrolabe-hook.mjs");
  expect(
    hookSource.startsWith("#!/usr/bin/env node\n// -*- coding: utf-8 -*-\n")
  ).toBe(true);
  expect(hookSource).not.toMatch(/\btry\b|\.catch\s*\(/u);
  const markdownSources = packageAssetPaths
    .filter((relativePath) => relativePath.endsWith(".md"))
    .map((relativePath) => readPluginFile(relativePath));
  for (const markdownSource of markdownSources) {
    expect(markdownSource).not.toContain("```bash");
    expect(markdownSource).not.toContain("```python");
  }
  const shellBlocks = [
    ...readPluginFile("README.md").matchAll(/```sh\n(?<body>[\s\S]*?)```/gu)
  ].map((match) => match[1]);
  expect(shellBlocks.length).toBeGreaterThan(0);
  for (const shellBlock of shellBlocks) {
    expect(shellBlock).toContain("#!/usr/bin/env sh\n");
    expect(shellBlock).toContain("# -*- coding: utf-8 -*-\n");
  }
  const requiredCheckBlock = shellBlocks.find((block) =>
    block.includes("bun test scripts/tests/astrolabe")
  );
  expect(requiredCheckBlock).toContain("set -e\n");
  const optionalValidatorBlock = shellBlocks.find((block) =>
    block.includes("claude plugin validate plugins/astrolabe")
  );
  expect(optionalValidatorBlock).toContain("command -v claude >/dev/null 2>&1");
  expect(optionalValidatorBlock).not.toContain("set -e\n");
});

test("prose blocks have one canonical owner", () => {
  expect(findDuplicateBlocks(proseBlocks())).toEqual([]);
});
