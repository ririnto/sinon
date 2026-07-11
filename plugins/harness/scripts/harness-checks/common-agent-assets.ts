// -*- coding: utf-8 -*-

import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { fail, isRecord, requireDir, requireFile } from "./check-support.js";

type TargetAgentSpec = Readonly<{
  claudeDisallowedTools?: readonly string[];
  claudeTools?: readonly string[];
  name: string;
}>;

const targetAgentSpecs: readonly TargetAgentSpec[] = [
  { claudeTools: ["Read", "Glob", "Grep"], name: "reviewer" },
  {
    claudeTools: ["Read", "Glob", "Grep", "Edit", "Write"],
    name: "implementer"
  },
  {
    claudeDisallowedTools: ["Agent"],
    claudeTools: ["Read", "Edit", "Write"],
    name: "scoped-implementer"
  },
  {
    claudeTools: ["Read", "Glob", "Grep", "Bash"],
    name: "validation-executor"
  }
];

export const readClaudeAgentFrontmatter = (
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
  return isRecord(value) && !Array.isArray(value)
    ? value
    : fail(`[claudeAgent] frontmatter must be an object: ${filePath}`);
};

const requireAgentStringList = (
  filePath: string,
  field: string,
  actual: unknown,
  expected: readonly string[] | undefined
): void => {
  if (
    expected !== undefined &&
    (!Array.isArray(actual) ||
      JSON.stringify(actual) !== JSON.stringify(expected))
  ) {
    fail(
      `[claudeAgent] ${field} must equal ${expected.join(", ")}: ${filePath}`
    );
  }
};

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
};

const requireCodexAgentToml = (
  filePath: string,
  spec: TargetAgentSpec
): void => {
  requireFile(filePath);
  const value: unknown = Bun.TOML.parse(readFileSync(filePath, "utf-8"));
  const toml = isRecord(value)
    ? value
    : fail(`[codexAgentToml] expected TOML object: ${filePath}`);
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
};

/** Validate packaged target-agent identities and frontmatter contracts. */
export const checkCommonAgentAssets = (common: string): void => {
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
  if (existsSync(path.join(common, ".claude", "skills", "review"))) {
    fail("[common assets] review must remain an agent contract, not a skill");
  }
  for (const pathName of [
    ".claude/agents/project-orchestrator.md",
    ".claude/agents/explorer.md",
    ".codex/agents/project-orchestrator.toml",
    ".codex/agents/explorer.toml"
  ]) {
    if (existsSync(path.join(common, pathName))) {
      fail(`[common assets] forbidden packaged agent: ${pathName}`);
    }
  }
};
