import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import {
  validateEffort,
  validateLocalReferences,
  validateTopology
} from "./agent-routing-artifact-rules.js";
import {
  isRecord,
  normalizeInstructions,
  stringList
} from "./agent-routing-contract.js";
import type {
  AgentManifestEntry,
  ClaudeAgent
} from "./agent-routing-contract.js";

const CLAUDE_FRONTMATTER_FIELDS = new Set([
  "background",
  "color",
  "description",
  "disallowedTools",
  "effort",
  "initialPrompt",
  "isolation",
  "maxTurns",
  "memory",
  "model",
  "name",
  "skills",
  "tools"
]);
const PROJECT_ONLY_AGENT_FIELDS = new Set([
  "hooks",
  "mcpServers",
  "permissionMode"
]);

const parseClaudeAgent = (filePath: string): ClaudeAgent => {
  const source = readFileSync(filePath, "utf-8");
  const match =
    /^---\r?\n(?<frontmatter>[\s\S]*?)\r?\n---(?:\r?\n(?<body>[\s\S]*))?$/u.exec(
      source
    );
  if (match?.groups?.["frontmatter"] === undefined) {
    throw new Error(`${filePath}: missing YAML frontmatter`);
  }
  const value: unknown = Bun.YAML.parse(match.groups["frontmatter"]);
  if (!isRecord(value)) {
    throw new Error(`${filePath}: YAML frontmatter must be an object`);
  }
  return {
    body: normalizeInstructions(match.groups["body"] ?? ""),
    frontmatter: value
  };
};

const validateFrontmatter = (
  label: string,
  frontmatter: Readonly<Record<string, unknown>>,
  errors: string[]
): void => {
  for (const [field, value] of Object.entries(frontmatter)) {
    if (PROJECT_ONLY_AGENT_FIELDS.has(field)) {
      const reason = label.includes("/.claude/agents/")
        ? "disallowed by Sinon installed-agent security policy"
        : "ignored for plugin-loaded agents";
      errors.push(`${label}: ${field} is ${reason}`);
    } else if (!CLAUDE_FRONTMATTER_FIELDS.has(field)) {
      errors.push(`${label}: unsupported frontmatter field ${field}`);
    } else if (
      [
        "color",
        "description",
        "effort",
        "initialPrompt",
        "isolation",
        "memory",
        "model",
        "name"
      ].includes(field) &&
      typeof value !== "string"
    ) {
      errors.push(`${label}: ${field} must be a string`);
    } else if (
      ["disallowedTools", "skills", "tools"].includes(field) &&
      !(
        typeof value === "string" ||
        (Array.isArray(value) &&
          value.every((item) => typeof item === "string"))
      )
    ) {
      errors.push(`${label}: ${field} must be a string or string array`);
    } else if (field === "background" && typeof value !== "boolean") {
      errors.push(`${label}: background must be a boolean`);
    } else if (
      field === "maxTurns" &&
      (typeof value !== "number" || !Number.isInteger(value) || value < 1)
    ) {
      errors.push(`${label}: maxTurns must be a positive integer`);
    } else if (field === "isolation" && value !== "worktree") {
      errors.push(`${label}: isolation must be worktree`);
    }
  }
};

export const validateClaudeAgent = (
  root: string,
  entry: AgentManifestEntry,
  errors: string[],
  warnings: string[]
): ClaudeAgent | undefined => {
  const filePath = path.join(root, entry.claudePath);
  if (!existsSync(filePath)) {
    return undefined;
  }
  let agent: ClaudeAgent;
  try {
    agent = parseClaudeAgent(filePath);
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return undefined;
  }
  const { frontmatter, body } = agent;
  validateFrontmatter(entry.claudePath, frontmatter, errors);
  if (frontmatter["name"] !== entry.name) {
    errors.push(
      `${entry.claudePath}: name must match manifest entry ${entry.name}`
    );
  }
  if (frontmatter["model"] !== entry.claudeModel) {
    errors.push(`${entry.claudePath}: model must be ${entry.claudeModel}`);
  }
  if (frontmatter["effort"] !== entry.claudeEffort) {
    errors.push(`${entry.claudePath}: effort must be ${entry.claudeEffort}`);
  }
  if (
    typeof frontmatter["description"] !== "string" ||
    frontmatter["description"].trim() === ""
  ) {
    errors.push(`${entry.claudePath}: description must be a non-empty string`);
  }
  validateEffort(
    entry.claudePath,
    entry.claudeModel,
    entry.claudeEffort,
    body,
    entry.topology,
    errors,
    warnings
  );
  validateTopology(
    entry,
    entry.claudePath,
    body,
    stringList(frontmatter["tools"]),
    true,
    errors
  );
  validateLocalReferences(root, filePath, body, errors);
  return agent;
};
