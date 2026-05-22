#!/usr/bin/env bun
import { dirname } from "node:path";
import type { Finding, HarnessCheckRule, HarnessManifest, RuleContext } from "../harness-check-rule";

/**
 * Require frontmatter in agent files.
 */
export class RequireAgentFrontmatterRule implements HarnessCheckRule {
  static readonly category = "requireAgentFrontmatter";

  constructor(private readonly ctx: RuleContext) {}

  applies(manifest: HarnessManifest): boolean {
    const section = manifest.requireAgentFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = this.ctx.readJsonObject((section as Record<string, unknown>).parameters);
    return parameters.directory !== undefined;
  }

  validate(_root: string, manifest: HarnessManifest): Finding[] {
    const section = this.ctx.readJsonObject(manifest.requireAgentFrontmatter);
    const parameters = this.ctx.readJsonObject(section.parameters);
    const directory = typeof parameters.directory === "string" ? parameters.directory : "";
    if (!directory || !this.ctx.isDirectory(directory)) {
      return [];
    }

    const [agents, dirFindings] = this.ctx.walkDirectory(directory);
    const agentFiles = agents.filter((f) => dirname(f) === directory && f.endsWith(".md"));

    if (agentFiles.length === 0) {
      return [
        {
          severity: this.ctx.severityOf(manifest, RequireAgentFrontmatterRule.category),
          category: RequireAgentFrontmatterRule.category,
          message: ".claude/agents must contain at least one .md agent",
        },
      ];
    }

    return dirFindings.concat(
      agentFiles.flatMap((agent) => {
        const text = this.ctx.read(agent);
        return [
          !text.startsWith("---")
            ? {
                severity: this.ctx.severityOf(manifest, RequireAgentFrontmatterRule.category),
                category: RequireAgentFrontmatterRule.category,
                message: `agent missing frontmatter: ${agent}`,
              }
            : null,
          !/^name:\s*[-a-z0-9]+\s*$/m.test(text)
            ? {
                severity: this.ctx.severityOf(manifest, RequireAgentFrontmatterRule.category),
                category: RequireAgentFrontmatterRule.category,
                message: `agent missing name: ${agent}`,
              }
            : null,
          !/^description:\s*.+$/m.test(text)
            ? {
                severity: this.ctx.severityOf(manifest, RequireAgentFrontmatterRule.category),
                category: RequireAgentFrontmatterRule.category,
                message: `agent missing description: ${agent}`,
              }
            : null,
        ].filter((f): f is Finding => f !== null);
      })
    );
  }
}
