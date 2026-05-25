#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { createRuleContext } from "./core/rule-context";
import { HARNESS_CHECKS, MANIFEST_PATH } from "./harness-check";
import { logger } from "./logger";
import { renderFindings } from "./reporter";
import type { Finding } from "./rules/harness-check-rule";

const root = process.cwd();

/**
 * Validate harness installation and exit with non-zero status on errors.
 */
function main(): void {
	const rawManifest: unknown = JSON.parse(
		readFileSync(join(root, MANIFEST_PATH), "utf8"),
	);
	const context = createRuleContext(root, rawManifest);
	const manifest = context.manifest.raw;
	if (manifest === null || Object.keys(manifest).length === 0) {
		throw new Error(`manifest is empty or malformed: ${MANIFEST_PATH}`);
	}
	const knownCategories = new Set<string>(
		HARNESS_CHECKS.map((c) => c.category),
	);
	const knownKeys = new Set<string>([
		"name",
		"description",
		"$schema",
		"seedFiles",
		"generatedArtifacts",
		"harnessEvolution",
		"teamPatterns",
	]);
	const unknownKeyFindings: readonly Finding[] = Object.keys(manifest)
		.filter((key) => !knownCategories.has(key) && !knownKeys.has(key))
		.map((key) => ({
			severity: "WARN" as const,
			category: "manifestSchema",
			message: `unknown manifest key: ${key}`,
		}));
	const ruleFindings: readonly Finding[] = Array.from(
		new Map(
			HARNESS_CHECKS.filter((rule) => rule.applies(context))
				.flatMap((rule) => rule.validate(context))
				.map((finding) => [
					`${finding.severity}|${finding.category}|${finding.message}|${finding.file ?? ""}|${finding.startLine ?? ""}|${finding.startColumn ?? ""}`,
					finding,
				]),
		).values(),
	);
	const findings: readonly Finding[] = unknownKeyFindings.concat(ruleFindings);
	renderFindings(root, findings).forEach((line) => {
		logger.log(line);
	});
	if (findings.some((finding) => finding.severity === "ERROR")) {
		process.exit(1);
	}
}

if (import.meta.main) {
	main();
}
