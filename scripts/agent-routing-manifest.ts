import { readFileSync } from "node:fs";

import {
  AGENT_ACCESSES,
  AGENT_TOPOLOGIES,
  CLAUDE_MODELS,
  CLAUDE_TO_CODEX,
  CODEX_MODELS,
  hasNonEmptyString,
  isRecord,
  TOPOLOGY_MODEL
} from "./agent-routing-contract.js";
import type {
  AgentAccess,
  AgentManifestEntry,
  AgentRoutingManifest,
  AgentTopology
} from "./agent-routing-contract.js";

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

const validateStringFields = (
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

const validateCodexFields = (
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

const parseEntry = (
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
  const stringsValid = validateStringFields(value, label, errors);
  const accessValid = AGENT_ACCESSES.has(value["access"] as AgentAccess);
  const topologyValid = AGENT_TOPOLOGIES.has(
    value["topology"] as AgentTopology
  );
  if (!accessValid) {
    errors.push(`${label}: unsupported access ${String(value["access"])}`);
  }
  if (!topologyValid) {
    errors.push(`${label}: unsupported topology ${String(value["topology"])}`);
  }
  if (
    !stringsValid ||
    !accessValid ||
    !topologyValid ||
    !validateCodexFields(value, label, errors)
  ) {
    return undefined;
  }
  return value as AgentManifestEntry;
};

export const parseAgentRoutingManifest = (
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
  const parseEntries = (
    entries: readonly unknown[],
    label: string
  ): readonly AgentManifestEntry[] =>
    entries.flatMap((entry, index) => {
      const parsed = parseEntry(entry, `${label}[${index}]`, errors);
      return parsed === undefined ? [] : [parsed];
    });
  return {
    agents: parseEntries(value["agents"], "agents"),
    pendingIntegrations: parseEntries(
      value["pendingIntegrations"] ?? [],
      "pendingIntegrations"
    ),
    schemaVersion: 1
  };
};

export const validateManifestEntry = (
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
    return;
  }
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
};
