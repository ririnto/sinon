#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { readFileSync } from "node:fs";
import { join } from "node:path";
import type { Finding, HarnessCheckRule } from "./rules/harness-check-rule";
import type { HarnessManifest } from "./core/manifest";
import { createRuleContext } from "./core/rule-context";
import { logger } from "./logger";
import { renderFindings } from "./reporter";
import { agentFrontmatterRule } from "./rules/text/agent-frontmatter";
import { ciHookCommandParityRule } from "./rules/text/ci-hook-command-parity";
import { directoryPresenceRule } from "./rules/fs/directory-presence";
import { docContentRule } from "./rules/text/doc-content";
import { docHeadingsRule } from "./rules/text/doc-headings";
import { earlyReturnRule } from "./rules/ast/early-return";
import { emptyCatchBlockRule } from "./rules/ast/empty-catch-block";
import { emptyDirectoryPlaceholdersRule } from "./rules/fs/empty-directory-placeholders";
import { envShebangUsageRule } from "./rules/text/env-shebang-usage";
import { filePresenceRule } from "./rules/fs/file-presence";
import { greaterThanComparisonRule } from "./rules/ast/greater-than-comparison";
import { hookCommandRule } from "./rules/text/hook-command";
import { hookExecutableRule } from "./rules/text/hook-executable";
import { hookGeneratedMarkerRule } from "./rules/text/hook-generated-marker";
import { hookShebangRule } from "./rules/text/hook-shebang";
import { hookStageRule } from "./rules/text/hook-stage";
import { ifStatementBracesRule } from "./rules/ast/if-statement-braces";
import { implicitLambdaItRule } from "./rules/ast/implicit-lambda-it";
import { importOverFqnRule } from "./rules/ast/import-over-fqn";
import { kotlinTopLevelDeclarationCountRule } from "./rules/ast/kotlin-top-level-declaration-count";
import { leafFunctionBlankLinesRule } from "./rules/ast/leaf-function-blank-lines";
import { mutableCollectionRule } from "./rules/ast/mutable-collection";
import { publicDeclarationDocCommentRule } from "./rules/ast/public-declaration-doc-comment";
import { scaffoldLeaksRule } from "./rules/text/scaffold-leaks";
import { shebangEncodingMarkerRule } from "./rules/text/shebang-encoding-marker";
import { silentCatchRule } from "./rules/ast/silent-catch";
import { skillFrontmatterRule } from "./rules/text/skill-frontmatter";
import { symlinkSafetyRule } from "./rules/fs/symlink-safety";
import { templateGroupsRule } from "./rules/text/template-groups";
import { uncheckedTasksRule } from "./rules/text/unchecked-tasks";
import { unstructuredLoggingRule } from "./rules/ast/unstructured-logging";
import { wildcardImportRule } from "./rules/ast/wildcard-import";

const root = process.cwd();
export const MANIFEST_PATH = join("docs", "harness", "manifest.json");

function createHarnessChecks(): Record<string, HarnessCheckRule> {
  return {
    FILE_PRESENCE: filePresenceRule,
    DIRECTORY_PRESENCE: directoryPresenceRule,
    EMPTY_DIRECTORY_PLACEHOLDERS: emptyDirectoryPlaceholdersRule,
    TEMPLATE_GROUPS: templateGroupsRule,
    DOC_HEADINGS: docHeadingsRule,
    DOC_CONTENT: docContentRule,
    AGENT_FRONTMATTER: agentFrontmatterRule,
    SKILL_FRONTMATTER: skillFrontmatterRule,
    SCAFFOLD_LEAKS: scaffoldLeaksRule,
    HOOK_SHEBANG: hookShebangRule,
    HOOK_EXECUTABLE: hookExecutableRule,
    HOOK_GENERATED_MARKER: hookGeneratedMarkerRule,
    HOOK_STAGE: hookStageRule,
    HOOK_COMMAND: hookCommandRule,
    CI_HOOK_COMMAND_PARITY: ciHookCommandParityRule,
    ENV_SHEBANG_USAGE: envShebangUsageRule,
    SHEBANG_ENCODING_MARKER: shebangEncodingMarkerRule,
    UNCHECKED_TASKS: uncheckedTasksRule,
    SYMLINK_SAFETY: symlinkSafetyRule,
    IMPLICIT_LAMBDA_IT: implicitLambdaItRule,
    KOTLIN_TOP_LEVEL_DECLARATION_COUNT: kotlinTopLevelDeclarationCountRule,
    IMPORT_OVER_FQN: importOverFqnRule,
    GREATER_THAN_COMPARISON: greaterThanComparisonRule,
    LEAF_FUNCTION_BLANK_LINES: leafFunctionBlankLinesRule,
    EARLY_RETURN: earlyReturnRule,
    SILENT_CATCH: silentCatchRule,
    MUTABLE_COLLECTION: mutableCollectionRule,
    UNSTRUCTURED_LOGGING: unstructuredLoggingRule,
    WILDCARD_IMPORT: wildcardImportRule,
    EMPTY_CATCH_BLOCK: emptyCatchBlockRule,
    IF_STATEMENT_BRACES: ifStatementBracesRule,
    PUBLIC_DECLARATION_DOC_COMMENT: publicDeclarationDocCommentRule,
  } as const;
}

/**
 * All harness check rules as singleton instances.
 */
export const HARNESS_CHECKS: readonly HarnessCheckRule[] =
  Object.values(createHarnessChecks());

async function main(): Promise<void> {
  const manifest: HarnessManifest = JSON.parse(
    readFileSync(join(root, MANIFEST_PATH), "utf8"),
  );
  const executionContext = createRuleContext(root, manifest);
  const findings: Finding[] = HARNESS_CHECKS
    .filter((rule) => rule.applies(executionContext))
    .flatMap((rule) => rule.validate(executionContext));
  renderFindings(root, findings).forEach((line) => logger.log(line));
  process.exit(findings.some((f) => f.severity === "ERROR") ? 1 : 0);
}

if (import.meta.main) {
  await main();
}
