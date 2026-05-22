#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import type { HarnessCheckRule, Finding, HarnessManifest, RuleContext } from "./harness-check-rule";
import { requireFilesExistRule } from "./rules/require-files-exist";
import { requireDirectoriesExistRule } from "./rules/require-directories-exist";
import { requireKeepfileInEmptyDirectoriesRule } from "./rules/require-keepfile-in-empty-directories";
import { requireTemplateGroupsRule } from "./rules/require-template-groups";
import { requireDocHeadingsRule } from "./rules/require-doc-headings";
import { requireDocContentRule } from "./rules/require-doc-content";
import { requireAgentFrontmatterRule } from "./rules/require-agent-frontmatter";
import { requireSkillFrontmatterRule } from "./rules/require-skill-frontmatter";
import { forbidScaffoldLeaksRule } from "./rules/forbid-scaffold-leaks";
import { requireHookShebangRule } from "./rules/require-hook-shebang";
import { requireHookExecutableRule } from "./rules/require-hook-executable";
import { requireHookGeneratedMarkerRule } from "./rules/require-hook-generated-marker";
import { requireHookStageRule } from "./rules/require-hook-stage";
import { requireHookCommandRule } from "./rules/require-hook-command";
import { requireCiCommandMatchesHookRule } from "./rules/require-ci-command-matches-hook";
import { requireEnvShebangUnderRule } from "./rules/require-env-shebang-under";
import { forbidUncheckedTasksUnderRule } from "./rules/forbid-unchecked-tasks-under";
import { forbidUnsafeSymlinksRule } from "./rules/forbid-unsafe-symlinks";
import { requireImportOverFqnRule } from "./rules/require-import-over-fqn";
import { forbidImplicitLambdaItRule } from "./rules/forbid-implicit-lambda-it";
import { requireSingleTopLevelKotlinDeclarationRule } from "./rules/require-single-top-level-kotlin-declaration";
import { forbidGreaterThanComparisonRule } from "./rules/forbid-greater-than-comparison";
import { forbidBlankLineInLeafFunctionRule } from "./rules/forbid-blank-line-in-leaf-function";
import { forbidEarlyReturnRule } from "./rules/forbid-early-return";
import { forbidSilentCatchRule } from "./rules/forbid-silent-catch";
import { forbidMutableCollectionRule } from "./rules/forbid-mutable-collection";
import { forbidUnstructuredLoggingRule } from "./rules/forbid-unstructured-logging";
import { forbidWildcardImportRule } from "./rules/forbid-wildcard-import";
import { forbidEmptyCatchBlockRule } from "./rules/forbid-empty-catch-block";
import { requireBracesOnIfRule } from "./rules/require-braces-on-if";
import { requireDocCommentOnPublicDeclarationRule } from "./rules/require-doc-comment-on-public-declaration";

const root = process.cwd();

function pathOf(path: string): string {
  return join(root, path);
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
  } catch {
  }
  return false;
}

function isDirectory(path: string): boolean {
  try {
    if (!isSymlink(path)) {
      return statSync(pathOf(path)).isDirectory();
    }
  } catch {
  }
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
        if (!lstatSync(pathOf(expected)).isSymbolicLink() && statSync(pathOf(expected)).isFile()) {
          return pathOf(expected);
        }
      }
    } catch {
    }
  }
  return null;
}

function readStringArray(value: unknown): readonly string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function readJsonObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function severityOf(manifest: HarnessManifest, category: string): "ERROR" | "WARN" | "INFO" {
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
 * @param category Stack category (e.g., "typescript").
 * @return Sorted unique list of source file paths relative to root.
 */
function stackSources(manifest: HarnessManifest, category: string): readonly string[] {
  const collected = new Set<string>();
  const parameters = readJsonObject(manifest[category]);

  const sourceDirs = readStringArray(readJsonObject(parameters.sourceRootsPerStack)[category]);
  const extensions = new Set(readStringArray(readJsonObject(parameters.extensionsPerStack)[category]));

  if (!(sourceDirs.length === 0 || extensions.size === 0)) {
    function* walkDirGen(dirPath: string): Generator<string> {
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
    for (const sourceDir of sourceDirs) {
      if (sourceDir.includes("*")) {
        try {
          for (const match of new Bun.Glob(sourceDir).scanSync(".")) {
            const normPath = `${sourceDir.split("/")[0]}/${match}`;
            const ext = normPath.slice(normPath.lastIndexOf(".") + 1);
            if (extensions.has(ext)) {
              collected.add(normPath);
            }
          }
        } catch {
          continue;
        }
      } else {
        for (const file of walkDirGen(sourceDir)) {
          collected.add(file);
        }
      }
    }
  }
  return [...collected].sort();
}

function walkDirectory(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path)) {
    findings.push({
      severity: "ERROR",
      category: "forbidUnsafeSymlinks",
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
  const files = readdirSync(pathOf(path))
    .flatMap((entry) => {
      const child = `${path}/${entry}`;
      const full = pathOf(child);
      if (lstatSync(full).isSymbolicLink()) {
        findings.push({
          severity: "ERROR",
          category: "forbidUnsafeSymlinks",
          message: `symlink scan entry is not allowed: ${child}`,
        });
        return [];
      }
      return statSync(full).isDirectory() ? walkDirectory(child)[0] : [child];
    });
  return [files, findings];
}

function collectFilesUnder(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path) && allowedRootContractTarget(path) === null) {
    findings.push({
      severity: "ERROR",
      category: "forbidUnsafeSymlinks",
      message: `symlink path is not allowed: ${path}`,
    });
  }
  if (findings.length > 0) {
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
export const HARNESS_CHECKS: readonly { category: string; rule: HarnessCheckRule }[] = [
  { category: "requireFilesExist", rule: requireFilesExistRule(ruleContext) },
  { category: "requireDirectoriesExist", rule: requireDirectoriesExistRule(ruleContext) },
  { category: "requireKeepfileInEmptyDirectories", rule: requireKeepfileInEmptyDirectoriesRule(ruleContext) },
  { category: "requireTemplateGroups", rule: requireTemplateGroupsRule(ruleContext) },
  { category: "requireDocHeadings", rule: requireDocHeadingsRule(ruleContext) },
  { category: "requireDocContent", rule: requireDocContentRule(ruleContext) },
  { category: "requireAgentFrontmatter", rule: requireAgentFrontmatterRule(ruleContext) },
  { category: "requireSkillFrontmatter", rule: requireSkillFrontmatterRule(ruleContext) },
  { category: "forbidScaffoldLeaks", rule: forbidScaffoldLeaksRule(ruleContext) },
  { category: "requireHookShebang", rule: requireHookShebangRule(ruleContext) },
  { category: "requireHookExecutable", rule: requireHookExecutableRule(ruleContext) },
  { category: "requireHookGeneratedMarker", rule: requireHookGeneratedMarkerRule(ruleContext) },
  { category: "requireHookStage", rule: requireHookStageRule(ruleContext) },
  { category: "requireHookCommand", rule: requireHookCommandRule(ruleContext) },
  { category: "requireCiCommandMatchesHook", rule: requireCiCommandMatchesHookRule(ruleContext) },
  { category: "requireEnvShebangUnder", rule: requireEnvShebangUnderRule(ruleContext) },
  { category: "forbidUncheckedTasksUnder", rule: forbidUncheckedTasksUnderRule(ruleContext) },
  { category: "forbidUnsafeSymlinks", rule: forbidUnsafeSymlinksRule(ruleContext) },
  { category: "requireImportOverFqn", rule: requireImportOverFqnRule(ruleContext) },
  { category: "forbidImplicitLambdaIt", rule: forbidImplicitLambdaItRule(ruleContext) },
  { category: "requireSingleTopLevelKotlinDeclaration", rule: requireSingleTopLevelKotlinDeclarationRule(ruleContext) },
  { category: "forbidGreaterThanComparison", rule: forbidGreaterThanComparisonRule(ruleContext) },
  { category: "forbidBlankLineInLeafFunction", rule: forbidBlankLineInLeafFunctionRule(ruleContext) },
  { category: "forbidEarlyReturn", rule: forbidEarlyReturnRule(ruleContext) },
  { category: "forbidSilentCatch", rule: forbidSilentCatchRule(ruleContext) },
  { category: "forbidMutableCollection", rule: forbidMutableCollectionRule(ruleContext) },
  { category: "forbidUnstructuredLogging", rule: forbidUnstructuredLoggingRule(ruleContext) },
  { category: "forbidWildcardImport", rule: forbidWildcardImportRule(ruleContext) },
  { category: "forbidEmptyCatchBlock", rule: forbidEmptyCatchBlockRule(ruleContext) },
  { category: "requireBracesOnIf", rule: requireBracesOnIfRule(ruleContext) },
  { category: "requireDocCommentOnPublicDeclaration", rule: requireDocCommentOnPublicDeclarationRule(ruleContext) },
] as const;

async function main(): Promise<void> {
  let manifest: HarnessManifest;
  try {
    manifest = JSON.parse(readFileSync(join(root, "harness.json"), "utf8"));
  } catch {
    console.error("failed to read harness.json");
    process.exit(1);
  }

  const findings: Finding[] = HARNESS_CHECKS
    .filter(({ rule }) => rule.applies(manifest))
    .flatMap(({ rule }) => rule.validate(root, manifest));

  if (findings.length === 0) {
    console.log("OK");
    process.exit(0);
  }

  const grouped = new Map<"ERROR" | "WARN" | "INFO", Finding[]>();
  findings.forEach((finding) => {
    if (!grouped.has(finding.severity)) {
      grouped.set(finding.severity, []);
    }
    grouped.get(finding.severity)!.push(finding);
  });

  for (const severity of ["ERROR", "WARN", "INFO"] as const) {
    const items = grouped.get(severity);
    if (items && items.length > 0) {
      console.log(`${severity}: ${items.length}`);
      for (const item of items) {
        console.log(`  [${item.category}] ${item.message}`);
      }
    }
  }

  process.exit(findings.some((f) => f.severity === "ERROR") ? 1 : 0);
}

main().catch((err) => {
  console.error(err);
  process.exit(2);
});
