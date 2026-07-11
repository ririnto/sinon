// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import {
  parseFencedCodeBlocks,
  parseMarkdownHeadings
} from "../plugins/harness/scripts/harness-checks/markdown-structure.js";
import { parseWorkflowPolicyDocument } from "./workflow-policy-document.js";

const root = path.resolve(import.meta.dirname, "..");

const readRepositoryFile = (filePath: string): Promise<string> =>
  Bun.file(path.join(root, filePath)).text();

const shellCode = (source: string): string =>
  parseFencedCodeBlocks(source)
    .filter((block) => block.language === "sh")
    .map((block) => block.content)
    .join("\n");

test("host-specific workflow references keep host executables isolated", async () => {
  // Given
  const skillPath =
    "plugins/workspace-workflow/skills/pr-mr-convention/SKILL.md";
  const githubPath = `${path.dirname(skillPath)}/references/github.md`;
  const gitlabPath = `${path.dirname(skillPath)}/references/gitlab.md`;
  const [skill, githubReference, gitlabReference] = await Promise.all([
    readRepositoryFile(skillPath),
    readRepositoryFile(githubPath),
    readRepositoryFile(gitlabPath)
  ]);

  // When
  const skillHeadings = parseMarkdownHeadings(skill);
  const skillCommands = shellCode(skill);
  const githubCommands = shellCode(githubReference);
  const gitlabCommands = shellCode(gitlabReference);

  // Then
  expect(skillHeadings.filter((heading) => heading.level === 1)).toHaveLength(
    1
  );
  expect(
    skillHeadings.filter((heading) => heading.level === 2).length
  ).toBeGreaterThan(0);
  expect(skill).toMatch(/`references\/github\.md`/u);
  expect(skill).toMatch(/`references\/gitlab\.md`/u);
  expect(skillCommands).not.toMatch(/\b(?:gh|glab)\b/u);
  expect(githubCommands).toMatch(/^gh\s/mu);
  expect(githubCommands).not.toMatch(/^glab\s/mu);
  expect(gitlabCommands).toMatch(/^glab\s/mu);
  expect(gitlabCommands).not.toMatch(/^gh\s/mu);
});

test("the SDD agent exposes a leaf-only Claude tool boundary", async () => {
  // Given
  const agent = await readRepositoryFile(
    "plugins/spec-driven-development/agents/spec-driven-development.md"
  );
  const frontmatter = /^---\n(?<content>[\s\S]+?)\n---/u.exec(agent)?.groups?.[
    "content"
  ];

  // When
  const tools = [
    ...(frontmatter ?? "").matchAll(/^ {2}- (?<tool>\w+)$/gmu)
  ].flatMap((match) => {
    const tool = match.groups?.["tool"];
    return tool === undefined ? [] : [tool];
  });

  // Then
  expect(tools).toEqual(["Read", "Glob", "Grep", "Write", "Edit", "Bash"]);
  expect(tools).not.toContain("Agent");
});

test("workflow policy keeps orchestration at the root and excludes packaged orchestrators", async () => {
  // Given
  const workflow = await readRepositoryFile(
    "plugins/harness/skills/harness-install/assets/common/WORKFLOW.md"
  );

  // When
  const decision = parseWorkflowPolicyDocument(workflow);
  const installedClaudeOrchestrator = Bun.file(
    path.join(
      root,
      "plugins/agent-capability-kit/agents/project-orchestrator.md"
    )
  );
  const installedCodexOrchestrator = Bun.file(
    path.join(root, ".codex/agents/project-orchestrator.toml")
  );

  // Then
  expect(decision.blockers).toEqual([]);
  expect(decision.value?.root.terra.delegates).toEqual([
    "exploration",
    "implementation",
    "validation",
    "review"
  ]);
  expect(await installedClaudeOrchestrator.exists()).toBe(false);
  expect(await installedCodexOrchestrator.exists()).toBe(false);
});

test("Claude leaf policy cites nested-subagent support without granting delegation", async () => {
  // Given
  const reference = await readRepositoryFile(
    "plugins/agent-capability-kit/skills/agent-authoring/references/agent-execution.md"
  );

  // When
  const headings = parseMarkdownHeadings(reference);

  // Then
  expect(headings.filter((heading) => heading.level === 1)).toHaveLength(1);
  expect(
    headings.filter((heading) => heading.level === 2).length
  ).toBeGreaterThan(0);
  expect(reference).toContain(
    "https://code.claude.com/docs/en/sub-agents.md#spawn-nested-subagents"
  );
});

test("new Codex spawn examples isolate parent turns", async () => {
  // Given
  const reference = await readRepositoryFile(
    "plugins/agent-capability-kit/skills/agent-authoring/references/agent-execution.md"
  );

  // When
  const spawnExamples = parseFencedCodeBlocks(reference).filter(
    (block) =>
      block.language === "ts" && /\bspawn_agent\s*\(/u.test(block.content)
  );

  // Then
  expect(spawnExamples).toHaveLength(1);
  expect(spawnExamples[0]?.content).toMatch(/\bfork_turns:\s*"none"/u);
  expect(spawnExamples[0]?.content).not.toMatch(/\bfork_turns:\s*"(?!none")/u);
});
