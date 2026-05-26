#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import type { Finding } from "./rules/harness-check-rule";

/**
 * Renders a collection of validation findings in structured diagnostic format.
 * Handles both new findings with location/fix metadata and legacy findings with only severity/category/message.
 *
 * @param root Absolute filesystem root for resolving relative file paths.
 * @param findings Read-only array of validation findings to render.
 * @returns Array of formatted output lines (without trailing newlines).
 */
export function renderFindings(root: string, findings: readonly Finding[]): readonly string[] {
    if (findings.length === 0) {
        return ["OK"];
    }
    const lines: string[] = [];
    const fileSet = new Set<string>();
    const severityCounts: Record<"ERROR" | "WARN" | "INFO", number> = {
        ERROR: 0,
        WARN: 0,
        INFO: 0,
    };
    const safeFixCount = findings.filter((f) => f.fix && f.fix.safety === "safe").length;
    findings.forEach((finding) => {
        const hasFile = finding.file !== undefined;
        const hasLocation = hasFile && finding.startLine !== undefined;
        severityCounts[finding.severity]++;
        if (hasFile) {
            fileSet.add(finding.file);
        }
        if (!hasFile) {
            lines.push(`[${finding.severity.toUpperCase()}] ${finding.category}: ${finding.message}`);
        } else if (!hasLocation) {
            lines.push(`${finding.file} [${finding.severity.toUpperCase()}] ${finding.category}: ${finding.message}`);
        } else {
            lines.push(
                `${finding.file}:${finding.startLine}:${finding.startColumn ?? 1} [${finding.severity.toUpperCase()}] ${finding.category}: ${finding.message}`,
            );
            const snippetLines = getSnippet(root, finding.file, finding.startLine);
            if (snippetLines.length > 0) {
                lines.push("");
                snippetLines.forEach((line) => {
                    lines.push(line);
                });
            }
        }
        if (finding.fix) {
            lines.push("");
            lines.push(`   Safety: ${finding.fix.safety}`);
            lines.push(`   Help: ${finding.fix.description}`);
            if (finding.fix.edits && finding.fix.edits.length > 0) {
                lines.push("");
                lines.push("   Before:");
                extractEditText(root, finding.fix.edits[0])
                    .split("\n")
                    .filter((line) => line)
                    .forEach((line) => {
                        lines.push(`   - ${line}`);
                    });
                lines.push("   After:");
                finding.fix.edits[0].replacement
                    .split("\n")
                    .filter((line) => line)
                    .forEach((line) => {
                        lines.push(`   + ${line}`);
                    });
            }
        }
        lines.push("");
    });
    lines.push(
        `Checked ${fileSet.size} file(s). ${findings.length} violation(s): ${severityCounts.ERROR} error, ${severityCounts.WARN} warn, ${severityCounts.INFO} info.${safeFixCount > 0 ? ` [*] ${safeFixCount} fixable` : ""}`,
    );
    return lines;
}

/**
 * Extracts code context lines surrounding the finding location.
 * Shows 1 line before and 1 line after the offending line, with proper formatting.
 *
 * @param root Absolute filesystem root for resolving relative paths.
 * @param file File path (absolute or relative to root).
 * @param lineNumber 1-indexed line number of the finding.
 * @returns Array of formatted context lines.
 */
function getSnippet(root: string, file: string, lineNumber: number): readonly string[] {
    const absolutePath = file.startsWith("/") ? file : resolve(root, file);
    if (!existsSync(absolutePath)) {
        return [];
    }
    const fileLines = readFileSync(absolutePath, "utf8").split("\n");
    if (lineNumber < 1 || lineNumber > fileLines.length) {
        return [];
    }
    const before = lineNumber > 1 ? lineNumber - 1 : null;
    const after = lineNumber < fileLines.length ? lineNumber + 1 : null;
    const maxLineNum = Math.max(before ?? 0, lineNumber, after ?? 0);
    const lineNumWidth = String(maxLineNum).length;
    return [
        ...(before !== null
            ? [`   ${String(before).padStart(lineNumWidth, " ")} │ ${fileLines[before - 1] ?? ""}`]
            : []),
        `  > ${String(lineNumber).padStart(lineNumWidth, " ")}  │ ${fileLines[lineNumber - 1] ?? ""}`,
        ...(after !== null ? [`   ${String(after).padStart(lineNumWidth, " ")} │ ${fileLines[after - 1] ?? ""}`] : []),
    ];
}

/**
 * Extracts the original text at an edit location from the source file.
 * Used to display the "Before:" section of a fix.
 *
 * @param root Absolute filesystem root for resolving relative paths.
 * @param edit The edit operation containing location and replacement text.
 * @returns The original text that would be replaced.
 */
function extractEditText(
    root: string,
    edit: { file: string; startLine: number; startColumn: number; endLine: number; endColumn: number },
): string {
    const absolutePath = edit.file.startsWith("/") ? edit.file : resolve(root, edit.file);
    if (!existsSync(absolutePath)) {
        return "";
    }
    const lines = readFileSync(absolutePath, "utf8").split("\n");
    if (edit.startLine < 1 || edit.startLine > lines.length) {
        return "";
    }
    return edit.startLine === edit.endLine
        ? lines[edit.startLine - 1].slice(edit.startColumn - 1, edit.endColumn)
        : [
              lines[edit.startLine - 1].slice(edit.startColumn - 1),
              ...Array.from({ length: edit.endLine - edit.startLine - 1 }, (_, idx) => lines[edit.startLine + idx]),
              lines[edit.endLine - 1].slice(0, edit.endColumn),
          ].join("\n");
}
