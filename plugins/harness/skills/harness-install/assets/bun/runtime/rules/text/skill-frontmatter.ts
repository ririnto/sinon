#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import type {
  Finding,
  HarnessCheckRule,
  RuleContext,
} from "../harness-check-rule";

/**
 * Require frontmatter in skill files.
 */
export const skillFrontmatterRule: HarnessCheckRule = {
  category: "skillFrontmatter",
  applies(ctx: RuleContext): boolean {
    const section = ctx.manifest.raw.skillFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    return (
      enabled !== false &&
      ctx.readJsonObject((section as Record<string, unknown>).parameters)
        .rootDirectory !== undefined
    );
  },

  validate(ctx: RuleContext): readonly Finding[] {
    const parameters = ctx.readJsonObject(
      ctx.readJsonObject(ctx.manifest.raw.skillFrontmatter).parameters,
    );
    const rootDirectory =
      typeof parameters.rootDirectory === "string"
        ? parameters.rootDirectory
        : "";
    const filename =
      typeof parameters.filename === "string"
        ? parameters.filename
        : "SKILL.md";
    if (!rootDirectory || !ctx.isDirectory(rootDirectory)) {
      return [];
    }
    const [skills, dirFindings] = ctx.walkDirectory(rootDirectory);
    const skillFiles = skills.filter((f) => f.endsWith(`/${filename}`));
    if (skillFiles.length === 0) {
      return [
        {
          severity: ctx.severityOf("skillFrontmatter"),
          category: "skillFrontmatter",
          message: ".claude/skills must contain at least one SKILL.md",
          file: rootDirectory,
          startLine: 1,
          startColumn: 1,
          endLine: 1,
          endColumn: 1,
          fix: {
            description: `create ${filename} file with frontmatter`,
            safety: "manual",
          },
        },
      ];
    }
    return dirFindings.concat(
      skillFiles
        .map((skill) => ({ skill, text: ctx.read(skill) }))
        .flatMap(({ skill, text }) => {
          const findings: Finding[] = [];
          if (!text.startsWith("---")) {
            findings.push({
              severity: ctx.severityOf("skillFrontmatter"),
              category: "skillFrontmatter",
              message: `skill missing frontmatter: ${skill}`,
              file: skill,
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
          if (!/^description:\s*.+$/m.test(text)) {
            findings.push({
              severity: ctx.severityOf("skillFrontmatter"),
              category: "skillFrontmatter",
              message: `skill missing description: ${skill}`,
              file: skill,
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
