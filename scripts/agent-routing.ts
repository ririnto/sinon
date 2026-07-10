#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";

type AgentAccess = "read-only" | "router" | "writer";
type AgentTopology = "inventory" | "leaf" | "mechanical";

type AgentManifestEntry = Readonly<{
  access: AgentAccess;
  claudeEffort: string;
  claudeModel: string;
  claudePath: string;
  codexEffort?: string;
  codexModel?: string;
  codexPath?: string;
  name: string;
  topology: AgentTopology;
}>;

type AgentRoutingManifest = Readonly<{
  agents: readonly AgentManifestEntry[];
  pendingIntegrations?: readonly AgentManifestEntry[];
  schemaVersion: number;
}>;

type ClaudeAgent = Readonly<{
  body: string;
  frontmatter: Readonly<Record<string, unknown>>;
}>;

type CodexAgent = Readonly<{
  body: string;
  toml: Readonly<Record<string, unknown>>;
}>;

type DiscoveredAgents = Readonly<{
  claude: ReadonlySet<string>;
  codex: ReadonlySet<string>;
}>;

/** Result of deterministic repository agent-routing validation. */
export interface AgentRoutingResult {
  errors: readonly string[];
  warnings: readonly string[];
}

const CLAUDE_MODELS = new Set(["haiku", "opus", "sonnet"]);
const CODEX_MODELS = new Set(["gpt-5.6-luna", "gpt-5.6-sol", "gpt-5.6-terra"]);
const EFFORTS = new Set(["high", "low", "max", "medium", "xhigh"]);
const MUTATION_TOOLS = new Set(["Edit", "MultiEdit", "NotebookEdit", "Write"]);
const HIGH_EFFORTS = new Set(["high", "max", "xhigh"]);
const CLAUDE_TO_CODEX = new Map([
  ["haiku", "gpt-5.6-luna"],
  ["opus", "gpt-5.6-sol"],
  ["sonnet", "gpt-5.6-terra"]
]);
const TOPOLOGY_MODEL = new Map<AgentTopology, string>([
  ["inventory", "haiku"],
  ["leaf", "sonnet"],
  ["mechanical", "haiku"]
]);
const EXCLUDED_DIRECTORIES = new Set([".git", ".worktrees", "node_modules"]);
const READ_ONLY_NAME_PATTERN =
  /(?:reviewer|validator|^review$|inventory-scanner|commit-message-architect|pr-body-architect)$/u;
const AGENT_ACCESSES = new Set<AgentAccess>(["read-only", "router", "writer"]);
const AGENT_TOPOLOGIES = new Set<AgentTopology>([
  "inventory",
  "leaf",
  "mechanical"
]);
const MANIFEST_FIELDS = new Set([
  "access",
  "claudeEffort",
  "claudeModel",
  "claudePath",
  "codexEffort",
  "codexModel",
  "codexPath",
  "name",
  "topology"
]);
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

const relativePath = (root: string, filePath: string): string =>
  path.relative(root, filePath).split(path.sep).join("/");

const normalizeWhitespace = (value: string): string =>
  value.replaceAll(/\s+/gu, " ").trim();

const isRecord = (value: unknown): value is Readonly<Record<string, unknown>> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const hasNonEmptyString = (value: unknown): value is string =>
  typeof value === "string" && value !== "";

const validateManifestStringFields = (
  value: Readonly<Record<string, unknown>>,
  label: string,
  errors: string[]
): boolean => {
  const fields = [
    "name",
    "claudePath",
    "claudeModel",
    "claudeEffort",
    "access",
    "topology"
  ] as const;
  for (const field of fields) {
    if (!hasNonEmptyString(value[field])) {
      errors.push(`${label}: ${field} must be a non-empty string`);
    }
  }
  return fields.every((field) => hasNonEmptyString(value[field]));
};

const validateCodexManifestFields = (
  value: Readonly<Record<string, unknown>>,
  label: string,
  errors: string[]
): boolean => {
  const fields = ["codexPath", "codexModel", "codexEffort"] as const;
  const present = fields.filter((field) => value[field] !== undefined);
  if (present.length !== 0 && present.length !== fields.length) {
    errors.push(
      `${label}: Codex path, model, and effort must be declared together`
    );
  }
  for (const field of present) {
    if (!hasNonEmptyString(value[field])) {
      errors.push(`${label}: ${field} must be a non-empty string`);
    }
  }
  return (
    present.length === 0 ||
    (present.length === fields.length &&
      present.every((field) => hasNonEmptyString(value[field])))
  );
};

const parseManifestEntry = (
  value: unknown,
  label: string,
  errors: string[]
): AgentManifestEntry | undefined => {
  if (!isRecord(value)) {
    errors.push(`${label}: entry must be an object`);
    return undefined;
  }
  for (const field of Object.keys(value)) {
    if (!MANIFEST_FIELDS.has(field)) {
      errors.push(`${label}: unsupported field ${field}`);
    }
  }
  const stringsValid = validateManifestStringFields(value, label, errors);
  if (!AGENT_ACCESSES.has(value["access"] as AgentAccess)) {
    errors.push(`${label}: unsupported access ${String(value["access"])}`);
  }
  if (!AGENT_TOPOLOGIES.has(value["topology"] as AgentTopology)) {
    errors.push(`${label}: unsupported topology ${String(value["topology"])}`);
  }
  const codexFieldsValid = validateCodexManifestFields(value, label, errors);
  if (
    !stringsValid ||
    !AGENT_ACCESSES.has(value["access"] as AgentAccess) ||
    !AGENT_TOPOLOGIES.has(value["topology"] as AgentTopology) ||
    !codexFieldsValid
  ) {
    return undefined;
  }
  return value as AgentManifestEntry;
};

const parseManifest = (
  manifestPath: string,
  errors: string[]
): AgentRoutingManifest | undefined => {
  let value: unknown;
  try {
    value = JSON.parse(readFileSync(manifestPath, "utf-8"));
  } catch (error) {
    errors.push(
      `${manifestPath}: invalid JSON: ${error instanceof Error ? error.message : String(error)}`
    );
    return undefined;
  }
  if (!isRecord(value)) {
    errors.push(`${manifestPath}: manifest must be an object`);
    return undefined;
  }
  for (const field of Object.keys(value)) {
    if (!["agents", "pendingIntegrations", "schemaVersion"].includes(field)) {
      errors.push(`${manifestPath}: unsupported top-level field ${field}`);
    }
  }
  if (value["schemaVersion"] !== 1) {
    errors.push(`${manifestPath}: schemaVersion must be 1`);
  }
  if (!Array.isArray(value["agents"])) {
    errors.push(`${manifestPath}: agents must be an array`);
    return undefined;
  }
  if (
    value["pendingIntegrations"] !== undefined &&
    !Array.isArray(value["pendingIntegrations"])
  ) {
    errors.push(`${manifestPath}: pendingIntegrations must be an array`);
    return undefined;
  }
  const agents = value["agents"].flatMap((entry, index) => {
    const parsed = parseManifestEntry(entry, `agents[${index}]`, errors);
    return parsed === undefined ? [] : [parsed];
  });
  const pendingIntegrations = (
    (value["pendingIntegrations"] as readonly unknown[] | undefined) ?? []
  ).flatMap((entry, index) => {
    const parsed = parseManifestEntry(
      entry,
      `pendingIntegrations[${index}]`,
      errors
    );
    return parsed === undefined ? [] : [parsed];
  });
  return { agents, pendingIntegrations, schemaVersion: 1 };
};

const parseClaudeAgent = (filePath: string): ClaudeAgent => {
  const lines = readFileSync(filePath, "utf-8").split(/\r?\n/u);
  if (lines[0] !== "---") {
    throw new Error(`${filePath}: missing YAML frontmatter`);
  }
  const end = lines.indexOf("---", 1);
  if (end === -1) {
    throw new Error(`${filePath}: unterminated YAML frontmatter`);
  }
  const value: unknown = Bun.YAML.parse(lines.slice(1, end).join("\n"));
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error(`${filePath}: YAML frontmatter must be an object`);
  }
  return {
    body: lines
      .slice(end + 1)
      .join("\n")
      .trim(),
    frontmatter: value as Readonly<Record<string, unknown>>
  };
};

const parseCodexAgent = (filePath: string): CodexAgent => {
  const value: unknown = Bun.TOML.parse(readFileSync(filePath, "utf-8"));
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new Error(`${filePath}: TOML value must be an object`);
  }
  const toml = value as Readonly<Record<string, unknown>>;
  return {
    body:
      typeof toml["developer_instructions"] === "string"
        ? toml["developer_instructions"].trim()
        : "",
    toml
  };
};

const stringList = (value: unknown): readonly string[] => {
  if (typeof value === "string") {
    return value.split(",").map((item) => item.trim());
  }
  if (Array.isArray(value) && value.every((item) => typeof item === "string")) {
    return value;
  }
  return [];
};

const discoverAgents = (root: string, errors: string[]): DiscoveredAgents => {
  const claude = new Set<string>();
  const codex = new Set<string>();
  const inspectAgentsDirectory = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const filePath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may not contain nested files or directories`
        );
        for (const nested of readdirSync(filePath, { recursive: true })) {
          if (typeof nested !== "string") {
            continue;
          }
          errors.push(
            `${relativePath(root, path.join(filePath, nested))}: agent directories may not contain nested files or directories`
          );
        }
      } else if (entry.isSymbolicLink()) {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may not contain symlink entries`
        );
      } else if (entry.isFile() && entry.name.endsWith(".md")) {
        claude.add(relativePath(root, filePath));
      } else if (entry.isFile() && entry.name.endsWith(".toml")) {
        codex.add(relativePath(root, filePath));
      } else {
        errors.push(
          `${relativePath(root, filePath)}: agent directories may contain only direct Markdown or TOML agent files`
        );
      }
    }
  };
  const walk = (directory: string): void => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (EXCLUDED_DIRECTORIES.has(entry.name) || entry.isSymbolicLink()) {
        continue;
      }
      const filePath = path.join(directory, entry.name);
      if (!entry.isDirectory()) {
        continue;
      }
      if (entry.name === "agents") {
        inspectAgentsDirectory(filePath);
      } else {
        walk(filePath);
      }
    }
  };
  walk(root);
  return { claude, codex };
};

const validateInventory = (
  label: string,
  expected: ReadonlySet<string>,
  actual: ReadonlySet<string>,
  errors: string[]
): void => {
  const missing = [...expected].filter((item) => !actual.has(item)).toSorted();
  const unexpected = [...actual]
    .filter((item) => !expected.has(item))
    .toSorted();
  if (missing.length > 0 || unexpected.length > 0) {
    errors.push(
      `${label} inventory drift; missing: ${missing.join(", ") || "none"}; unexpected: ${unexpected.join(", ") || "none"}`
    );
  }
};

const hasSectionContent = (body: string, heading: string): boolean => {
  const pattern = new RegExp(
    `^## ${heading}\\n(?<content>[\\s\\S]*?)(?=^## |(?![\\s\\S]))`,
    "mu"
  );
  return (pattern.exec(body)?.groups?.["content"]?.trim().length ?? 0) > 0;
};

const validateEffort = (
  label: string,
  model: string,
  effort: string,
  body: string,
  topology: AgentTopology,
  errors: string[],
  warnings: string[]
): void => {
  if (!EFFORTS.has(effort)) {
    errors.push(`${label}: unsupported effort ${effort}`);
  }
  if (
    HIGH_EFFORTS.has(effort) &&
    !hasSectionContent(body, "Effort Exception")
  ) {
    errors.push(
      `${label}: ${effort} requires a written Effort Exception section`
    );
  }
  if (
    topology === "leaf" &&
    effort === "low" &&
    !hasSectionContent(body, "Low Effort Rationale")
  ) {
    errors.push(
      `${label}: low leaf effort requires a written Low Effort Rationale section`
    );
  }
  if (
    (topology === "inventory" || topology === "mechanical") &&
    effort !== "low"
  ) {
    errors.push(
      `${label}: lightweight inventory and mechanical agents must use low effort`
    );
  }
  if (model === "haiku") {
    warnings.push(
      `${label}: Claude accepts effort: low, but current official documentation does not list Haiku effort as effective; treat the declaration as runtime-inert compatibility metadata`
    );
  }
};

const validateLocalReferences = (
  root: string,
  filePath: string,
  body: string,
  errors: string[]
): void => {
  for (const match of body.matchAll(
    /`(?<plugin>[a-z0-9-]+):(?<skill>[a-z0-9-]+)`/gu
  )) {
    const plugin = match.groups?.["plugin"];
    const skill = match.groups?.["skill"];
    if (plugin === undefined || skill === undefined) {
      continue;
    }
    const skillPath = path.join(
      root,
      "plugins",
      plugin,
      "skills",
      skill,
      "SKILL.md"
    );
    if (!existsSync(skillPath)) {
      errors.push(
        `${relativePath(root, filePath)}: broken skill reference ${plugin}:${skill}`
      );
    }
  }
  for (const match of body.matchAll(
    /docs\/agent-references\/[a-z0-9_./-]+\.md/gu
  )) {
    const [reference] = match;
    const pluginMatch = /^plugins\/(?<plugin>[^/]+)\//u.exec(
      relativePath(root, filePath)
    );
    const candidates = [path.join(root, reference)];
    if (pluginMatch?.groups?.["plugin"] !== undefined) {
      candidates.push(
        path.join(root, "plugins", pluginMatch.groups["plugin"], reference)
      );
    }
    if (!candidates.some((candidate) => existsSync(candidate))) {
      errors.push(
        `${relativePath(root, filePath)}: broken agent reference ${reference}`
      );
    }
  }
};

const validateClaudeFrontmatter = (
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

const hasExecutionProcess = (body: string): boolean =>
  /^## (?:Authoring Procedure|Core Process|How to Use This Agent|Process|Workflow)\b/imu.test(
    body
  );

const hasOutputContract = (body: string): boolean =>
  /^## Output(?: Contract| Format| contract)?\b/imu.test(body);

const hasStopContract = (body: string): boolean => {
  const sections = body.matchAll(
    /^## (?:Boundaries|Edge Cases|Escalation|Ownership Boundary|Pitfalls|Scope(?: Notes| Boundaries|:.*?)?|Stop Conditions)\b[^\n]*\n(?<content>[\s\S]*?)(?=^## |(?![\s\S]))/gimu
  );
  return [...sections].some((match) =>
    /\b(?:ambiguity|blocker|does not|do not|failed|missing|outside|stop|uncertain|unresolved)\b/iu.test(
      match.groups?.["content"] ?? ""
    )
  );
};

const validateTopology = (
  entry: AgentManifestEntry,
  label: string,
  body: string,
  tools: readonly string[],
  enforceTools: boolean,
  errors: string[]
): void => {
  const mutationTools = tools.filter((tool) => MUTATION_TOOLS.has(tool));
  if (entry.access !== "writer" && mutationTools.length > 0) {
    errors.push(
      `${label}: ${entry.access} agent exposes mutation tools ${mutationTools.join(", ")}`
    );
  }
  if (enforceTools && entry.access === "writer" && mutationTools.length === 0) {
    errors.push(`${label}: writer agents must expose a mutation tool`);
  }
  const delegationTools = tools.filter(
    (tool) =>
      tool === "Agent" ||
      tool.startsWith("Agent(") ||
      tool === "Task" ||
      tool.startsWith("Task(")
  );
  if (delegationTools.length > 0) {
    errors.push(
      `${label}: installable leaf agents may not expose delegation tools or child allowlists`
    );
  }
  if (!/This agent is a .*leaf/iu.test(body)) {
    errors.push(`${label}: leaf topology must be explicit in the agent body`);
  }
  if (!hasExecutionProcess(body)) {
    errors.push(`${label}: agent body must define an executable process`);
  }
  if (!hasStopContract(body)) {
    errors.push(`${label}: agent body must define a blocker or stop path`);
  }
  if (!hasOutputContract(body)) {
    errors.push(`${label}: agent body must define an output contract`);
  }
  if (READ_ONLY_NAME_PATTERN.test(entry.name) && entry.access !== "read-only") {
    errors.push(
      `${label}: reviewer, validator, scanner, and drafting roles must be read-only`
    );
  }
};

const validateManifestEntry = (
  entry: AgentManifestEntry,
  errors: string[]
): void => {
  const expectedClaudeModel = TOPOLOGY_MODEL.get(entry.topology);
  if (entry.claudeModel !== expectedClaudeModel) {
    errors.push(
      `${entry.name}: ${entry.topology} topology requires Claude model ${expectedClaudeModel}`
    );
  }
  if (!CLAUDE_MODELS.has(entry.claudeModel)) {
    errors.push(`${entry.name}: unsupported Claude model ${entry.claudeModel}`);
  }
  if (entry.codexPath === undefined) {
    if (entry.codexModel !== undefined || entry.codexEffort !== undefined) {
      errors.push(`${entry.name}: Codex routing fields require codexPath`);
    }
  } else {
    const expectedCodexModel = CLAUDE_TO_CODEX.get(entry.claudeModel);
    if (entry.codexModel !== expectedCodexModel) {
      errors.push(
        `${entry.name}: Claude ${entry.claudeModel} must pair with ${expectedCodexModel}`
      );
    }
    if (entry.codexEffort !== entry.claudeEffort) {
      errors.push(
        `${entry.name}: Claude and Codex counterpart efforts must match`
      );
    }
    if (entry.codexModel === undefined || !CODEX_MODELS.has(entry.codexModel)) {
      errors.push(`${entry.name}: unsupported Codex model ${entry.codexModel}`);
    }
  }
};

const validateClaudeAgent = (
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
  const label = entry.claudePath;
  const { frontmatter, body } = agent;
  validateClaudeFrontmatter(label, frontmatter, errors);
  if (frontmatter["name"] !== entry.name) {
    errors.push(`${label}: name must match manifest entry ${entry.name}`);
  }
  if (frontmatter["model"] !== entry.claudeModel) {
    errors.push(`${label}: model must be ${entry.claudeModel}`);
  }
  if (frontmatter["effort"] !== entry.claudeEffort) {
    errors.push(`${label}: effort must be ${entry.claudeEffort}`);
  }
  if (
    typeof frontmatter["description"] !== "string" ||
    frontmatter["description"].trim() === ""
  ) {
    errors.push(`${label}: description must be a non-empty string`);
  }
  const tools = stringList(frontmatter["tools"]);
  validateEffort(
    label,
    entry.claudeModel,
    entry.claudeEffort,
    body,
    entry.topology,
    errors,
    warnings
  );
  validateTopology(entry, label, body, tools, true, errors);
  validateLocalReferences(root, filePath, body, errors);
  return agent;
};

const validateCodexAgent = (
  root: string,
  entry: AgentManifestEntry,
  claude: ClaudeAgent | undefined,
  errors: string[]
): void => {
  if (
    entry.codexPath === undefined ||
    entry.codexModel === undefined ||
    entry.codexEffort === undefined
  ) {
    return;
  }
  const filePath = path.join(root, entry.codexPath);
  if (!existsSync(filePath)) {
    return;
  }
  let agent: CodexAgent;
  try {
    agent = parseCodexAgent(filePath);
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return;
  }
  const label = entry.codexPath;
  const { toml, body } = agent;
  if (toml["name"] !== entry.name) {
    errors.push(`${label}: name must match manifest entry ${entry.name}`);
  }
  if (toml["model"] !== entry.codexModel) {
    errors.push(`${label}: model must be ${entry.codexModel}`);
  }
  if (toml["model_reasoning_effort"] !== entry.codexEffort) {
    errors.push(
      `${label}: model_reasoning_effort must be ${entry.codexEffort}`
    );
  }
  const expectedSandbox =
    entry.access === "writer" ? "workspace-write" : "read-only";
  if (toml["sandbox_mode"] !== expectedSandbox) {
    errors.push(`${label}: sandbox_mode must be ${expectedSandbox}`);
  }
  validateTopology(entry, label, body, [], false, errors);
  validateLocalReferences(root, filePath, body, errors);
  if (claude !== undefined) {
    const claudeDescription = claude.frontmatter["description"];
    const codexDescription = toml["description"];
    if (
      typeof claudeDescription !== "string" ||
      typeof codexDescription !== "string" ||
      normalizeWhitespace(claudeDescription) !==
        normalizeWhitespace(codexDescription)
    ) {
      errors.push(`${label}: counterpart description drift`);
    }
    if (claude.body !== body) {
      errors.push(`${label}: counterpart developer instructions drift`);
    }
  }
};

/** Validate all canonical Claude and Codex agent mappings in one repository. */
export const validateAgentRouting = (
  root: string,
  manifestPath = path.join(root, "scripts", "agent-routing-manifest.json")
): AgentRoutingResult => {
  const errors: string[] = [];
  const warnings: string[] = [];
  const manifest = parseManifest(manifestPath, errors);
  if (manifest === undefined) {
    return { errors, warnings };
  }
  const names = manifest.agents.map((entry) => entry.name);
  const allNames = new Set(names);
  if (allNames.size !== names.length) {
    errors.push("agent routing manifest contains duplicate names");
  }
  if (names.includes("project-orchestrator")) {
    errors.push(
      "project-orchestrator must remain top-level workflow policy, not an installable agent"
    );
  }
  const claudePaths = manifest.agents.map((entry) => entry.claudePath);
  const codexPaths = manifest.agents.flatMap((entry) =>
    entry.codexPath === undefined ? [] : [entry.codexPath]
  );
  if (new Set(claudePaths).size !== claudePaths.length) {
    errors.push("agent routing manifest contains duplicate Claude paths");
  }
  if (new Set(codexPaths).size !== codexPaths.length) {
    errors.push("agent routing manifest contains duplicate Codex paths");
  }
  for (const pending of manifest.pendingIntegrations ?? []) {
    validateManifestEntry(pending, errors);
    if (
      existsSync(path.join(root, pending.claudePath)) ||
      (pending.codexPath !== undefined &&
        existsSync(path.join(root, pending.codexPath)))
    ) {
      errors.push(
        `${pending.name}: pending integration files exist; promote the entry into the canonical agents inventory`
      );
    }
  }
  const discovered = discoverAgents(root, errors);
  validateInventory(
    "Claude agent",
    new Set(claudePaths),
    discovered.claude,
    errors
  );
  validateInventory(
    "Codex agent",
    new Set(codexPaths),
    discovered.codex,
    errors
  );
  for (const entry of manifest.agents) {
    validateManifestEntry(entry, errors);
    const claude = validateClaudeAgent(root, entry, errors, warnings);
    validateCodexAgent(root, entry, claude, errors);
  }
  return { errors, warnings };
};

const main = (): number => {
  const root = path.resolve(
    Bun.argv[2] ?? path.join(import.meta.dirname, "..")
  );
  const result = validateAgentRouting(root);
  for (const warning of result.warnings) {
    console.warn(`[agent routing warning] ${warning}`);
  }
  if (result.errors.length > 0) {
    console.error(
      `Agent routing validation failed:\n${result.errors.join("\n")}`
    );
    return 1;
  }
  console.error(
    `[agent routing] OK (${result.warnings.length} compatibility warning)`
  );
  return 0;
};

if (import.meta.main) {
  process.exit(main());
}
