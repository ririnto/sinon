#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { createRuleContext } from "./core/rule-context";
import { logger } from "./logger";
import { renderFindings } from "./reporter";
import { runOxlint } from "./oxlint/oxlint-adapter";
import { OXLINT_CATEGORIES } from "./oxlint/oxlint-code-map";
import { directoryPresenceRule } from "./rules/fs/directory-presence";
import { emptyDirectoryPlaceholdersRule } from "./rules/fs/empty-directory-placeholders";
import { filePresenceRule } from "./rules/fs/file-presence";
import { symlinkSafetyRule } from "./rules/fs/symlink-safety";
import type { HarnessCheckRule } from "./rules/harness-check-rule";
import { agentFrontmatterRule } from "./rules/text/agent-frontmatter";
import { ciHookCommandParityRule } from "./rules/text/ci-hook-command-parity";
import { docContentRule } from "./rules/text/doc-content";
import { docHeadingsRule } from "./rules/text/doc-headings";
import { envShebangUsageRule } from "./rules/text/env-shebang-usage";
import { hookCommandRule } from "./rules/text/hook-command";
import { hookExecutableRule } from "./rules/text/hook-executable";
import { hookGeneratedMarkerRule } from "./rules/text/hook-generated-marker";
import { hookShebangRule } from "./rules/text/hook-shebang";
import { hookStageRule } from "./rules/text/hook-stage";
import { scaffoldLeaksRule } from "./rules/text/scaffold-leaks";
import { shebangEncodingMarkerRule } from "./rules/text/shebang-encoding-marker";
import { skillFrontmatterRule } from "./rules/text/skill-frontmatter";
import { templateGroupsRule } from "./rules/text/template-groups";
import { uncheckedTasksRule } from "./rules/text/unchecked-tasks";

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
    } as const;
}

/**
 * All harness check rules as singleton instances.
 */
export const HARNESS_CHECKS: readonly HarnessCheckRule[] = Object.values(createHarnessChecks());

function main(): void {
    const context = createRuleContext(root, JSON.parse(readFileSync(join(root, MANIFEST_PATH), "utf8")));
    const manifest = context.manifest.raw;
    if (manifest === null || Object.keys(manifest).length === 0) {
        throw new Error(`manifest is empty or malformed: ${MANIFEST_PATH}`);
    }
    const knownCategories = new Set([...HARNESS_CHECKS.map((c) => c.category), ...OXLINT_CATEGORIES]);
    const knownKeys = new Set([
        "name",
        "description",
        "$schema",
        "seedFiles",
        "generatedArtifacts",
        "harnessEvolution",
        "teamPatterns",
    ]);
    const oxlintFindings = runOxlint(context);
    const allRuleFindings = [
        ...HARNESS_CHECKS.filter((rule) => rule.applies(context))
            .flatMap((rule) => rule.validate(context)),
        ...oxlintFindings,
    ];
    const findings = [
        ...Object.keys(manifest)
            .filter((key) => !knownCategories.has(key) && !knownKeys.has(key))
            .map((key) => ({
                severity: "WARN" as const,
                category: "manifestSchema",
                message: `unknown manifest key: ${key}`,
            })),
        ...Array.from(
            new Map(
                allRuleFindings.map((finding) => [
                    `${finding.severity}|${finding.category}|${finding.message}|${finding.file ?? ""}|${finding.startLine ?? ""}|${finding.startColumn ?? ""}`,
                    finding,
                ]),
            ).values(),
        ),
    ];
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
