#!/usr/bin/env bun
import { dirname } from "node:path";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "../harness-check-rule";

/**
 * Require frontmatter in agent files.
 */
export const agentFrontmatterRule = (
	ctx: RuleContext,
): HarnessCheckRule => ({
	category: "agentFrontmatter",
	applies(manifest: HarnessManifest): boolean {
		const section = manifest.agentFrontmatter;
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

	validate(_root: string, manifest: HarnessManifest): readonly Finding[] {
		const parameters = ctx.readJsonObject(
			ctx.readJsonObject(manifest.agentFrontmatter).parameters,
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
					severity: ctx.severityOf(manifest, "agentFrontmatter"),
					category: "agentFrontmatter",
					message: ".claude/agents must contain at least one .md agent",
				},
			];
		}
		return dirFindings.concat(
			agentFiles
				.map((agent) => ({ agent, text: ctx.read(agent) }))
				.flatMap(({ agent, text }) =>
					[
						!text.startsWith("---")
							? {
									severity: ctx.severityOf(manifest, "agentFrontmatter"),
									category: "agentFrontmatter",
									message: `agent missing frontmatter: ${agent}`,
								}
							: null,
						!/^name:\s*[-a-z0-9]+\s*$/m.test(text)
							? {
									severity: ctx.severityOf(manifest, "agentFrontmatter"),
									category: "agentFrontmatter",
									message: `agent missing name: ${agent}`,
								}
							: null,
						!/^description:\s*.+$/m.test(text)
							? {
									severity: ctx.severityOf(manifest, "agentFrontmatter"),
									category: "agentFrontmatter",
									message: `agent missing description: ${agent}`,
								}
							: null,
					].filter((f): f is Finding => f !== null),
				),
		);
	},
});
