#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

import { checkPackageSurface } from "./harness-checks/package-surface.js";

type StackMode = "bun" | "gradle" | "maven" | "shell" | "uv";

type TextCheck = Readonly<{
  fragments: readonly string[];
  path: string;
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

const hostTemplateFiles = [
  ".github/ISSUE_TEMPLATE/config.yml",
  ".github/ISSUE_TEMPLATE/bug_report.yml",
  ".github/ISSUE_TEMPLATE/feature_request.yml",
  ".github/ISSUE_TEMPLATE/task.yml",
  ".github/pull_request_template.md",
  ".gitlab/issue_templates/Bug.md",
  ".gitlab/issue_templates/Feature.md",
  ".gitlab/issue_templates/Task.md",
  ".gitlab/merge_request_templates/Default.md"
] as const;

const hostTemplateTexts: readonly TextCheck[] = [
  {
    fragments: ["Acceptance criteria", "Validation plan", "Risks"],
    path: ".github/ISSUE_TEMPLATE/bug_report.yml"
  },
  {
    fragments: ["Non-goals", "Acceptance criteria", "Validation plan"],
    path: ".github/ISSUE_TEMPLATE/feature_request.yml"
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
    fragments: [
      "## TL;DR (For humans)",
      "## File Structure",
      "## Final Verification Wave",
      "## Commit Strategy",
      "Do not add hash metadata, branch metadata, or external planning-tool paths"
    ],
    path: "docs/templates/docs/exec-plan.md"
  }
];

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
 * Require one tracked directory path to exist.
 *
 * @param root Harness plugin root.
 * @param directoryPath Directory path to inspect.
 */
const requireDir = (root: string, directoryPath: string): void => {
  if (!existsSync(directoryPath)) {
    fail(`[requireDir] missing required directory: ${directoryPath}`);
  }
  if (!statSync(directoryPath).isDirectory()) {
    fail(`[requireDir] expected directory: ${directoryPath}`);
  }
  const relativePath = path.relative(root, directoryPath);
  const result = spawnSync(
    "git",
    ["-C", root, "ls-files", "--", relativePath],
    {
      encoding: "utf-8"
    }
  );
  if (result.status !== 0 || result.stdout.trim() === "") {
    fail(`[requireDir] missing tracked entries: ${directoryPath}`);
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
    ".claude/agents/implementation-agent.md",
    ".claude/agents/review-agent.md",
    ".claude/skills/review/SKILL.md",
    ".claude/skills/validate/SKILL.md",
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
        "`.agents/skills/` MUST be `-> .claude/skills/`",
        "`.agents/agents/` MUST NOT exist",
        "`.codex/agents/` MUST be `-> .claude/agents/`"
      ],
      path: path.join(common, "AGENTS.md")
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
    root,
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
    },
    {
      fragments: ['tasks("ktlintCheck")', "createHooks()"],
      path: path.join(assets, "settings.gradle.kts")
    }
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
  requireTexts([
    {
      fragments: [
        "spotless-maven-plugin",
        "markdownlint-cli2",
        "maven-checkstyle-plugin"
      ],
      path: path.join(assets, "pom.xml")
    }
  ]);
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "spotless.yaml"),
    null,
    [
      "actions/checkout@v7",
      "actions/setup-java@v5",
      "./mvnw validate -DspotlessFiles"
    ]
  );
  checkGitlabCi(
    path.join(assets, ".gitlab-ci.yml"),
    "spotless",
    "./mvnw validate -DspotlessFiles"
  );
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
  requireFile(path.join(assets, "ruff.toml"));
  requireFile(path.join(assets, "uv.toml"));
  requireFile(path.join(assets, "scripts", "check.py"));
  checkGithubWorkflow(
    path.join(assets, ".github", "workflows", "ruff.yaml"),
    "uv run scripts/check.py",
    ["actions/checkout@v7", "astral-sh/setup-uv@v8.2.0"]
  );
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
  for (const filePath of [
    "cli.ts",
    "commands.ts",
    "contracts.ts",
    "files.ts",
    "installer.ts",
    "operations.ts",
    "paths.ts",
    "planning.ts",
    "preview.ts",
    "types.ts"
  ]) {
    requireFile(path.join(scripts, "install-harness", filePath));
  }
  console.error("[installer surface] OK");
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
