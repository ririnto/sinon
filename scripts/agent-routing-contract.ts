import path from "node:path";

export type AgentAccess = "executor" | "read-only" | "router" | "writer";
export type AgentTopology = "inventory" | "leaf" | "mechanical";

export type AgentManifestEntry = Readonly<{
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

export type AgentRoutingManifest = Readonly<{
  agents: readonly AgentManifestEntry[];
  pendingIntegrations?: readonly AgentManifestEntry[];
  schemaVersion: number;
}>;

export type ClaudeAgent = Readonly<{
  body: string;
  frontmatter: Readonly<Record<string, unknown>>;
}>;

export type CodexAgent = Readonly<{
  body: string;
  toml: Readonly<Record<string, unknown>>;
}>;

export const CLAUDE_MODELS = new Set(["haiku", "opus", "sonnet"]);
export const CODEX_MODELS = new Set([
  "gpt-5.6-luna",
  "gpt-5.6-sol",
  "gpt-5.6-terra"
]);
export const EFFORTS = new Set(["high", "low", "max", "medium", "xhigh"]);
export const HIGH_EFFORTS = new Set(["high", "max", "xhigh"]);
export const MUTATION_TOOLS = new Set([
  "Edit",
  "MultiEdit",
  "NotebookEdit",
  "Write"
]);
export const CLAUDE_TO_CODEX = new Map([
  ["haiku", "gpt-5.6-luna"],
  ["opus", "gpt-5.6-sol"],
  ["sonnet", "gpt-5.6-terra"]
]);
export const TOPOLOGY_MODEL = new Map<AgentTopology, string>([
  ["inventory", "haiku"],
  ["leaf", "sonnet"],
  ["mechanical", "haiku"]
]);
export const AGENT_ACCESSES = new Set<AgentAccess>([
  "executor",
  "read-only",
  "router",
  "writer"
]);
export const AGENT_TOPOLOGIES = new Set<AgentTopology>([
  "inventory",
  "leaf",
  "mechanical"
]);

export const relativePath = (root: string, filePath: string): string =>
  path.relative(root, filePath).split(path.sep).join("/");

export const isRecord = (
  value: unknown
): value is Readonly<Record<string, unknown>> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

export const hasNonEmptyString = (value: unknown): value is string =>
  typeof value === "string" && value !== "";

export const normalizeInstructions = (value: string): string =>
  value.replaceAll(/\r\n?/gu, "\n").trim();

export const normalizeDescription = (value: string): string =>
  value.replaceAll(/\s+/gu, " ").trim();

export const stringList = (value: unknown): readonly string[] => {
  if (typeof value === "string") {
    return value.split(",").map((item) => item.trim());
  }
  if (Array.isArray(value) && value.every((item) => typeof item === "string")) {
    return value;
  }
  return [];
};
