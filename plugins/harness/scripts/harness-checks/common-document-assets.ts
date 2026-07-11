// -*- coding: utf-8 -*-

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import {
  fail,
  rejectTextFragments,
  requireFile,
  requireTexts
} from "./check-support.js";
import { readClaudeAgentFrontmatter } from "./common-agent-assets.js";
import { markdownStructureErrors } from "./markdown-structure.js";

const hostTemplateFiles = [
  "docs/templates/github/README.md",
  "docs/templates/github/tasks/bug-report.md",
  "docs/templates/github/tasks/documentation.md",
  "docs/templates/github/tasks/feature-request.md",
  "docs/templates/github/tasks/improvement.md",
  "docs/templates/github/tasks/refactor.md",
  "docs/templates/github/tasks/task.md",
  "docs/templates/github/change-description.md",
  "docs/templates/gitlab/tasks/bug-report.md",
  "docs/templates/gitlab/tasks/documentation.md",
  "docs/templates/gitlab/tasks/enhancement.md",
  "docs/templates/gitlab/tasks/feature-request.md",
  "docs/templates/gitlab/tasks/refactor.md",
  "docs/templates/gitlab/tasks/task.md",
  "docs/templates/gitlab/change-description.md"
] as const;

const hostTemplateTexts = [
  [
    "docs/templates/github/tasks/bug-report.md",
    ["Acceptance criteria", "Validation plan", "Risks"]
  ],
  [
    "docs/templates/github/tasks/documentation.md",
    ["Documentation target", "Acceptance criteria", "Validation plan"]
  ],
  [
    "docs/templates/github/tasks/feature-request.md",
    ["Non-goals", "Acceptance criteria", "Validation plan"]
  ],
  [
    "docs/templates/github/tasks/improvement.md",
    ["Measurement", "Compatibility impact", "Validation plan"]
  ],
  [
    "docs/templates/github/tasks/refactor.md",
    ["Behavior preservation", "Interfaces and handoffs", "Rollback plan"]
  ],
  [
    "docs/templates/github/tasks/task.md",
    ["Completion criteria", "Validation method", "Risks"]
  ],
  [
    "docs/templates/github/change-description.md",
    ["## Validation", "## Unverified Items", "## Rollback"]
  ],
  [
    "docs/templates/gitlab/change-description.md",
    ["## Validation", "## Unverified Items", "## Rollback"]
  ],
  [
    "docs/templates/gitlab/tasks/documentation.md",
    ["## Documentation Target", "## Validation Plan", "## Risks"]
  ],
  [
    "docs/templates/gitlab/tasks/enhancement.md",
    ["## Measurement", "## Compatibility Impact", "## Risks"]
  ],
  [
    "docs/templates/gitlab/tasks/feature-request.md",
    ["## Non-goals", "## Validation Plan", "## Risks"]
  ],
  [
    "docs/templates/gitlab/tasks/refactor.md",
    ["## Behavior Preservation", "## Interfaces and Handoffs", "## Risks"]
  ],
  [
    "docs/templates/gitlab/tasks/task.md",
    ["## Completion Criteria", "## Validation Method", "## Risks"]
  ],
  [
    "docs/templates/docs/exec-plan.md",
    [
      "## Goal",
      "## Scope",
      "## Tasks",
      "## Verification",
      "Completed plans must not keep unchecked task lines"
    ]
  ]
] as const;

const targetAssetBannedFragments = [
  "Fresh installs",
  "fresh installs",
  "target-specific",
  "target repository",
  "installed target",
  "installed contract",
  "selected install mode",
  "selected stack asset package",
  "harness installation",
  "plugin checkout",
  "Optional Seed Files",
  "seed file",
  "replaceable seed files",
  "scaffold-token"
] as const;

const requireMarkdownStructure = (
  filePath: string,
  kind: "addendum" | "full"
): void => {
  requireFile(filePath);
  for (const error of markdownStructureErrors(readFileSync(filePath, "utf-8"), {
    kind
  })) {
    fail(`[markdown] ${error}: ${filePath}`);
  }
};

/** Validate packaged target documentation, templates, and workflow structure. */
export const checkCommonDocumentAssets = (common: string): void => {
  for (const filePath of [
    "AGENTS.md",
    "CLAUDE.md",
    "ARCHITECTURE.md",
    "WORKFLOW.md",
    "WORKFLOW.github.md",
    "WORKFLOW.gitlab.md",
    "WORKFLOW.none.md",
    ".mcp.json",
    ".editorconfig",
    ".markdownlint-cli2.jsonc",
    ".codegraph/.gitignore",
    ".claude/skills/autonomous-execution/SKILL.md",
    ".claude/skills/problem-analysis/SKILL.md",
    "docs/design-docs/repository-layout.md",
    "scripts/no-box-drawing.ts",
    "scripts/exec-plan-links.ts",
    "scripts/docs-root-files.ts"
  ]) {
    requireFile(path.join(common, filePath));
  }
  if (existsSync(path.join(common, "docs", "git-hooks"))) {
    fail("[common assets] docs/git-hooks must not exist");
  }
  for (const filePath of hostTemplateFiles) {
    requireFile(path.join(common, filePath));
  }
  requireTexts(
    hostTemplateTexts.map(([relativePath, fragments]) => ({
      fragments,
      path: path.join(common, relativePath)
    }))
  );
  requireTexts([
    {
      fragments: ["# CLAUDE.md", "@AGENTS.md"],
      path: path.join(common, "CLAUDE.md")
    },
    {
      fragments: [
        '"codegraph"',
        '"type": "stdio"',
        '"command": "codegraph"',
        '"serve"',
        '"--mcp"'
      ],
      path: path.join(common, ".mcp.json")
    },
    {
      fragments: [
        '"docs/no-box-drawing": true',
        '"docs/root-files": true',
        "./scripts/no-box-drawing.ts",
        "./scripts/docs-root-files.ts"
      ],
      path: path.join(common, ".markdownlint-cli2.jsonc")
    },
    {
      fragments: ["docs/no-box-drawing", "\\u2500-\\u257F"],
      path: path.join(common, "scripts", "no-box-drawing.ts")
    },
    {
      fragments: ["docs/root-files", "allowedDocsDirectories"],
      path: path.join(common, "scripts", "docs-root-files.ts")
    }
  ]);
  for (const filePath of [
    "ARCHITECTURE.md",
    "docs/templates/gitlab/tasks/enhancement.md",
    ".claude/skills/problem-analysis/SKILL.md",
    "docs/PLANS.md",
    "docs/SECURITY.md",
    "docs/design-docs/core-beliefs.md",
    "docs/design-docs/repository-layout.md",
    "docs/product-specs/new-user-onboarding.md",
    "docs/references/README.md",
    "docs/templates/agent/AGENT.md",
    "docs/templates/docs/AGENTS.md",
    "docs/templates/skill/SKILL.md",
    "docs/templates/docs/exec-plan.md",
    "docs/templates/docs/reference-llms.txt"
  ]) {
    rejectTextFragments(
      path.join(common, filePath),
      targetAssetBannedFragments
    );
  }
  requireMarkdownStructure(path.join(common, "WORKFLOW.md"), "full");
  for (const name of ["github", "gitlab", "none"] as const) {
    requireMarkdownStructure(
      path.join(common, `WORKFLOW.${name}.md`),
      "addendum"
    );
  }
  const autonomousSkill = path.join(
    common,
    ".claude",
    "skills",
    "autonomous-execution",
    "SKILL.md"
  );
  const autonomousFrontmatter = readClaudeAgentFrontmatter(autonomousSkill);
  if (
    autonomousFrontmatter["name"] !== "autonomous-execution" ||
    typeof autonomousFrontmatter["description"] !== "string" ||
    autonomousFrontmatter["description"] === ""
  ) {
    fail(`[autonomousExecution] invalid skill frontmatter: ${autonomousSkill}`);
  }
  requireMarkdownStructure(autonomousSkill, "full");
};
