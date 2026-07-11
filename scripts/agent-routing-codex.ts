import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import {
  validateLocalReferences,
  validateTopology
} from "./agent-routing-artifact-rules.js";
import { isRecord, normalizeInstructions } from "./agent-routing-contract.js";
import type {
  AgentManifestEntry,
  CodexAgent
} from "./agent-routing-contract.js";

const parseCodexAgent = (filePath: string): CodexAgent => {
  const value: unknown = Bun.TOML.parse(readFileSync(filePath, "utf-8"));
  if (!isRecord(value)) {
    throw new Error(`${filePath}: TOML value must be an object`);
  }
  const instructions = value["developer_instructions"];
  if (typeof instructions !== "string") {
    throw new TypeError(`${filePath}: developer_instructions must be a string`);
  }
  return { body: normalizeInstructions(instructions), toml: value };
};

export const validateCodexAgent = (
  root: string,
  entry: AgentManifestEntry,
  errors: string[]
): CodexAgent | undefined => {
  if (
    entry.codexPath === undefined ||
    entry.codexModel === undefined ||
    entry.codexEffort === undefined
  ) {
    return undefined;
  }
  const filePath = path.join(root, entry.codexPath);
  if (!existsSync(filePath)) {
    return undefined;
  }
  let agent: CodexAgent;
  try {
    agent = parseCodexAgent(filePath);
  } catch (error) {
    errors.push(error instanceof Error ? error.message : String(error));
    return undefined;
  }
  const { toml, body } = agent;
  if (toml["name"] !== entry.name) {
    errors.push(
      `${entry.codexPath}: name must match manifest entry ${entry.name}`
    );
  }
  if (toml["model"] !== entry.codexModel) {
    errors.push(`${entry.codexPath}: model must be ${entry.codexModel}`);
  }
  if (toml["model_reasoning_effort"] !== entry.codexEffort) {
    errors.push(
      `${entry.codexPath}: model_reasoning_effort must be ${entry.codexEffort}`
    );
  }
  const expectedSandbox =
    entry.access === "writer" || entry.access === "executor"
      ? "workspace-write"
      : "read-only";
  if (toml["sandbox_mode"] !== expectedSandbox) {
    errors.push(`${entry.codexPath}: sandbox_mode must be ${expectedSandbox}`);
  }
  validateTopology(entry, entry.codexPath, body, [], false, errors);
  validateLocalReferences(root, filePath, body, errors);
  return agent;
};
