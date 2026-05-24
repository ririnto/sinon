#!/usr/bin/env bun
import {
	lstatSync,
	readdirSync,
	readFileSync,
	readlinkSync,
	statSync,
} from "node:fs";
import { join, resolve } from "node:path";
import type {
	Finding,
	HarnessCheckRule,
	HarnessManifest,
	RuleContext,
} from "./harness-check-rule";
import { logger } from "./logger";
import { leafFunctionBlankLinesRule } from "./rules/leaf-function-blank-lines";
import { earlyReturnRule } from "./rules/early-return";
import { emptyCatchBlockRule } from "./rules/empty-catch-block";
import { greaterThanComparisonRule } from "./rules/greater-than-comparison";
import { implicitLambdaItRule } from "./rules/implicit-lambda-it";
import { mutableCollectionRule } from "./rules/mutable-collection";
import { scaffoldLeaksRule } from "./rules/scaffold-leaks";
import { silentCatchRule } from "./rules/silent-catch";
import { uncheckedTasksRule } from "./rules/unchecked-tasks";
import { symlinkSafetyRule } from "./rules/symlink-safety";
import { unstructuredLoggingRule } from "./rules/unstructured-logging";
import { wildcardImportRule } from "./rules/wildcard-import";
import { agentFrontmatterRule } from "./rules/agent-frontmatter";
import { ifStatementBracesRule } from "./rules/if-statement-braces";
import { ciHookCommandParityRule } from "./rules/ci-hook-command-parity";
import { directoryPresenceRule } from "./rules/directory-presence";
import { publicDeclarationDocCommentRule } from "./rules/public-declaration-doc-comment";
import { docContentRule } from "./rules/doc-content";
import { docHeadingsRule } from "./rules/doc-headings";
import { envShebangUsageRule } from "./rules/env-shebang-usage";
import { filePresenceRule } from "./rules/file-presence";
import { hookCommandRule } from "./rules/hook-command";
import { hookExecutableRule } from "./rules/hook-executable";
import { hookGeneratedMarkerRule } from "./rules/hook-generated-marker";
import { hookShebangRule } from "./rules/hook-shebang";
import { hookStageRule } from "./rules/hook-stage";
import { importOverFqnRule } from "./rules/import-over-fqn";
import { emptyDirectoryPlaceholdersRule } from "./rules/empty-directory-placeholders";
import { kotlinTopLevelDeclarationCountRule } from "./rules/kotlin-top-level-declaration-count";
import { skillFrontmatterRule } from "./rules/skill-frontmatter";
import { templateGroupsRule } from "./rules/template-groups";

const root = process.cwd();

function pathOf(path: string): string {
	return join(root, path);
}

function isWithinRoot(path: string): boolean {
	const resolvedPath = resolve(pathOf(path));
	const resolvedRoot = resolve(pathOf("."));
	return resolvedPath === resolvedRoot || resolvedPath.startsWith(`${resolvedRoot}/`);
}

function read(path: string): string {
	try {
		const target = allowedRootContractTarget(path);
		return readFileSync(target ?? pathOf(path), "utf8");
	} catch {
		return "";
	}
}

function firstLine(path: string): string {
	return read(path).split(/\r?\n/, 1)[0] ?? "";
}

function isFile(path: string): boolean {
	try {
		if (!(isSymlink(path) && allowedRootContractTarget(path) === null)) {
			return statSync(pathOf(path)).isFile();
		}
	} catch {}
	return false;
}

function isDirectory(path: string): boolean {
	try {
		if (!isSymlink(path)) {
			return statSync(pathOf(path)).isDirectory();
		}
	} catch {}
	return false;
}

function isExecutablePath(path: string): boolean {
	try {
		const target = allowedRootContractTarget(path);
		return (statSync(target ?? pathOf(path)).mode & 0o100) !== 0;
	} catch {
		return false;
	}
}

function isSymlink(path: string): boolean {
	try {
		return lstatSync(pathOf(path)).isSymbolicLink();
	} catch {
		return false;
	}
}

function allowedRootContractTarget(path: string): string | null {
	if (path === "AGENTS.md" || path === "CLAUDE.md") {
		try {
			const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
			if (readlinkSync(pathOf(path)) === expected) {
				if (
					!lstatSync(pathOf(expected)).isSymbolicLink() &&
					statSync(pathOf(expected)).isFile()
				) {
					return pathOf(expected);
				}
			}
		} catch {}
	}
	return null;
}

function readStringArray(value: unknown): readonly string[] {
	return Array.isArray(value)
		? value.filter((item): item is string => typeof item === "string")
		: [];
}

function readJsonObject(value: unknown): Record<string, unknown> {
	return typeof value === "object" && value !== null
		? (value as Record<string, unknown>)
		: {};
}

function severityOf(
	manifest: HarnessManifest,
	category: string,
): "ERROR" | "WARN" | "INFO" {
	const sev = readJsonObject(manifest[category]).severity;
	return sev === "ERROR" || sev === "WARN" || sev === "INFO" ? sev : "ERROR";
}

/**
 * Collect TypeScript/JavaScript source files matching stack configuration.
 *
 * Expands glob entries via Bun.Glob, treats literal paths as directories,
 * filters by configured extensions, and skips node_modules and build directories.
 *
 * @param manifest Harness manifest with sourceRootsPerStack and extensionsPerStack.
 * @param category Harness check category.
 * @param stack Stack source key (e.g., "typescript").
 * @return Sorted unique list of source file paths relative to root.
 */
function stackSources(
	manifest: HarnessManifest,
	category: string,
	stack: string,
): readonly string[] {
	const collected = new Set<string>();
	const parameters = readJsonObject(readJsonObject(manifest[category]).parameters);

	const sourceDirs = readStringArray(
		readJsonObject(parameters.sourceRootsPerStack)[stack],
	);
	const extensions = new Set(
		readStringArray(readJsonObject(parameters.extensionsPerStack)[stack]),
	);

	if (!(sourceDirs.length === 0 || extensions.size === 0)) {
		function* walkDirGen(dirPath: string): Generator<string> {
			if (!isWithinRoot(dirPath)) {
				return;
			}
			if (isSymlink(dirPath)) {
				return;
			}
			if (!isDirectory(dirPath)) {
				return;
			}
			try {
				for (const entry of readdirSync(pathOf(dirPath))) {
					if (entry === "node_modules" || entry === "build") {
						continue;
					}
					const child = `${dirPath}/${entry}`;
					const full = pathOf(child);
					if (lstatSync(full).isSymbolicLink()) {
						continue;
					}
					const stat = statSync(full);
					if (stat.isDirectory()) {
						yield* walkDirGen(child);
					} else if (stat.isFile()) {
						const ext = child.slice(child.lastIndexOf(".") + 1);
						if (extensions.has(ext)) {
							yield child;
						}
					}
				}
			} catch {
				return;
			}
		}
		const collectGlobMatch = (match: string): void => {
			if (isDirectory(match)) {
				for (const file of walkDirGen(match)) {
					collected.add(file);
				}
			} else {
				const ext = match.slice(match.lastIndexOf(".") + 1);
				if (isWithinRoot(match) && extensions.has(ext) && isFile(match)) {
					collected.add(match);
				}
			}
		};
		for (const sourceDir of sourceDirs) {
			if (sourceDir.includes("*")) {
				try {
					for (const match of new Bun.Glob(sourceDir).scanSync(".")) {
						collectGlobMatch(match);
					}
					for (const match of new Bun.Glob(`${sourceDir}/**/*`).scanSync(".")) {
						collectGlobMatch(match);
					}
				} catch {}
			} else {
				for (const file of walkDirGen(sourceDir)) {
					collected.add(file);
				}
			}
		}
	}
	return [...collected].sort();
}

function walkDirectory(
	path: string,
): readonly [readonly string[], readonly Finding[]] {
	const findings: Finding[] = [];
	if (isSymlink(path)) {
		findings.push({
			severity: "ERROR",
			category: "symlinkSafety",
			message: `symlink scan root is not allowed: ${path}`,
		});
		return [[], findings];
	}
	if (isFile(path)) {
		return [[path], findings];
	}
	if (!isDirectory(path)) {
		return [[], findings];
	}
	const files = readdirSync(pathOf(path)).flatMap((entry: string) => {
		const child = `${path}/${entry}`;
		const full = pathOf(child);
		if (lstatSync(full).isSymbolicLink()) {
			findings.push({
				severity: "ERROR",
				category: "symlinkSafety",
				message: `symlink scan entry is not allowed: ${child}`,
			});
			return [];
		}
		return statSync(full).isDirectory() ? walkDirectory(child)[0] : [child];
	});
	return [files, findings];
}

function collectFilesUnder(
	path: string,
): readonly [readonly string[], readonly Finding[]] {
	const findings: Finding[] = [];
	if (!isWithinRoot(path)) {
		findings.push({
			severity: "ERROR",
			category: "symlinkSafety",
			message: `source path escapes repository root: ${path}`,
		});
	}
	if (isSymlink(path) && allowedRootContractTarget(path) === null) {
		findings.push({
			severity: "ERROR",
			category: "symlinkSafety",
			message: `symlink path is not allowed: ${path}`,
		});
	}
	if (0 < findings.length) {
		return [[], findings];
	}
	return isFile(path) ? [[path], findings] : walkDirectory(path);
}

const ruleContext: RuleContext = {
	pathOf,
	read,
	firstLine,
	isFile,
	isDirectory,
	isExecutablePath,
	isSymlink,
	allowedRootContractTarget,
	readStringArray,
	readJsonObject,
	severityOf,
	stackSources,
	walkDirectory,
	collectFilesUnder,
};

/**
 * All harness check rules instantiated with shared context.
 */
export const HARNESS_CHECKS: readonly HarnessCheckRule[] = [
	filePresenceRule(ruleContext),
	directoryPresenceRule(ruleContext),
	emptyDirectoryPlaceholdersRule(ruleContext),
	templateGroupsRule(ruleContext),
	docHeadingsRule(ruleContext),
	docContentRule(ruleContext),
	agentFrontmatterRule(ruleContext),
	skillFrontmatterRule(ruleContext),
	scaffoldLeaksRule(ruleContext),
	hookShebangRule(ruleContext),
	hookExecutableRule(ruleContext),
	hookGeneratedMarkerRule(ruleContext),
	hookStageRule(ruleContext),
	hookCommandRule(ruleContext),
	ciHookCommandParityRule(ruleContext),
	envShebangUsageRule(ruleContext),
	uncheckedTasksRule(ruleContext),
	symlinkSafetyRule(ruleContext),
	importOverFqnRule(ruleContext),
	implicitLambdaItRule(ruleContext),
	kotlinTopLevelDeclarationCountRule(ruleContext),
	greaterThanComparisonRule(ruleContext),
	leafFunctionBlankLinesRule(ruleContext),
	earlyReturnRule(ruleContext),
	silentCatchRule(ruleContext),
	mutableCollectionRule(ruleContext),
	unstructuredLoggingRule(ruleContext),
	wildcardImportRule(ruleContext),
	emptyCatchBlockRule(ruleContext),
	ifStatementBracesRule(ruleContext),
	publicDeclarationDocCommentRule(ruleContext),
] as const;

async function main(): Promise<void> {
	let manifest: HarnessManifest;
	try {
		manifest = JSON.parse(readFileSync(join(root, "harness.json"), "utf8"));
	} catch {
		logger.error("failed to read harness.json");
		process.exit(1);
	}

	const findings: Finding[] = HARNESS_CHECKS.filter((rule) => rule.applies(manifest)).flatMap((rule) =>
		rule.validate(root, manifest),
	);

	if (findings.length === 0) {
		logger.log("OK");
		process.exit(0);
	}

	const grouped = new Map<"ERROR" | "WARN" | "INFO", Finding[]>();
	findings.forEach((finding) => {
		if (!grouped.has(finding.severity)) {
			grouped.set(finding.severity, []);
		}
		grouped.get(finding.severity)!.push(finding);
	});

	(["ERROR", "WARN", "INFO"] as const).forEach((severity) => {
		const items = grouped.get(severity);
		if (items && 0 < items.length) {
			logger.log(`${severity}: ${items.length}`);
			items.forEach((item) => {
				logger.log(`  [${item.category}] ${item.message}`);
			});
		}
	});

	process.exit(findings.some((f) => f.severity === "ERROR") ? 1 : 0);
}

if (import.meta.main) {
	main().catch((err) => {
		logger.error(err instanceof Error ? err.message : String(err));
		process.exit(2);
	});
}
