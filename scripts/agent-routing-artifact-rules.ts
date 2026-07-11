import { existsSync } from "node:fs";
import path from "node:path";

import {
  EFFORTS,
  HIGH_EFFORTS,
  MUTATION_TOOLS,
  relativePath
} from "./agent-routing-contract.js";
import type {
  AgentManifestEntry,
  AgentTopology
} from "./agent-routing-contract.js";

const READ_ONLY_NAME_PATTERN =
  /(?:reviewer|validator|^review$|inventory-scanner|commit-message-architect|pr-body-architect)$/u;

const hasSectionContent = (body: string, heading: string): boolean => {
  const pattern = new RegExp(
    `^## ${heading}\\n(?<content>[\\s\\S]*?)(?=^## |(?![\\s\\S]))`,
    "mu"
  );
  return (pattern.exec(body)?.groups?.["content"]?.trim().length ?? 0) > 0;
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

export const validateEffort = (
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

export const validateLocalReferences = (
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
    if (
      plugin !== undefined &&
      skill !== undefined &&
      !existsSync(
        path.join(root, "plugins", plugin, "skills", skill, "SKILL.md")
      )
    ) {
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

export const validateTopology = (
  entry: AgentManifestEntry,
  label: string,
  body: string,
  tools: readonly string[],
  enforceTools: boolean,
  errors: string[]
): void => {
  const mutationTools = tools.filter((tool) => MUTATION_TOOLS.has(tool));
  if (entry.access === "executor" && entry.topology !== "mechanical") {
    errors.push(`${label}: executor access requires mechanical topology`);
  }
  if (entry.access !== "writer" && mutationTools.length > 0) {
    errors.push(
      `${label}: ${entry.access} agent exposes mutation tools ${mutationTools.join(", ")}`
    );
  }
  if (enforceTools && entry.access === "writer" && mutationTools.length === 0) {
    errors.push(`${label}: writer agents must expose a mutation tool`);
  }
  if (enforceTools && entry.access === "executor" && !tools.includes("Bash")) {
    errors.push(`${label}: executor agents must expose Bash`);
  }
  const delegationTools = tools.filter(
    (tool) =>
      tool === "Agent" ||
      tool.startsWith("Agent(") ||
      tool === "Task" ||
      tool.startsWith("Task(")
  );
  if (
    (entry.topology === "leaf" || entry.topology === "mechanical") &&
    delegationTools.length > 0
  ) {
    errors.push(
      `${label}: ${entry.topology} topology agents may not expose delegation tools or child allowlists`
    );
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
