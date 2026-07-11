// -*- coding: utf-8 -*-

import { readFileSync } from "node:fs";
import path from "node:path";

import {
  fail,
  isRecord,
  requireFile,
  requirePosixHook,
  requireTexts
} from "./check-support.js";

type StackMode = "bun" | "gradle" | "maven" | "shell" | "uv";
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

const stackModes = ["gradle", "maven", "bun", "uv", "shell"] as const;
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

const isClaudeHookGroup = (value: unknown): value is ClaudeHookGroup =>
  isRecord(value) &&
  value["matcher"] === "EnterWorktree" &&
  Array.isArray(value["hooks"]);
const isSingleItemArray = (value: unknown): value is readonly [unknown] =>
  Array.isArray(value) && value.length === 1;
const isAsyncCommandHook = (
  value: unknown
): value is ClaudeHook & { command: string } =>
  isRecord(value) &&
  value["async"] === true &&
  typeof value["command"] === "string" &&
  !("args" in value) &&
  !("matcher" in value) &&
  value["type"] === "command";

const requireClaudeHookGroup = (
  value: unknown,
  filePath: string
): ClaudeHookGroup => {
  if (isClaudeHookGroup(value)) {
    return value;
  }
  return fail(`${filePath}: PostToolUse matcher must target EnterWorktree`);
};

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

export const checkStackCommonAssets = (root: string): void => {
  for (const mode of stackModes) {
    const assets = path.join(root, "skills", "harness-install", "assets", mode);
    const settings = path.join(assets, ".claude", "settings.json");
    requireFile(path.join(assets, ".gitignore"));
    requireFile(settings);
    requireFile(path.join(assets, ".worktreeinclude"));
    checkGitignoreCommon(path.join(assets, ".gitignore"));
    checkWorktreeinclude(path.join(assets, ".worktreeinclude"));
    requirePosixHook(path.join(assets, ".githooks", "pre-commit"));
    requirePosixHook(path.join(assets, ".githooks", "pre-push"));
    checkSettings(settings, expectedSetupCommands[mode]);
  }
};
