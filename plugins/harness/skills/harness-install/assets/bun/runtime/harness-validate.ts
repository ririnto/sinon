#!/usr/bin/env bun
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { HARNESS_CHECKS } from "./harness-check";
import { logger } from "./logger";

const root = process.cwd();
const MANIFEST_PATH = "docs/harness/manifest.json";

type Manifest = Record<string, unknown>;

function pathOf(path: string): string {
	return join(root, path);
}

function loadManifest(): Manifest {
	try {
		return JSON.parse(readFileSync(pathOf(MANIFEST_PATH), "utf8")) as Manifest;
	} catch {
		return {};
	}
}

function main(): void {
	const manifest = loadManifest();
	if (
		!manifest ||
		typeof manifest !== "object" ||
		Object.keys(manifest).length === 0
	) {
		logger.error(`[ERROR] manifest not found or invalid: ${MANIFEST_PATH}`);
		process.exit(1);
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
	Object.keys(manifest)
		.filter((key) => !knownCategories.has(key) && !knownKeys.has(key))
		.forEach((key) => logger.warn(`[WARN] unknown manifest key: ${key}`));

	const uniqueFindings = Array.from(
		new Map(
			HARNESS_CHECKS.filter((check) => check.applies(manifest))
				.flatMap((check) => check.validate(root, manifest))
				.map((f) => [`${f.severity}|${f.category}|${f.message}`, f]),
		).values(),
	);

	const errors = uniqueFindings.filter((f) => f.severity === "ERROR");
	const warnings = uniqueFindings.filter((f) => f.severity === "WARN");
	const infos = uniqueFindings.filter((f) => f.severity === "INFO");

	errors.forEach((e) => logger.error(`[ERROR] ${e.message}`));
	warnings.forEach((w) => logger.warn(`[WARN] ${w.message}`));
	infos.forEach((i) => logger.info(`[INFO] ${i.message}`));

	if (0 < errors.length) {
		logger.error("Harness validation failed");
		process.exit(1);
	}
	logger.log("Harness validation passed");
}

main();
