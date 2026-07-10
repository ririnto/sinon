#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { spawnSync } from "node:child_process";
import {
  accessSync,
  constants,
  existsSync,
  lstatSync,
  readFileSync,
  statSync
} from "node:fs";
import path from "node:path";

import {
  manifestPathFor,
  readAssetManifest
} from "../skills/harness-install/scripts/asset-manifest.js";
import { checkInstallerRuntime } from "./harness-checks/installer-runtime.js";
import { checkPackageSurface } from "./harness-checks/package-surface.js";

type StackMode = "bun" | "gradle" | "maven" | "shell" | "uv";

type TextCheck = Readonly<{
  fragments: readonly string[];
  path: string;
}>;

type TargetAgentSpec = Readonly<{
  claudeDisallowedTools?: readonly string[];
  claudeEffort?: string;
  claudeModel?: string;
  claudeTools?: readonly string[];
  codexEffort?: string;
  codexModel?: string;
  fragments: readonly string[];
  name: string;
}>;

type ClaudeHook = Readonly<{
  async?: unknown;
  args?: unknown;
  command?: unknown;
  matcher?: unknown;
  type?: unknown;
}>;

type ClaudeHookGroup = Readonly<{
  hooks: readonly unknown[];
  matcher: "EnterWorktree";
}>;

class CheckFailureError extends Error {
  name = "CheckFailureError";
}

/**
 * Raise a self-check failure with a stable diagnostic prefix.
 *
 * @param message Failure message.
 */
const fail = (message: string): never => {
  throw new CheckFailureError(message);
};

/**
 * Check whether an unknown value is an object record.
 *
 * @param value Value to inspect.
 */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;

/**
 * Check whether an unknown value is a Claude hook group.
 *
 * @param value Value to inspect.
 */
const isClaudeHookGroup = (value: unknown): value is ClaudeHookGroup =>
  isRecord(value) &&
  value["matcher"] === "EnterWorktree" &&
  Array.isArray(value["hooks"]);

/**
 * Require one Claude hook group.
 *
 * @param value Value to inspect.
 * @param filePath Claude settings path.
 */
const requireClaudeHookGroup = (
  value: unknown,
  filePath: string
): ClaudeHookGroup => {
  if (isClaudeHookGroup(value)) {
    return value;
  }
  return fail(`${filePath}: PostToolUse matcher must target EnterWorktree`);
};

/**
 * Check whether an unknown value is a one-item array.
 *
 * @param value Value to inspect.
 */
const isSingleItemArray = (value: unknown): value is readonly [unknown] =>
  Array.isArray(value) && value.length === 1;

/**
 * Check whether an unknown value is an async command hook.
 *
 * @param value Value to inspect.
 */
const isAsyncCommandHook = (
  value: unknown
): value is ClaudeHook & { command: string } =>
  isRecord(value) &&
  value["async"] === true &&
  typeof value["command"] === "string" &&
  !("args" in value) &&
  !("matcher" in value) &&
  value["type"] === "command";

const stackModes = ["gradle", "maven", "bun", "uv", "shell"] as const;

const targetAgentSpecs: readonly TargetAgentSpec[] = [
  {
    claudeEffort: "medium",
    claudeModel: "sonnet",
    codexEffort: "medium",
    codexModel: "gpt-5.6-terra",
    fragments: [
      "complete affected set",
      "cross-file reasoning and integration validation",
      "`scoped-implementer`",
      "explore and plan"
    ],
    name: "implementation"
  },
  {
    claudeDisallowedTools: ["Agent"],
    claudeEffort: "low",
    claudeModel: "haiku",
    claudeTools: ["Read", "Edit", "Write", "Bash"],
    codexEffort: "low",
    codexModel: "gpt-5.6-luna",
    fragments: [
      "exhaustive list of every file",
      "desired behavior",
      "targeted validation commands",
      "Do not broaden",
      "Do not decide architecture",
      "Do not refactor adjacent code",
      "Do not delegate",
      "Do not integrate",
      "Do not commit, push, publish",
      "ownership lists are disjoint",
      "Sonnet/Terra `implementation` agent or the root orchestrator"
    ],
    name: "scoped-implementer"
  },
  {
    fragments: [],
    name: "review"
  }
];

const hostTemplateFiles = [
  ".github/ISSUE_TEMPLATE/config.yml",
  ".github/ISSUE_TEMPLATE/bug_report.yml",
  ".github/ISSUE_TEMPLATE/docs.yml",
  ".github/ISSUE_TEMPLATE/feature_request.yml",
  ".github/ISSUE_TEMPLATE/improvement.yml",
  ".github/ISSUE_TEMPLATE/refactor.yml",
  ".github/ISSUE_TEMPLATE/task.yml",
  ".github/pull_request_template.md",
  ".gitlab/issue_templates/Bug.md",
  ".gitlab/issue_templates/Docs.md",
  ".gitlab/issue_templates/Enhancement.md",
  ".gitlab/issue_templates/Feature.md",
  ".gitlab/issue_templates/Refactor.md",
  ".gitlab/issue_templates/Task.md",
  ".gitlab/merge_request_templates/Default.md"
] as const;

const hostTemplateTexts: readonly TextCheck[] = [
  {
    fragments: ["Acceptance criteria", "Validation plan", "Risks"],
    path: ".github/ISSUE_TEMPLATE/bug_report.yml"
  },
  {
    fragments: [
      "Documentation target",
      "Acceptance criteria",
      "Validation plan"
    ],
    path: ".github/ISSUE_TEMPLATE/docs.yml"
  },
  {
    fragments: ["Non-goals", "Acceptance criteria", "Validation plan"],
    path: ".github/ISSUE_TEMPLATE/feature_request.yml"
  },
  {
    fragments: ["Measurement", "Compatibility impact", "Validation plan"],
    path: ".github/ISSUE_TEMPLATE/improvement.yml"
  },
  {
    fragments: [
      "Behavior preservation",
      "Interfaces and handoffs",
      "Rollback plan"
    ],
    path: ".github/ISSUE_TEMPLATE/refactor.yml"
  },
  {
    fragments: ["Completion criteria", "Validation method", "Risks"],
    path: ".github/ISSUE_TEMPLATE/task.yml"
  },
  {
    fragments: ["## Validation", "## Unverified Items", "## Rollback"],
    path: ".github/pull_request_template.md"
  },
  {
    fragments: ["## Validation", "## Unverified Items", "## Rollback"],
    path: ".gitlab/merge_request_templates/Default.md"
  },
  {
    fragments: ["## Documentation Target", "## Validation Plan", "## Risks"],
    path: ".gitlab/issue_templates/Docs.md"
  },
  {
    fragments: ["## Measurement", "## Compatibility Impact", "## Risks"],
    path: ".gitlab/issue_templates/Enhancement.md"
  },
  {
    fragments: ["## Non-goals", "## Validation Plan", "## Risks"],
    path: ".gitlab/issue_templates/Feature.md"
  },
  {
    fragments: [
      "## Behavior Preservation",
      "## Interfaces and Handoffs",
      "## Risks"
    ],
    path: ".gitlab/issue_templates/Refactor.md"
  },
  {
    fragments: ["## Completion Criteria", "## Validation Method", "## Risks"],
    path: ".gitlab/issue_templates/Task.md"
  },
  {
    fragments: [
      "## Goal",
      "## Scope",
      "## Tasks",
      "## Verification",
      "Completed plans must not keep unchecked task lines"
    ],
    path: "docs/templates/docs/exec-plan.md"
  }
];

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

const expectedSetupCommands: Record<StackMode, readonly string[]> = {
  bun: ["codegraph init; codegraph index", "bun install"],
  gradle: ["codegraph init; codegraph index", "./gradlew help"],
  maven: [
    "codegraph init; codegraph index",
    "./mvnw -q -DskipTests dependency:go-offline"
  ],
  shell: ["codegraph init; codegraph index"],
  uv: ["codegraph init; codegraph index", "uv sync"]
};

/**
 * Require one file path to exist.
 *
 * @param filePath File path to inspect.
 */
const requireFile = (filePath: string): void => {
  if (!existsSync(filePath)) {
    fail(`[requireFile] missing required file: ${filePath}`);
  }
  if (!statSync(filePath).isFile()) {
    fail(`[requireFile] expected regular file: ${filePath}`);
  }
};

/**
 * Require one file to contain a text fragment.
 *
 * @param filePath File path to inspect.
 * @param fragment Text fragment that must appear in the file.
 */
const requireText = (filePath: string, fragment: string): void => {
  requireFile(filePath);
  const content = readFileSync(filePath, "utf-8");
  if (!content.includes(fragment)) {
    fail(`[requireText] missing text in ${filePath}: ${fragment}`);
  }
};

/**
 * Read one required regular-expression capture from an asset.
 *
 * @param filePath Asset path to inspect.
 * @param pattern Expression with one required capture group.
 * @param label Description used in a failure message.
 * @returns Captured asset value.
 */
const readRequiredCapture = (
  filePath: string,
  pattern: RegExp,
  label: string
): string => {
  requireFile(filePath);
  const match = readFileSync(filePath, "utf-8").match(pattern);
  if (match?.[1] === undefined) {
    return fail(`[assetVersion] missing ${label}: ${filePath}`);
  }
  return match[1];
};

/**
 * Return the Java release required by the declared Checkstyle major line.
 *
 * @param version Declared Checkstyle version.
 * @returns Required Java release.
 */
const requiredJavaReleaseForCheckstyle = (version: string): number => {
  const major = Number(version.split(".")[0]);
  if (!Number.isInteger(major) || major < 1) {
    return fail(`[assetVersion] invalid Checkstyle version: ${version}`);
  }
  return major >= 13 ? 21 : 17;
};

/** Require an executable Git hook with the repository POSIX header. */
const requirePosixHook = (filePath: string): void => {
  requireFile(filePath);
  try {
    accessSync(filePath, constants.X_OK);
  } catch {
    fail(`[gitHook] hook must be executable: ${filePath}`);
  }
  if (
    !readFileSync(filePath, "utf-8").startsWith(
      "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n"
    )
  ) {
    fail(`[gitHook] hook must use the POSIX header: ${filePath}`);
  }
};

/**
 * Parse one Claude agent YAML frontmatter object.
 *
 * @param filePath Agent Markdown path.
 */
const readClaudeAgentFrontmatter = (
  filePath: string
): Record<string, unknown> => {
  const lines = readFileSync(filePath, "utf-8").split(/\r?\n/u);
  if (lines[0] !== "---") {
    return fail(`[claudeAgent] missing YAML frontmatter: ${filePath}`);
  }
  const end = lines.indexOf("---", 1);
  if (end === -1) {
    return fail(`[claudeAgent] unterminated YAML frontmatter: ${filePath}`);
  }
  let value: unknown;
  try {
    value = Bun.YAML.parse(lines.slice(1, end).join("\n"));
  } catch (error) {
    return fail(
      `[claudeAgent] invalid YAML frontmatter: ${filePath}: ${
        error instanceof Error ? error.message : String(error)
      }`
    );
  }
  if (isRecord(value) && !Array.isArray(value)) {
    return value;
  }
  return fail(`[claudeAgent] frontmatter must be an object: ${filePath}`);
};

/** Require one exact string-list field when the specification declares it. */
const requireAgentStringList = (
  filePath: string,
  field: string,
  actual: unknown,
  expected: readonly string[] | undefined
): void => {
  if (expected === undefined) {
    return;
  }
  if (
    !Array.isArray(actual) ||
    JSON.stringify(actual) !== JSON.stringify(expected)
  ) {
    fail(
      `[claudeAgent] ${field} must equal ${expected.join(", ")}: ${filePath}`
    );
  }
};

/** Require one Claude agent Markdown file to match its target specification. */
const requireClaudeAgentMarkdown = (
  filePath: string,
  spec: TargetAgentSpec
): void => {
  requireFile(filePath);
  const frontmatter = readClaudeAgentFrontmatter(filePath);
  if (frontmatter["name"] !== spec.name) {
    fail(`[claudeAgent] name must match ${spec.name}: ${filePath}`);
  }
  if (
    typeof frontmatter["description"] !== "string" ||
    frontmatter["description"] === ""
  ) {
    fail(`[claudeAgent] missing description: ${filePath}`);
  }
  if (
    spec.claudeModel !== undefined &&
    frontmatter["model"] !== spec.claudeModel
  ) {
    fail(`[claudeAgent] model must match ${spec.claudeModel}: ${filePath}`);
  }
  if (
    spec.claudeEffort !== undefined &&
    frontmatter["effort"] !== spec.claudeEffort
  ) {
    fail(`[claudeAgent] effort must match ${spec.claudeEffort}: ${filePath}`);
  }
  requireAgentStringList(
    filePath,
    "tools",
    frontmatter["tools"],
    spec.claudeTools
  );
  requireAgentStringList(
    filePath,
    "disallowedTools",
    frontmatter["disallowedTools"],
    spec.claudeDisallowedTools
  );
  for (const fragment of spec.fragments) {
    requireText(filePath, fragment);
  }
};

/** Require one Codex agent TOML file to match its target specification. */
const requireCodexAgentToml = (
  filePath: string,
  spec: TargetAgentSpec
): void => {
  requireFile(filePath);
  const value: unknown = Bun.TOML.parse(readFileSync(filePath, "utf-8"));
  if (!isRecord(value)) {
    fail(`[codexAgentToml] expected TOML object: ${filePath}`);
  }
  const toml = value as Record<string, unknown>;
  if (toml["name"] !== spec.name) {
    fail(`[codexAgentToml] name must match ${spec.name}: ${filePath}`);
  }
  if (typeof toml["description"] !== "string" || toml["description"] === "") {
    fail(`[codexAgentToml] missing description: ${filePath}`);
  }
  if (
    typeof toml["developer_instructions"] !== "string" ||
    toml["developer_instructions"] === ""
  ) {
    fail(`[codexAgentToml] missing developer_instructions: ${filePath}`);
  }
  if (spec.codexModel !== undefined && toml["model"] !== spec.codexModel) {
    fail(`[codexAgentToml] model must match ${spec.codexModel}: ${filePath}`);
  }
  if (
    spec.codexEffort !== undefined &&
    toml["model_reasoning_effort"] !== spec.codexEffort
  ) {
    fail(
      `[codexAgentToml] model_reasoning_effort must match ${spec.codexEffort}: ${filePath}`
    );
  }
  for (const fragment of spec.fragments) {
    requireText(filePath, fragment);
  }
};

/**
 * Require one directory path to exist.
 *
 * @param directoryPath Directory path to inspect.
 */
const requireDir = (directoryPath: string): void => {
  if (!existsSync(directoryPath)) {
    fail(`[requireDir] missing required directory: ${directoryPath}`);
  }
  if (!statSync(directoryPath).isDirectory()) {
    fail(`[requireDir] expected directory: ${directoryPath}`);
  }
};

/**
 * Require static text fragments across multiple files.
 *
 * @param checks Static text checks to run.
 */
const requireTexts = (checks: readonly TextCheck[]): void => {
  for (const check of checks) {
    for (const fragment of check.fragments) {
      requireText(check.path, fragment);
    }
  }
};

/**
 * Require one file not to contain target-asset install-time wording.
 *
 * @param filePath File path to inspect.
 * @param fragments Text fragments that must not appear.
 */
const rejectTextFragments = (
  filePath: string,
  fragments: readonly string[]
): void => {
  requireFile(filePath);
  const content = readFileSync(filePath, "utf-8");
  for (const fragment of fragments) {
    if (content.includes(fragment)) {
      fail(`[rejectTextFragments] forbidden text in ${filePath}: ${fragment}`);
    }
  }
};

/**
 * Validate Claude settings structure and setup commands.
 *
 * @param filePath Claude settings path.
 * @param expectedCommands Hook command strings that must be present.
 */
const checkSettings = (
  filePath: string,
  expectedCommands: readonly string[]
): void => {
  const settings = JSON.parse(readFileSync(filePath, "utf-8")) as {
    $schema?: unknown;
    env?: Record<string, unknown>;
    hooks?: { PostToolUse?: unknown };
    includeCoAuthoredBy?: unknown;
    includeGitInstructions?: unknown;
    showClearContextOnPlanAccept?: unknown;
  };
  if (
    settings.$schema !==
    "https://json.schemastore.org/claude-code-settings.json"
  ) {
    fail(`${filePath}: invalid schema`);
  }
  if (
    settings.includeCoAuthoredBy !== false ||
    settings.includeGitInstructions !== false ||
    settings.showClearContextOnPlanAccept !== true
  ) {
    fail(`${filePath}: durable settings flags drifted`);
  }
  if (settings.env?.CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR !== "1") {
    fail(`${filePath}: missing CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR`);
  }
  const groups = settings.hooks?.PostToolUse;
  if (isSingleItemArray(groups)) {
    const [group] = groups;
    const hookGroup = requireClaudeHookGroup(group, filePath);
    const commands = new Set<string>();
    for (const [index, handler] of hookGroup.hooks.entries()) {
      if (isAsyncCommandHook(handler)) {
        commands.add(handler.command);
        continue;
      }
      fail(`${filePath}: invalid EnterWorktree hook at index ${index}`);
    }
    for (const command of expectedCommands) {
      if (!commands.has(command)) {
        fail(`${filePath}: missing hook command ${command}`);
      }
    }
    return;
  }
  fail(`${filePath}: hooks.PostToolUse must define one matcher group`);
};

/**
 * Validate worktree include rules remain portable.
 *
 * @param filePath Worktree include path.
 */
const checkWorktreeinclude = (filePath: string): void => {
  requireTexts([
    { fragments: [".env", ".env.*", "*.local", "*.local.*"], path: filePath }
  ]);
  const content = readFileSync(filePath, "utf-8");
  for (const pattern of [
    ".codegraph/",
    ".gradle/",
    "node_modules/",
    ".bun/",
    ".venv/",
    ".cache/",
    ".mvn/"
  ]) {
    if (content.includes(pattern)) {
      fail(
        `[worktreeinclude] unsafe broad copy pattern in ${filePath}: ${pattern}`
      );
    }
  }
};

/**
 * Validate common ignore rules across operating systems.
 *
 * @param filePath Gitignore path.
 */
const checkGitignoreCommon = (filePath: string): void => {
  requireTexts([
    {
      fragments: [
        ".DS_Store",
        ".com.apple.timemachine.supported",
        ".PKInstallSandboxManager",
        "[Dd]esktop.ini",
        "*.msix",
        "*.lnk",
        ".fuse_hidden*",
        ".idea/",
        ".vscode/",
        "logs/",
        "log/",
        "*.log",
        "*.tmp",
        ".tmp/",
        "tmp/",
        "temp/",
        "*.local.*",
        ".claude/worktrees/"
      ],
      path: filePath
    }
  ]);
};

/**
 * Validate one GitHub workflow command and action fragments.
 *
 * @param filePath GitHub workflow path.
 * @param command Expected command in a simple run step.
 * @param fragments Additional expected fragments.
 */
const checkGithubWorkflow = (
  filePath: string,
  command: null | string,
  fragments: readonly string[]
): void => {
  requireFile(filePath);
  if (command !== null) {
    requireText(filePath, `run: ${command}`);
  }
  for (const fragment of fragments) {
    requireText(filePath, fragment);
  }
};

/**
 * Validate one GitLab CI job command.
 *
 * @param filePath GitLab CI path.
 * @param job Job name.
 * @param command Expected command.
 */
const checkGitlabCi = (
  filePath: string,
  job: string,
  command: string
): void => {
  requireFile(filePath);
  requireText(filePath, `${job}:`);
  requireText(filePath, command);
};

/**
 * Validate common target harness assets.
 *
 * @param root Harness plugin root.
 */
const checkCommonAssets = (root: string): void => {
  const common = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "common"
  );
  requireDir(path.join(common, ".codex", "agents"));
  for (const spec of targetAgentSpecs) {
    requireClaudeAgentMarkdown(
      path.join(common, ".claude", "agents", `${spec.name}.md`),
      spec
    );
    requireCodexAgentToml(
      path.join(common, ".codex", "agents", `${spec.name}.toml`),
      spec
    );
  }
  const reviewAgents = [
    path.join(common, ".claude", "agents", "review.md"),
    path.join(common, ".codex", "agents", "review.toml")
  ];
  requireTexts(
    reviewAgents.map((filePath) => ({
      fragments: [
        "read-only independent leaf reviewer",
        "user requirements, plan, and workflow decisions",
        "applicable `AGENTS.md` instructions",
        "base and head revisions, or an exact changed-file scope and diff",
        "Assess validation evidence without executing validation commands.",
        "Do not delegate, implement, run validation, approve publication, or mutate repository state.",
        "The owning writer fixes every confirmed finding, including minor findings.",
        "fresh independent review leaf re-reviews the same scope",
        "`blocker`, `major`, or `minor`",
        "exact file and line evidence, impact, and bounded fix direction"
      ],
      path: filePath
    }))
  );
  for (const filePath of reviewAgents) {
    rejectTextFragments(filePath, [
      "readiness review",
      "Run or require validation"
    ]);
  }
  if (existsSync(path.join(common, ".claude", "skills", "review"))) {
    fail("[common assets] review must remain an agent contract, not a skill");
  }
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
    ".claude/skills/issue-mining/SKILL.md",
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
    hostTemplateTexts.map((check) => ({
      fragments: check.fragments,
      path: path.join(common, check.path)
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
    "AGENTS.md",
    "ARCHITECTURE.md",
    "WORKFLOW.md",
    "WORKFLOW.github.md",
    "WORKFLOW.gitlab.md",
    "WORKFLOW.none.md",
    ".gitlab/issue_templates/Enhancement.md",
    ".claude/agents/implementation.md",
    ".claude/agents/review.md",
    ".claude/agents/scoped-implementer.md",
    ".claude/skills/autonomous-execution/SKILL.md",
    ".claude/skills/issue-mining/SKILL.md",
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
  requireTexts(
    [
      "WORKFLOW.md",
      "WORKFLOW.github.md",
      "WORKFLOW.gitlab.md",
      "WORKFLOW.none.md"
    ].map((filePath) => ({
      fragments: [
        "`implementation`",
        "`scoped-implementer`",
        "`review`",
        "`issue-mining`",
        "`autonomous-execution`",
        "exhaustive single-file or related-file ownership list",
        "large or cross-file changes",
        "Never send an ambiguous or incomplete file set directly",
        "Parallel `scoped-implementer` assignments MUST have disjoint ownership lists",
        "Only the user-facing top-level or root agent acts as orchestrator",
        "do not create or delegate to a `project-orchestrator` agent"
      ],
      path: path.join(common, filePath)
    }))
  );
  console.error("[common assets] OK");
};

/**
 * Validate stack-wide common files.
 *
 * @param root Harness plugin root.
 */
const checkStackCommonAssets = (root: string): void => {
  for (const mode of stackModes) {
    const assets = path.join(root, "skills", "harness-install", "assets", mode);
    requireFile(path.join(assets, ".gitignore"));
    requireFile(path.join(assets, ".claude", "settings.json"));
    requireFile(path.join(assets, ".worktreeinclude"));
    checkGitignoreCommon(path.join(assets, ".gitignore"));
    checkWorktreeinclude(path.join(assets, ".worktreeinclude"));
    requirePosixHook(path.join(assets, ".githooks", "pre-commit"));
    requirePosixHook(path.join(assets, ".githooks", "pre-push"));
    checkSettings(
      path.join(assets, ".claude", "settings.json"),
      expectedSetupCommands[mode]
    );
  }
};

/**
 * Validate Gradle stack assets.
 *
 * @param root Harness plugin root.
 */
const checkGradleAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "gradle"
  );
  requireDir(
    path.join(
      assets,
      "buildSrc",
      "src",
      "main",
      "kotlin",
      "com",
      "ririnto",
      "sinon",
      "ktlint"
    )
  );
  requireTexts([
    {
      fragments: [
        "checkMarkdown",
        'tasks.named("ktlintCheck")',
        "markdownlint-cli2"
      ],
      path: path.join(assets, "build.gradle.kts")
    }
  ]);
  rejectTextFragments(path.join(assets, "settings.gradle.kts"), [
    "createHooks()",
    "pre-commit-git-hooks"
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "ktlint.yaml"),
    "./gradlew ktlintCheck",
    ["actions/checkout@v7", "gradle/actions/setup-gradle@v6"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "ktlint",
    "./gradlew ktlintCheck"
  );
  console.error("[gradle assets] OK");
};

/**
 * Validate Maven stack assets.
 *
 * @param root Harness plugin root.
 */
const checkMavenAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "maven"
  );
  const pom = path.join(assets, "pom.xml");
  const githubWorkflow = path.join(
    assets,
    ".github",
    "workflows",
    "spotless.yaml"
  );
  const gitlabCi = path.join(assets, ".gitlab-ci.yml");
  requireTexts([
    {
      fragments: [
        "spotless-maven-plugin",
        "markdownlint-cli2",
        "maven-checkstyle-plugin"
      ],
      path: pom
    }
  ]);
  const spotlessVersion = readRequiredCapture(
    pom,
    /<artifactId>spotless-maven-plugin<\/artifactId>\s*<version>(?<version>[^<]+)<\/version>/u,
    "Spotless Maven version"
  );
  const checkstyleVersion = readRequiredCapture(
    pom,
    /<artifactId>checkstyle<\/artifactId>\s*<version>(?<version>[^<]+)<\/version>/u,
    "Checkstyle version"
  );
  const compilerRelease = Number(
    readRequiredCapture(
      pom,
      /<maven\.compiler\.release>(?<release>\d+)<\/maven\.compiler\.release>/u,
      "Maven compiler release"
    )
  );
  const githubRuntime = Number(
    readRequiredCapture(
      githubWorkflow,
      /java-version:\s*["']?(?<runtime>\d+)["']?/u,
      "GitHub Java runtime"
    )
  );
  const gitlabRuntime = Number(
    readRequiredCapture(
      gitlabCi,
      /image:\s*maven:3\.9-eclipse-temurin-(?<runtime>\d+)/u,
      "GitLab Maven Temurin runtime"
    )
  );
  const requiredRuntime = requiredJavaReleaseForCheckstyle(checkstyleVersion);
  if (!/^\d+\.\d+\.\d+$/u.test(spotlessVersion)) {
    fail(`[assetVersion] invalid Spotless Maven version: ${spotlessVersion}`);
  }
  if (!/^\d+\.\d+\.\d+$/u.test(checkstyleVersion)) {
    fail(`[assetVersion] invalid Checkstyle version: ${checkstyleVersion}`);
  }
  if (!Number.isSafeInteger(compilerRelease) || compilerRelease < 1) {
    fail(`[maven assets] invalid compiler release: ${pom}`);
  }
  if (githubRuntime !== requiredRuntime || gitlabRuntime !== requiredRuntime) {
    fail(
      `[maven assets] CI Java runtime must match Checkstyle compatibility release ${requiredRuntime}`
    );
  }
  if (githubRuntime <= compilerRelease) {
    fail("[maven assets] CI Java runtime must exceed the compiler release");
  }
  rejectTextFragments(pom, ["git-build-hook-maven-plugin", "core.hooksPath"]);
  checkGithubWorkflow(githubWorkflow, null, [
    "actions/checkout@v7",
    "actions/setup-java@v5",
    "./mvnw validate -DspotlessFiles"
  ]);
  checkGitlabCi(gitlabCi, "spotless", "./mvnw validate -DspotlessFiles");
  console.error("[maven assets] OK");
};

/**
 * Validate Bun stack assets.
 *
 * @param root Harness plugin root.
 */
const checkBunAssets = (root: string): void => {
  const assets = path.join(root, "skills", "harness-install", "assets", "bun");
  requireFile(path.join(assets, "package.json"));
  rejectTextFragments(path.join(assets, "package.json"), [
    '"prepare"',
    '"husky"'
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "ultracite.yaml"),
    "bun run check",
    ["actions/checkout@v7", "oven-sh/setup-bun@v2"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "ultracite",
    "bun run check"
  );
  console.error("[bun assets] OK");
};

/**
 * Validate uv stack assets.
 *
 * @param root Harness plugin root.
 */
const checkUvAssets = (root: string): void => {
  const assets = path.join(root, "skills", "harness-install", "assets", "uv");
  const checkRunner = path.join(assets, "scripts", "check.py");
  const fixRunner = path.join(assets, "scripts", "fix.py");
  const githubWorkflow = path.join(assets, ".github", "workflows", "ruff.yaml");
  const ruffSpec = readRequiredCapture(
    checkRunner,
    /RUFF_SPEC:\s*Final\s*=\s*"(?<spec>[^"]+)"/u,
    "Ruff check runner constraint"
  );
  const fixRuffSpec = readRequiredCapture(
    fixRunner,
    /RUFF_SPEC:\s*Final\s*=\s*"(?<spec>[^"]+)"/u,
    "Ruff fix runner constraint"
  );
  const setupUvVersion = readRequiredCapture(
    githubWorkflow,
    /astral-sh\/setup-uv@(?<version>v\d+\.\d+\.\d+)/u,
    "setup-uv version"
  );
  if (!/^ruff>=\d+\.\d+\.\d+,<\d+\.\d+\.\d+$/u.test(ruffSpec)) {
    fail(`[assetVersion] invalid Ruff constraint: ${ruffSpec}`);
  }
  if (fixRuffSpec !== ruffSpec) {
    fail("[uv assets] Ruff check and fix constraints must match");
  }
  if (!/^v\d+\.\d+\.\d+$/u.test(setupUvVersion)) {
    fail(`[assetVersion] invalid setup-uv version: ${setupUvVersion}`);
  }
  requireTexts(
    ["README.md", "skills/harness-install/references/rule-interface.md"].map(
      (relativePath) => ({
        fragments: [ruffSpec],
        path: path.join(root, relativePath)
      })
    )
  );
  requireTexts(
    ["scripts/check.ts", "scripts/fix.ts"].map((relativePath) => ({
      fragments: ["readRuffSpec"],
      path: path.join(root, relativePath)
    }))
  );
  requireFile(path.join(assets, "ruff.toml"));
  requireFile(path.join(assets, "pyproject.toml"));
  requireFile(checkRunner);
  requireText(path.join(assets, "pyproject.toml"), "package = false");
  rejectTextFragments(path.join(assets, "pyproject.toml"), ["pre-commit"]);
  if (existsSync(path.join(assets, ".pre-commit-config.yaml"))) {
    fail(
      "[uv assets] .pre-commit-config.yaml must not activate generated hooks"
    );
  }
  if (existsSync(path.join(assets, "uv.toml"))) {
    fail("[uv assets] uv.toml must not exist; use pyproject.toml instead");
  }
  checkGithubWorkflow(githubWorkflow, "uv run scripts/check.py", [
    "actions/checkout@v7",
    `astral-sh/setup-uv@${setupUvVersion}`
  ]);
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "ruff",
    "uv run scripts/check.py"
  );
  console.error("[uv assets] OK");
};

/**
 * Validate shell stack assets.
 *
 * @param root Harness plugin root.
 */
const checkShellAssets = (root: string): void => {
  const assets = path.join(
    root,
    "skills",
    "harness-install",
    "assets",
    "shell"
  );
  for (const filePath of [
    ".githooks/pre-commit",
    ".githooks/pre-push",
    "scripts/check.sh",
    "scripts/fix.sh"
  ]) {
    requireFile(path.join(assets, filePath));
  }
  requireTexts([
    {
      fragments: ["shellcheck -S warning", "shfmt -d", "markdownlint-cli2"],
      path: path.join(assets, "scripts", "check.sh")
    },
    {
      fragments: ["shfmt", "markdownlint-cli2", "--fix"],
      path: path.join(assets, "scripts", "fix.sh")
    }
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "shellcheck.yaml"),
    "sh scripts/check.sh",
    ["actions/checkout@v7", "shellcheck shfmt"]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "shellcheck",
    "sh scripts/check.sh"
  );
  console.error("[shell assets] OK");
};

/**
 * Validate repository-level check and fix scripts.
 *
 * @param root Harness plugin root.
 */
const checkRepositoryScripts = (root: string): void => {
  const scripts = path.join(root, "scripts");
  requireTexts([
    {
      fragments: [
        "markdownlint-cli2",
        "checkPluginPackages",
        "ruff",
        "Repository validation passed."
      ],
      path: path.join(scripts, "check.ts")
    },
    {
      fragments: ["markdownlint-cli2", "--fix", "ruff", "check"],
      path: path.join(scripts, "fix.ts")
    }
  ]);
  console.error("[repository scripts] OK");
};

/**
 * Validate the Bun installer entrypoint and module surface.
 *
 * @param root Harness plugin root.
 */
const checkInstallerSurface = (root: string): void => {
  const scripts = path.join(root, "skills", "harness-install", "scripts");
  requireFile(path.join(scripts, "install-harness.ts"));
  requireFile(path.join(scripts, "asset-manifest.ts"));
  requireFile(path.join(scripts, "generate-asset-manifest.ts"));
  for (const filePath of [
    "cli.ts",
    "commands.ts",
    "contracts.ts",
    "decisions.ts",
    "files.ts",
    "installer.ts",
    "managed.ts",
    "operations.ts",
    "paths.ts",
    "planning.ts",
    "preview.ts",
    "record.ts",
    "types.ts"
  ]) {
    requireFile(path.join(scripts, "install-harness", filePath));
  }
  requireFile(
    path.join(
      root,
      "skills",
      "harness-validate",
      "scripts",
      "validate-install-record.ts"
    )
  );
  console.error("[installer surface] OK");
};

/**
 * Lock installer contract shapes added by the harness-improvements work.
 *
 * Guards against silent regression of the opt-in flags, managed-block
 * markers, install-record schema, the Maven markdown-fix step, and the
 * removed `--no-ci` flag staying removed.
 *
 * @param root Harness plugin root.
 */
const checkInstallerContract = (root: string): void => {
  const installerDir = path.join(
    root,
    "skills",
    "harness-install",
    "scripts",
    "install-harness"
  );
  requireTexts([
    {
      fragments: [
        '"--activate-hooks"',
        '"--adopt"',
        '"--preview"',
        '"--show"',
        '"--only"'
      ],
      path: path.join(installerDir, "cli.ts")
    },
    {
      fragments: [
        'installRecordPath = ".harness/install-record.json"',
        "expectedPlanDigest",
        "schemaVersion: 2"
      ],
      path: path.join(installerDir, "record.ts")
    },
    {
      fragments: [
        'InstallOutcome = "conflict" | "created" | "kept" | "updated"',
        'AssetOwnership = "harness" | "shared" | "target"'
      ],
      path: path.join(installerDir, "types.ts")
    },
    {
      fragments: [
        'import { writeInstallRecord } from "./record.js";',
        "await writeInstallRecord(config, results, true);",
        "await writeInstallRecord(config, results, false);"
      ],
      path: path.join(installerDir, "installer.ts")
    },
    {
      fragments: [
        "<!-- harness:managed begin -->",
        "<!-- harness:managed end -->"
      ],
      path: path.join(installerDir, "managed.ts")
    },
    {
      fragments: ["exec:exec@format-markdown spotless:apply"],
      path: path.join(installerDir, "commands.ts")
    }
  ]);
  rejectTextFragments(path.join(installerDir, "cli.ts"), ['"--no-ci"']);
  console.error("[installer contract] OK");
};

/**
 * Validate the checked-in deny-by-default asset manifest against packaged paths.
 *
 * @param root Harness plugin root.
 */
const checkAssetManifest = (root: string): void => {
  const skillDir = path.join(root, "skills", "harness-install");
  const manifestPath = manifestPathFor(skillDir);
  requireFile(manifestPath);
  const assetsDir = path.join(skillDir, "assets");
  for (const [subdir, entries] of Object.entries(readAssetManifest(skillDir))) {
    const subdirDir = path.resolve(assetsDir, subdir);
    if (
      !subdirDir.startsWith(`${assetsDir}${path.sep}`) ||
      !existsSync(subdirDir) ||
      !lstatSync(subdirDir).isDirectory()
    ) {
      fail(`[assetManifest] invalid asset subdirectory: ${subdir}`);
    }
    for (const entry of entries) {
      const assetPath = path.resolve(subdirDir, entry);
      if (
        !assetPath.startsWith(`${subdirDir}${path.sep}`) ||
        !existsSync(assetPath) ||
        !lstatSync(assetPath).isFile()
      ) {
        fail(`[assetManifest] invalid asset path: ${subdir}/${entry}`);
      }
    }
  }
  console.error("[asset manifest] OK");
};

/**
 * Report whether optional native tools are available.
 */
const checkNativeTools = (): void => {
  for (const tool of [
    "bun",
    "uv",
    "shellcheck",
    "shfmt",
    "markdownlint-cli2"
  ]) {
    const result = spawnSync(tool, ["--version"], { encoding: "utf-8" });
    if (result.error === undefined) {
      console.error(`[tool] ${tool} OK`);
    } else {
      console.error(`[warning] ${tool} not found; smoke test skipped`);
    }
  }
};

/**
 * Run harness plugin self-check.
 *
 * @returns Process exit code.
 */
const main = (): number => {
  const root = Bun.argv[2] ?? path.resolve(import.meta.dirname, "..");
  try {
    console.error("Validating harness plugin native-lint end-state...");
    checkCommonAssets(root);
    checkStackCommonAssets(root);
    checkGradleAssets(root);
    checkMavenAssets(root);
    checkBunAssets(root);
    checkUvAssets(root);
    checkShellAssets(root);
    checkRepositoryScripts(root);
    checkPackageSurface(root);
    checkInstallerSurface(root);
    checkInstallerContract(root);
    checkAssetManifest(root);
    checkInstallerRuntime(root);
    requireFile(path.join(root, "scripts", "plugin-self-check.ts"));
    checkNativeTools();
    console.error("\nHarness asset/package smoke checks passed.");
    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return 1;
  }
};

process.exit(main());
