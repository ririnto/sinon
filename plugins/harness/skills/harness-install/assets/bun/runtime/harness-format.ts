#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { existsSync, mkdirSync, readFileSync, realpathSync, writeFileSync } from "node:fs";
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import type { HarnessManifest } from "./core/manifest";
import { createRuleContext, type RuleContext } from "./core/rule-context";
import { HARNESS_CHECKS, MANIFEST_PATH } from "./harness-check";
import { logger } from "./logger";
import { renderFindings } from "./reporter";
import type { Finding, FindingEdit } from "./rules/harness-check-rule";

const root = process.cwd();
const FORMAT_ALLOWLIST = new Set([
    "emptyDirectoryPlaceholders",
    "envShebangUsage",
    "hookGeneratedMarker",
    "hookShebang",
    "shebangEncodingMarker",
]);

type PreparedEdit = FindingEdit & {
    readonly relativeFile: string;
    readonly startOffset: number;
    readonly endOffset: number;
};

function relativeFileOf(file: string): string | null {
    const absoluteRoot = realpathSync(root);
    const absoluteFile = isAbsolute(file) ? resolve(file) : resolve(root, file);
    const relativeFile = relative(root, absoluteFile);
    if (relativeFile === "" || relativeFile.startsWith("..") || isAbsolute(relativeFile)) {
        return null;
    }
    const realTarget = realpathSync(existsSync(absoluteFile) ? absoluteFile : dirname(absoluteFile));
    const relativeTarget = relative(absoluteRoot, realTarget);
    if (relativeTarget === "" || relativeTarget.startsWith("..") || isAbsolute(relativeTarget)) {
        return null;
    }
    return relativeFile;
}

function absoluteFileOf(relativeFile: string): string {
    return resolve(root, relativeFile);
}

function isGeneratedArtifact(ctx: RuleContext, relativeFile: string, category: string): boolean {
    if (category === "emptyDirectoryPlaceholders") {
        return false;
    }
    const generatedArtifacts = ctx.readJsonObject(ctx.manifest.raw.generatedArtifacts);
    const generatedPath = typeof generatedArtifacts.path === "string" ? generatedArtifacts.path : "";
    if (generatedPath.length === 0) {
        return false;
    }
    const normalizedPath = generatedPath.endsWith("/") ? generatedPath : `${generatedPath}/`;
    const placeholder = typeof generatedArtifacts.placeholder === "string" ? generatedArtifacts.placeholder : "";
    return relativeFile !== placeholder && relativeFile.startsWith(normalizedPath);
}

function editableFile(ctx: RuleContext, relativeFile: string, category: string): boolean {
    if (isGeneratedArtifact(ctx, relativeFile, category)) {
        return false;
    }
    if (ctx.isSymlink(relativeFile) && ctx.allowedRootContractTarget(relativeFile) === null) {
        return false;
    }
    if (category === "emptyDirectoryPlaceholders" && relativeFile.endsWith("/.gitkeep")) {
        return true;
    }
    return ctx.isFile(relativeFile);
}

function lineStartsOf(text: string): readonly number[] {
    const starts = [0];
    for (let index = 0; index < text.length; index += 1) {
        if (text[index] === "\n") {
            starts.push(index + 1);
        }
    }
    return starts;
}

function lineLengthAt(text: string, lineStart: number): number {
    const newlineIndex = text.indexOf("\n", lineStart);
    const lineEnd = newlineIndex === -1 ? text.length : newlineIndex;
    return text.slice(lineStart, lineEnd).replace(/\r$/, "").length;
}

function offsetOf(text: string, lineStarts: readonly number[], line: number, column: number): number {
    if (line < 1 || line > lineStarts.length) {
        throw new Error(`invalid edit line ${line}`);
    }
    const lineStart = lineStarts[line - 1];
    const lineLength = lineLengthAt(text, lineStart);
    if (column < 1 || column > lineLength + 1) {
        throw new Error(`invalid edit column ${line}:${column}`);
    }
    return lineStart + column - 1;
}

function prepareEdit(text: string, edit: FindingEdit, relativeFile: string): PreparedEdit {
    const lineStarts = lineStartsOf(text);
    const startOffset = offsetOf(text, lineStarts, edit.startLine, edit.startColumn);
    const endOffset = offsetOf(text, lineStarts, edit.endLine, edit.endColumn);
    if (endOffset < startOffset) {
        throw new Error(`edit range is reversed: ${relativeFile}:${edit.startLine}:${edit.startColumn}`);
    }
    return { ...edit, relativeFile, startOffset, endOffset };
}

function editsForFinding(finding: Finding): readonly FindingEdit[] {
    if (finding.fix?.edits && finding.fix.edits.length > 0) {
        return finding.fix.edits;
    }
    if (finding.category === "emptyDirectoryPlaceholders" && finding.file !== undefined) {
        return [
            {
                file: finding.file,
                startLine: 1,
                startColumn: 1,
                endLine: 1,
                endColumn: 1,
                replacement: "",
            },
        ];
    }
    return [];
}

function collectSafeEdits(ctx: RuleContext, findings: readonly Finding[]): Map<string, PreparedEdit[]> {
    const byFile = new Map<string, PreparedEdit[]>();
    findings
        .filter((finding) => FORMAT_ALLOWLIST.has(finding.category))
        .filter((finding) => finding.fix?.safety === "safe")
        .forEach((finding) => {
            editsForFinding(finding).forEach((edit) => {
                const relativeFile = relativeFileOf(edit.file);
                if (relativeFile === null || !editableFile(ctx, relativeFile, finding.category)) {
                    return;
                }
                const absoluteFile = absoluteFileOf(relativeFile);
                const text = existsSync(absoluteFile) ? readFileSync(absoluteFile, "utf8") : "";
                const prepared = prepareEdit(text, edit, relativeFile);
                const edits = byFile.get(relativeFile) ?? [];
                edits.push(prepared);
                byFile.set(relativeFile, edits);
            });
        });
    return byFile;
}

function applyEdits(byFile: Map<string, PreparedEdit[]>): readonly string[] {
    const modified = new Set<string>();
    Array.from(byFile.entries()).forEach(([relativeFile, edits]) => {
        const sortedEdits = edits.sort((left, right) => {
            if (left.startLine !== right.startLine) {
                return right.startLine - left.startLine;
            }
            return right.startColumn - left.startColumn;
        });
        sortedEdits.forEach((edit, index) => {
            const previous = sortedEdits[index - 1];
            if (previous && previous.startOffset < edit.endOffset) {
                throw new Error(`overlapping edits for ${relativeFile}`);
            }
        });
        const absoluteFile = absoluteFileOf(relativeFile);
        let text = existsSync(absoluteFile) ? readFileSync(absoluteFile, "utf8") : "";
        sortedEdits.forEach((edit) => {
            text = `${text.slice(0, edit.startOffset)}${edit.replacement}${text.slice(edit.endOffset)}`;
        });
        const original = existsSync(absoluteFile) ? readFileSync(absoluteFile, "utf8") : null;
        if (original !== text) {
            mkdirSync(dirname(absoluteFile), { recursive: true });
            writeFileSync(absoluteFile, text, "utf8");
            modified.add(relativeFile);
        }
    });
    return Array.from(modified).sort();
}

function collectFindings(ctx: RuleContext): readonly Finding[] {
    return Array.from(
        new Map(
            HARNESS_CHECKS.filter((rule) => rule.applies(ctx))
                .flatMap((rule) => rule.validate(ctx))
                .map((finding) => [
                    `${finding.severity}|${finding.category}|${finding.message}|${finding.file ?? ""}|${finding.startLine ?? ""}|${finding.startColumn ?? ""}`,
                    finding,
                ]),
        ).values(),
    );
}

async function main(): Promise<void> {
    const manifestPath = join(root, MANIFEST_PATH);
    if (!existsSync(manifestPath)) {
        logger.error(`failed to read ${MANIFEST_PATH}`);
        throw new Error(`${MANIFEST_PATH} not found`);
    }
    const manifest: HarnessManifest = JSON.parse(readFileSync(manifestPath, "utf8"));
    const executionContext = createRuleContext(root, manifest);
    const oxfmtFiles = executionContext.stackSources("greaterThanComparison");
    if (oxfmtFiles.length > 0) {
        const oxfmtProc = Bun.spawnSync(
            ["bunx", "oxfmt@0.53.0", "--write", ...oxfmtFiles],
            { cwd: root },
        );
        const oxfmtStderr = oxfmtProc.stderr ? new TextDecoder().decode(oxfmtProc.stderr) : "";
        if (!oxfmtProc.success) {
            if (oxfmtProc.exitCode === 127 || oxfmtStderr.includes("command not found")) {
                logger.warn("[oxfmt] bunx not provisioned; skipping format");
            } else {
                logger.error(`[oxfmt] error:\n${oxfmtStderr}`);
                process.exit(1);
            }
        } else {
            logger.log("[oxfmt] format applied");
        }
    }
    const findings = collectFindings(executionContext);
    const modified = applyEdits(collectSafeEdits(executionContext, findings));
    if (0 < modified.length) {
        logger.log(`formatted: ${modified.length}`);
        modified.forEach((path) => {
            logger.log(`  ${path}`);
        });
    } else {
        logger.log("no files formatted");
    }
    logger.log("remaining findings after format:");
    const remainingFindings = collectFindings(executionContext);
    renderFindings(root, remainingFindings).forEach((line) => {
        logger.log(line);
    });
    process.exit(remainingFindings.some((finding) => finding.severity === "ERROR") ? 1 : 0);
}

if (import.meta.main) {
    main().catch((error: unknown) => {
        logger.error(error instanceof Error ? error.message : String(error));
        process.exit(1);
    });
}
