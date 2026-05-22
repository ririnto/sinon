#!/usr/bin/env bun
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require frontmatter in skill files.
 */
export class RequireSkillFrontmatterRule implements HarnessCheckRule {
  static readonly category = "requireSkillFrontmatter";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireSkillFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return parameters.rootDirectory !== undefined;
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireSkillFrontmatter);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const rootDirectory = typeof parameters.rootDirectory === "string" ? parameters.rootDirectory : "";
    const filename = typeof parameters.filename === "string" ? parameters.filename : "SKILL.md";

    if (!rootDirectory || !this.ctx.isDirectory(rootDirectory)) {
      return [];
    }

    const [skills, dirFindings] = this.ctx.walkDirectory(rootDirectory);
    const skillFiles = skills.filter((f) => f.endsWith(`/${filename}`));

    if (skillFiles.length === 0) {
      return [
        {
          severity: this.ctx.severityOf(manifest, RequireSkillFrontmatterRule.category),
          category: RequireSkillFrontmatterRule.category,
          message: ".claude/skills must contain at least one SKILL.md",
        },
      ];
    }

    return dirFindings.concat(
      skillFiles.flatMap((skill) => {
        const text = this.ctx.read(skill);
        return [
          !text.startsWith("---")
            ? {
                severity: this.ctx.severityOf(manifest, RequireSkillFrontmatterRule.category),
                category: RequireSkillFrontmatterRule.category,
                message: `skill missing frontmatter: ${skill}`,
              }
            : null,
          !/^description:\s*.+$/m.test(text)
            ? {
                severity: this.ctx.severityOf(manifest, RequireSkillFrontmatterRule.category),
                category: RequireSkillFrontmatterRule.category,
                message: `skill missing description: ${skill}`,
              }
            : null,
        ].filter((f): f is Finding => f !== null);
      })
    );
  }
}
