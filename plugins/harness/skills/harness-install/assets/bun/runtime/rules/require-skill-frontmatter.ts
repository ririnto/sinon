#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require frontmatter in skill files.
 */
export const requireSkillFrontmatterRule = (ctx: RuleContext): HarnessCheckRule => ({
  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireSkillFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return parameters.rootDirectory !== undefined;
  }

  validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
    const section = ctx.readJsonObject(manifest.requireSkillFrontmatter);
    const parameters = ctx.readJsonObject(section.parameters);
    const rootDirectory = typeof parameters.rootDirectory === "string" ? parameters.rootDirectory : "";
    const filename = typeof parameters.filename === "string" ? parameters.filename : "SKILL.md";

    if (!rootDirectory || !ctx.isDirectory(rootDirectory)) {
      return [];
    }

    const [skills, dirFindings] = ctx.walkDirectory(rootDirectory);
    const skillFiles = skills.filter((f) => f.endsWith(`/${filename}`));

    if (skillFiles.length === 0) {
      return [
        {
          severity: ctx.severityOf(manifest, "requireSkillFrontmatter"),
          category: "requireSkillFrontmatter",
          message: ".claude/skills must contain at least one SKILL.md",
        },
      ];
    }

    return dirFindings.concat(
      skillFiles.flatMap((skill) => {
        const text = ctx.read(skill);
        return [
          !text.startsWith("---")
            ? {
                severity: ctx.severityOf(manifest, "requireSkillFrontmatter"),
                category: "requireSkillFrontmatter",
                message: `skill missing frontmatter: ${skill}`,
              }
            : null,
          !/^description:\s*.+$/m.test(text)
            ? {
                severity: ctx.severityOf(manifest, "requireSkillFrontmatter"),
                category: "requireSkillFrontmatter",
                message: `skill missing description: ${skill}`,
              }
            : null,
        ].filter((f): f is Finding => f !== null);
      })
    );
  }

});
