#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { dirname } from "node:path";
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require frontmatter in agent files.
 */
export const agentFrontmatterRule: HarnessCheckRule = {
  category: "agentFrontmatter",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.agentFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readJsonObject((section as Record<string, unknown>).parameters)
        .directory !== undefined
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.agentFrontmatter).parameters,
    );
    const directory =
      typeof parameters.directory === "string" ? parameters.directory : "";
    if (!directory || !ctx.isDirectory(directory)) {
      return [];
    }
    const [agents, dirFindings] = ctx.walkDirectory(directory);
    const agentFiles = agents.filter(
      (f) => dirname(f) === directory && f.endsWith(".md"),
    );
    if (agentFiles.length === 0) {
      return [
        {
          severity: ctx.severityOf("agentFrontmatter"),
          category: "agentFrontmatter",
          message: ".claude/agents must contain at least one .md agent",
          file: directory,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
          fix: {
            description: "create at least one agent .md file",
            safety: "manual",
          },
        },
      ];
    }
    return dirFindings.concat(
      agentFiles
        .map((agent) => ({ agent, text: ctx.read(agent) }))
        .flatMap(({ agent, text }) => {
          const findings: Finding[] = [];
          if (!text.startsWith("---")) {
            findings.push({
              severity: ctx.severityOf("agentFrontmatter"),
              category: "agentFrontmatter",
              message: `agent missing frontmatter: ${agent}`,
              file: agent,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: 1,
              fix: {
                description: "add frontmatter block with --- delimiters",
                safety: "manual",
              },
            });
          }
          if (!/^name:\s*[-a-z0-9]+\s*$/m.test(text)) {
            findings.push({
              severity: ctx.severityOf("agentFrontmatter"),
              category: "agentFrontmatter",
              message: `agent missing name: ${agent}`,
              file: agent,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: 1,
              fix: {
                description: "add 'name: <kebab-case-identifier>' to frontmatter",
                safety: "manual",
              },
            });
          }
          if (!/^description:\s*.+$/m.test(text)) {
            findings.push({
              severity: ctx.severityOf("agentFrontmatter"),
              category: "agentFrontmatter",
              message: `agent missing description: ${agent}`,
              file: agent,
              startLine: 1,
              startColumn: 1,
              endLine: 1,
              endColumn: 1,
              fix: {
                description: "add 'description: <text>' to frontmatter",
                safety: "manual",
              },
            });
          }
          return findings;
        }),
    );
  },
};
