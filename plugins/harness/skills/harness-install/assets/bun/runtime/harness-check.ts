#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import type { HarnessCheckRule, Finding, HarnessManifest, RuleContext } from "./harness-check-rule";
import { RequireFilesExistRule } from "./rules/require-files-exist";
import { RequireDirectoriesExistRule } from "./rules/require-directories-exist";
import { RequireKeepfileInEmptyDirectoriesRule } from "./rules/require-keepfile-in-empty-directories";
import { RequireTemplateGroupsRule } from "./rules/require-template-groups";
import { RequireDocHeadingsRule } from "./rules/require-doc-headings";
import { RequireDocContentRule } from "./rules/require-doc-content";
import { RequireAgentFrontmatterRule } from "./rules/require-agent-frontmatter";
import { RequireSkillFrontmatterRule } from "./rules/require-skill-frontmatter";
import { ForbidScaffoldLeaksRule } from "./rules/forbid-scaffold-leaks";
import { RequireHookShebangRule } from "./rules/require-hook-shebang";
import { RequireHookExecutableRule } from "./rules/require-hook-executable";
import { RequireHookGeneratedMarkerRule } from "./rules/require-hook-generated-marker";
import { RequireHookStageRule } from "./rules/require-hook-stage";
import { RequireHookCommandRule } from "./rules/require-hook-command";
import { RequireCiCommandMatchesHookRule } from "./rules/require-ci-command-matches-hook";
import { RequireEnvShebangUnderRule } from "./rules/require-env-shebang-under";
import { ForbidUncheckedTasksUnderRule } from "./rules/forbid-unchecked-tasks-under";
import { ForbidUnsafeSymlinksRule } from "./rules/forbid-unsafe-symlinks";
import { ForbidImplicitLambdaItRule } from "./rules/forbid-implicit-lambda-it";
import { RequireSingleTopLevelKotlinDeclarationRule } from "./rules/require-single-top-level-kotlin-declaration";
import { ForbidGreaterThanComparisonRule } from "./rules/forbid-greater-than-comparison";
import { ForbidBlankLineInLeafFunctionRule } from "./rules/forbid-blank-line-in-leaf-function";
import { ForbidEarlyReturnRule } from "./rules/forbid-early-return";
import { ForbidSilentCatchRule } from "./rules/forbid-silent-catch";
import { ForbidMutableCollectionRule } from "./rules/forbid-mutable-collection";
import { ForbidUnstructuredLoggingRule } from "./rules/forbid-unstructured-logging";
import { ForbidWildcardImportRule } from "./rules/forbid-wildcard-import";
import { ForbidEmptyCatchBlockRule } from "./rules/forbid-empty-catch-block";
import { RequireBracesOnIfRule } from "./rules/require-braces-on-if";
import { RequireDocCommentOnPublicDeclarationRule } from "./rules/require-doc-comment-on-public-declaration";

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
    if (isSymlink(path) && allowedRootContractTarget(path) === null) {
      return false;
    }
    return statSync(pathOf(path)).isFile();
  } catch {
    return false;
  }
}

function isDirectory(path: string): boolean {
  try {
    if (isSymlink(path)) {
      return false;
    }
    return statSync(pathOf(path)).isDirectory();
  } catch {
    return false;
  }
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
  if (path !== "AGENTS.md" && path !== "CLAUDE.md") {
    return null;
  }
  try {
    const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
    if (readlinkSync(pathOf(path)) !== expected) {
      return null;
    }
    return !lstatSync(pathOf(expected)).isSymbolicLink() && statSync(pathOf(expected)).isFile()
      ? pathOf(expected)
      : null;
  } catch {
    return null;
  }
}

function readStringArray(value: unknown): readonly string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function readJsonObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function severityOf(manifest: HarnessManifest, category: string): "ERROR" | "WARN" | "INFO" {
  const section = readJsonObject(manifest[category]);
  const sev = section.severity;
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
  const parameters = readJsonObject(manifest[category]);
  const sourceRootsPerStack = readJsonObject(parameters.sourceRootsPerStack);
  const extensionsPerStack = readJsonObject(parameters.extensionsPerStack);

  const sourceDirs = readStringArray(sourceRootsPerStack[category]);
  const extensions = new Set(readStringArray(extensionsPerStack[category]));

  if (sourceDirs.length === 0 || extensions.size === 0) {
    return [];
  }

  const collected = new Set<string>();

  function* walkDirGen(dirPath: string): Generator<string> {
    const skip = (name: string) => name === "node_modules" || name === "build";

    if (isSymlink(dirPath)) {
      return;
    }
    if (!isDirectory(dirPath)) {
      return;
    }

    try {
      const entries = readdirSync(pathOf(dirPath));
      for (const entry of entries) {
        if (skip(entry)) {
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
        const glob = new Bun.Glob(sourceDir);
        for (const match of glob.scanSync(".")) {
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
  { category: "requireFilesExist", rule: new RequireFilesExistRule(ruleContext) },
  { category: "requireDirectoriesExist", rule: new RequireDirectoriesExistRule(ruleContext) },
  { category: "requireKeepfileInEmptyDirectories", rule: new RequireKeepfileInEmptyDirectoriesRule(ruleContext) },
  { category: "requireTemplateGroups", rule: new RequireTemplateGroupsRule(ruleContext) },
  { category: "requireDocHeadings", rule: new RequireDocHeadingsRule(ruleContext) },
  { category: "requireDocContent", rule: new RequireDocContentRule(ruleContext) },
  { category: "requireAgentFrontmatter", rule: new RequireAgentFrontmatterRule(ruleContext) },
  { category: "requireSkillFrontmatter", rule: new RequireSkillFrontmatterRule(ruleContext) },
  { category: "forbidScaffoldLeaks", rule: new ForbidScaffoldLeaksRule(ruleContext) },
  { category: "requireHookShebang", rule: new RequireHookShebangRule(ruleContext) },
  { category: "requireHookExecutable", rule: new RequireHookExecutableRule(ruleContext) },
  { category: "requireHookGeneratedMarker", rule: new RequireHookGeneratedMarkerRule(ruleContext) },
  { category: "requireHookStage", rule: new RequireHookStageRule(ruleContext) },
  { category: "requireHookCommand", rule: new RequireHookCommandRule(ruleContext) },
  { category: "requireCiCommandMatchesHook", rule: new RequireCiCommandMatchesHookRule(ruleContext) },
  { category: "requireEnvShebangUnder", rule: new RequireEnvShebangUnderRule(ruleContext) },
  { category: "forbidUncheckedTasksUnder", rule: new ForbidUncheckedTasksUnderRule(ruleContext) },
  { category: "forbidUnsafeSymlinks", rule: new ForbidUnsafeSymlinksRule(ruleContext) },
  { category: "forbidImplicitLambdaIt", rule: new ForbidImplicitLambdaItRule(ruleContext) },
  { category: "requireSingleTopLevelKotlinDeclaration", rule: new RequireSingleTopLevelKotlinDeclarationRule(ruleContext) },
  { category: "forbidGreaterThanComparison", rule: new ForbidGreaterThanComparisonRule(ruleContext) },
  { category: "forbidBlankLineInLeafFunction", rule: new ForbidBlankLineInLeafFunctionRule(ruleContext) },
  { category: "forbidEarlyReturn", rule: new ForbidEarlyReturnRule(ruleContext) },
  { category: "forbidSilentCatch", rule: new ForbidSilentCatchRule(ruleContext) },
  { category: "forbidMutableCollection", rule: new ForbidMutableCollectionRule(ruleContext) },
  { category: "forbidUnstructuredLogging", rule: new ForbidUnstructuredLoggingRule(ruleContext) },
  { category: "forbidWildcardImport", rule: new ForbidWildcardImportRule(ruleContext) },
  { category: "forbidEmptyCatchBlock", rule: new ForbidEmptyCatchBlockRule(ruleContext) },
  { category: "requireBracesOnIf", rule: new RequireBracesOnIfRule(ruleContext) },
  { category: "requireDocCommentOnPublicDeclaration", rule: new RequireDocCommentOnPublicDeclarationRule(ruleContext) },
] as const;

async function main(): Promise<void> {
  const manifestPath = join(root, "harness.json");
  let manifest: HarnessManifest = {};
  try {
    const text = readFileSync(manifestPath, "utf8");
    manifest = JSON.parse(text);
  } catch {
    console.error("failed to read harness.json");
    process.exit(1);
  }

  const findings: Finding[] = [];
  for (const { rule } of HARNESS_CHECKS) {
    if (rule.applies(manifest)) {
      findings.push(...rule.validate(root, manifest));
    }
  }

  if (findings.length === 0) {
    console.log("OK");
    process.exit(0);
  }

  const grouped = new Map<"ERROR" | "WARN" | "INFO", Finding[]>();
  for (const finding of findings) {
    if (!grouped.has(finding.severity)) {
      grouped.set(finding.severity, []);
    }
    grouped.get(finding.severity)!.push(finding);
  }

  const severityOrder = ["ERROR", "WARN", "INFO"] as const;
  for (const severity of severityOrder) {
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
