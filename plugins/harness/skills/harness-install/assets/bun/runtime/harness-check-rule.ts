#!/usr/bin/env bun

/**
 * Strategy interface implemented by each harness check rule.
 */
export interface HarnessCheckRule {
	/**
	 * Determines whether this rule applies to the given manifest.
	 */
	applies(manifest: HarnessManifest): boolean;

	/**
	 * Validates the project against this rule.
	 * Returns a read-only array of findings.
	 */
	validate(projectDir: string, manifest: HarnessManifest): readonly Finding[];
}

export interface Finding {
	severity: "ERROR" | "WARN" | "INFO";
	category: string;
	message: string;
}

export type HarnessManifest = Record<string, unknown>;

/**
 * Shared context passed to all rule instances.
 */
export interface RuleContext {
	pathOf(path: string): string;
	read(path: string): string;
	firstLine(path: string): string;
	isFile(path: string): boolean;
	isDirectory(path: string): boolean;
	isExecutablePath(path: string): boolean;
	isSymlink(path: string): boolean;
	allowedRootContractTarget(path: string): string | null;
	readStringArray(value: unknown): readonly string[];
	readJsonObject(value: unknown): Record<string, unknown>;
	severityOf(
		manifest: HarnessManifest,
		category: string,
	): "ERROR" | "WARN" | "INFO";
	stackSources(manifest: HarnessManifest, category: string): readonly string[];
	walkDirectory(path: string): readonly [readonly string[], readonly Finding[]];
	collectFilesUnder(
		path: string,
	): readonly [readonly string[], readonly Finding[]];
}
