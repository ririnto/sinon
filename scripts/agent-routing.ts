#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { existsSync } from "node:fs";
import path from "node:path";

import { validateClaudeAgent } from "./agent-routing-claude.js";
import { reportAgentRoutingResult } from "./agent-routing-cli.js";
import { validateCodexAgent } from "./agent-routing-codex.js";
import {
  discoverAgents,
  validateInventory
} from "./agent-routing-inventory.js";
import {
  parseAgentRoutingManifest,
  validateManifestEntry
} from "./agent-routing-manifest.js";
import { validateCounterpartParity } from "./agent-routing-parity.js";

/** Result of deterministic repository agent-routing validation. */
export interface AgentRoutingResult {
  errors: readonly string[];
  warnings: readonly string[];
}

/** Validate all canonical Claude and Codex agent mappings in one repository. */
export const validateAgentRouting = (
  root: string,
  manifestPath = path.join(root, "scripts", "agent-routing-manifest.json")
): AgentRoutingResult => {
  const errors: string[] = [];
  const warnings: string[] = [];
  const manifest = parseAgentRoutingManifest(manifestPath, errors);
  if (manifest === undefined) {
    return { errors, warnings };
  }

  const names = manifest.agents.map((entry) => entry.name);
  if (new Set(names).size !== names.length) {
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
    const codex = validateCodexAgent(root, entry, errors);
    validateCounterpartParity(entry.codexPath ?? "", claude, codex, errors);
  }
  return { errors, warnings };
};

if (import.meta.main) {
  const root = path.resolve(
    Bun.argv[2] ?? path.join(import.meta.dirname, "..")
  );
  process.exit(reportAgentRoutingResult(validateAgentRouting(root)));
}
