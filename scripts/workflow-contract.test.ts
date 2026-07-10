// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import {
  evaluateCompletion,
  evaluateFanIn,
  referenceForHost,
  selectImplementationAgent,
  selectReviewHost
} from "./workflow-contract.js";

test("non-overlapping writers can complete fan-in", () => {
  const result = evaluateFanIn([
    { files: ["src/a.ts"], id: "a", mode: "writer", status: "passed" },
    { files: ["src/b.ts"], id: "b", mode: "writer", status: "passed" }
  ]);
  expect(result).toEqual({ blockers: [], value: "complete" });
});

test("overlapping writers must be serialized", () => {
  const result = evaluateFanIn([
    { files: ["src/shared.ts"], id: "a", mode: "writer", status: "passed" },
    { files: ["src/shared.ts"], id: "b", mode: "writer", status: "passed" }
  ]);
  expect(result.blockers[0]).toContain("overlapping writer ownership");
  const ancestor = evaluateFanIn([
    { files: ["src/"], id: "directory", mode: "writer", status: "passed" },
    { files: ["./src/a.ts"], id: "file", mode: "writer", status: "passed" }
  ]);
  expect(ancestor.blockers[0]).toContain("overlapping writer ownership");
});

test("writers require an explicit ownership scope", () => {
  const result = evaluateFanIn([
    { files: [], id: "writer", mode: "writer", status: "passed" }
  ]);
  expect(result.blockers[0]).toContain("ownership scope is missing");
});

test("failed and missing workers block full fan-in", () => {
  const result = evaluateFanIn([
    { files: [], id: "failed", mode: "read-only", status: "failed" },
    { files: [], id: "missing", mode: "read-only", status: "missing" }
  ]);
  expect(result.blockers).toHaveLength(2);
  expect(result.value).toBeUndefined();
});

test("scoped implementation requires an exhaustive small file set", () => {
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: true,
      fileCount: 2,
      needsAffectedSetDiscovery: false
    }).value
  ).toBe("scoped-implementer");
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: false,
      fileCount: 1,
      needsAffectedSetDiscovery: false
    }).blockers[0]
  ).toContain("exploration or planning");
});

test("general implementation owns discovery and cross-boundary work", () => {
  expect(
    selectImplementationAgent({
      architectureDecision: true,
      crossesBoundary: true,
      exhaustiveFileSet: true,
      fileCount: 2,
      needsAffectedSetDiscovery: true
    }).value
  ).toBe("implementation");
  expect(
    selectImplementationAgent({
      architectureDecision: false,
      crossesBoundary: false,
      exhaustiveFileSet: false,
      fileCount: 0,
      needsAffectedSetDiscovery: true
    }).value
  ).toBe("implementation");
});

test("ambiguous GitHub and GitLab remotes block host routing", () => {
  const result = selectReviewHost({
    remoteCandidates: ["github", "gitlab"]
  });
  expect(result.value).toBeUndefined();
  expect(result.blockers[0]).toContain("focused host choice");
});

test("explicit host choice wins and loads one reference", () => {
  const result = selectReviewHost({
    explicit: "gitlab",
    remoteCandidates: ["github", "gitlab"]
  });
  expect(result).toEqual({ blockers: [], value: "gitlab" });
  expect(referenceForHost(result.value ?? "local")).toBe(
    "references/gitlab.md"
  );
  expect(referenceForHost("local")).toBeUndefined();
});

test("owner fixes, re-review, integrated validation, and authority gate publication", () => {
  const blocked = evaluateCompletion({
    integratedValidation: "missing",
    openFindings: 1,
    ownerFix: "missing",
    publicationAuthorized: false,
    rereview: "missing",
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(blocked.blockers).toHaveLength(5);
  const ready = evaluateCompletion({
    integratedValidation: "passed",
    openFindings: 0,
    ownerFix: "passed",
    publicationAuthorized: true,
    rereview: "passed",
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(ready).toEqual({ blockers: [], value: "publish" });
  const missingRereview = evaluateCompletion({
    integratedValidation: "passed",
    openFindings: 0,
    ownerFix: "passed",
    publicationAuthorized: true,
    rereview: "not-needed",
    workers: [
      { files: ["src/a.ts"], id: "writer", mode: "writer", status: "passed" }
    ]
  });
  expect(missingRereview.blockers).toContain(
    "owning-writer fixes require a passed re-review"
  );
});

test("workflow documents carry the complete lifecycle and selected-host split", async () => {
  const root = path.resolve(import.meta.dirname, "..");
  const common = path.join(
    root,
    "plugins/harness/skills/harness-install/assets/common"
  );
  const workflowTexts = await Promise.all(
    [
      "WORKFLOW.md",
      "WORKFLOW.github.md",
      "WORKFLOW.gitlab.md",
      "WORKFLOW.none.md"
    ].map((name) => Bun.file(path.join(common, name)).text())
  );
  for (const text of workflowTexts) {
    for (const fragment of [
      "max_depth = 1",
      "scoped-implementer",
      "implementation",
      "Fan in",
      "owning writer",
      "Re-review",
      "integrated validation"
    ]) {
      expect(text).toContain(fragment);
    }
  }
  const skill = await Bun.file(
    path.join(
      root,
      "plugins/workspace-workflow/skills/pr-mr-convention/SKILL.md"
    )
  ).text();
  expect(skill).toContain("references/github.md");
  expect(skill).toContain("references/gitlab.md");
  expect(skill).toContain("Do not run both host branches speculatively");
  expect(skill).not.toMatch(/^.*\bgh\s+/mu);
  expect(skill).not.toMatch(/^.*\bglab\s+/mu);
  expect(
    await Bun.file(
      path.join(
        root,
        "plugins/workspace-workflow/skills/pr-mr-convention/references/github.md"
      )
    ).exists()
  ).toBe(true);
  expect(
    await Bun.file(
      path.join(
        root,
        "plugins/workspace-workflow/skills/pr-mr-convention/references/gitlab.md"
      )
    ).exists()
  ).toBe(true);
  const githubReference = await Bun.file(
    path.join(
      root,
      "plugins/workspace-workflow/skills/pr-mr-convention/references/github.md"
    )
  ).text();
  const gitlabReference = await Bun.file(
    path.join(
      root,
      "plugins/workspace-workflow/skills/pr-mr-convention/references/gitlab.md"
    )
  ).text();
  expect(githubReference).toMatch(/^.*\bgh\s+/mu);
  expect(githubReference).not.toMatch(/^.*\bglab\s+/mu);
  expect(gitlabReference).toMatch(/^.*\bglab\s+/mu);
  expect(gitlabReference).not.toMatch(/^.*\bgh\s+/mu);
});

test("sequential SDD leaf returns nested work to the top-level session", async () => {
  const root = path.resolve(import.meta.dirname, "..");
  const agent = await Bun.file(
    path.join(
      root,
      "plugins/spec-driven-development/agents/spec-driven-development.md"
    )
  ).text();
  expect(agent).toContain("This agent is a sequential leaf workflow");
  expect(agent).toContain("Do not delegate or attempt nested subagents");
  expect(agent).toContain("return a decomposition handoff");
  expect(agent).not.toContain("Agent(");
});

test("orchestration remains top-level policy rather than an installable profile", async () => {
  const root = path.resolve(import.meta.dirname, "..");
  const rules = await Bun.file(path.join(root, "AGENTS.md")).text();
  expect(rules).toContain("sole general orchestrator");
  expect(rules).toContain("MUST NOT package a `project-orchestrator`");
  expect(
    await Bun.file(
      path.join(
        root,
        "plugins/agent-capability-kit/agents/project-orchestrator.md"
      )
    ).exists()
  ).toBe(false);
  expect(
    await Bun.file(
      path.join(root, ".codex/agents/project-orchestrator.toml")
    ).exists()
  ).toBe(false);
});

test("documentation treats Claude leaf behavior as policy, not a platform limit", async () => {
  const root = path.resolve(import.meta.dirname, "..");
  const files = ["AGENTS.md"];
  const glob = new Bun.Glob("plugins/**/*.md");
  for await (const filePath of glob.scan({ cwd: root, onlyFiles: true })) {
    files.push(filePath);
  }
  const texts = await Promise.all(
    files.map((filePath) => Bun.file(path.join(root, filePath)).text())
  );
  for (const text of texts) {
    expect(text).not.toMatch(
      /Claude(?: Code)? (?:subagents )?cannot create (?:nested )?subagents/iu
    );
  }
  const executionReference = await Bun.file(
    path.join(
      root,
      "plugins/agent-capability-kit/skills/agent-authoring/references/agent-execution.md"
    )
  ).text();
  expect(executionReference).toContain(
    "supports nested subagents up to depth 5"
  );
  expect(executionReference).toContain(
    "https://code.claude.com/docs/en/sub-agents.md#spawn-nested-subagents"
  );
  expect(executionReference).toContain("intentionally omits delegation tools");
});
