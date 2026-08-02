#!/usr/bin/env node
// -*- coding: utf-8 -*-

import { readFileSync } from "node:fs";
import path from "node:path";

/**
 * @typedef {"main" | "subagent"} Role
 */

/**
 * @typedef {Readonly<{
 *   hookSpecificOutput: Readonly<{
 *     hookEventName: "SessionStart" | "SubagentStart";
 *     additionalContext: string;
 *   }>;
 * }>} HookEnvelope
 */

/**
 * @typedef {Readonly<Record<Role, string>>} RoleSkillPaths
 */

const packageRoot = process.env.CLAUDE_PLUGIN_ROOT;
const role = process.argv.at(2);
/** @type {RoleSkillPaths} */
const roleSkillPaths = Object.freeze({
  main: "skills/orchestrating-work/SKILL.md",
  subagent: "skills/executing-delegated-work/SKILL.md"
});
const YAML_FRONTMATTER_RE = /^---[\s\S]*?---\s*/u;
const MARKDOWN_REFERENCE_LINK_RE = /(?<=\]\()(?:\.\/)?references\/[^)\s]+/gu;

/**
 * Remove one valid leading YAML frontmatter block without changing the role body.
 *
 * @param {string} source - Complete canonical skill source.
 * @returns {string} Skill source without leading YAML frontmatter.
 */
const stripYamlFrontmatter = (source) =>
  source.replace(YAML_FRONTMATTER_RE, "");

/**
 * Rewrite Markdown links to references under the selected skill root.
 *
 * @param {string} source - Frontmatter-stripped canonical skill source.
 * @param {string} skillRoot - Directory containing the selected SKILL.md.
 * @returns {string} Skill source with resolved reference links.
 */
const rewriteReferenceLinks = (source, skillRoot) =>
  source.replaceAll(MARKDOWN_REFERENCE_LINK_RE, (referencePath) =>
    path.resolve(skillRoot, referencePath.replace(/^\.\//u, ""))
  );

/**
 * Read the canonical skill source for one role.
 *
 * @param {string} root - Package root containing the canonical skill tree.
 * @param {Role} selectedRole - Routed role name.
 * @returns {string} Canonical role body with resolved reference links.
 */
const readContext = (root, selectedRole) => {
  const skillPath = path.join(root, roleSkillPaths[selectedRole]);
  const skillRoot = path.dirname(skillPath);
  return rewriteReferenceLinks(
    stripYamlFrontmatter(readFileSync(skillPath, "utf-8")),
    skillRoot
  );
};

/**
 * Build the exact hook envelope expected by Claude Code.
 *
 * @param {string} root - Package root containing the canonical skill tree.
 * @param {Role} selectedRole - Routed role name.
 * @returns {HookEnvelope} Exact Claude hook envelope.
 */
const createEnvelope = (root, selectedRole) => ({
  hookSpecificOutput: {
    additionalContext: readContext(root, selectedRole),
    hookEventName:
      selectedRole === "subagent" ? "SubagentStart" : "SessionStart"
  }
});

/**
 * Require a non-empty JSON object from standard input.
 *
 * @param {string} input - Complete standard-input payload.
 * @returns {void} Nothing when the payload is a JSON object.
 */
const validateInput = (input) => {
  if (input.trim().length === 0) {
    throw new TypeError("stdin JSON object is required");
  }
  const value = JSON.parse(input);
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError("stdin JSON must be an object");
  }
};

if (typeof packageRoot !== "string" || packageRoot.length === 0) {
  throw new Error("CLAUDE_PLUGIN_ROOT is required");
}

if (role !== "main" && role !== "subagent") {
  throw new Error("role must be main or subagent");
}

validateInput(readFileSync(0, "utf-8"));
process.stdout.write(`${JSON.stringify(createEnvelope(packageRoot, role))}\n`);
