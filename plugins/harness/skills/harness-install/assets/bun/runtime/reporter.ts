#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { lstatSync, readFileSync, realpathSync, statSync } from "node:fs";
import { isAbsolute, normalize, resolve, sep } from "node:path";
import type { Finding } from "./rules/harness-check-rule";

interface SafePathResult {
    readonly resolved: string;
    readonly safe: boolean;
}

const hasLeadingDashComponent = (path: string): boolean => path.split(/[\\/]/).some((segment) => segment !== "" && segment.startsWith("-"));

const safeRoot = (root: string): string => realpathSync(resolve(root));

const resolveSafeManifestPath = (root: string, path: string): SafePathResult => {
    if (path === "" || isAbsolute(path)) {
        return { resolved: "", safe: false };
    }

    const normalized = normalize(path);
    if (
        normalized === "." ||
        normalized === ".." ||
        normalized.startsWith("..") ||
        normalized.startsWith(`..${sep}`)
    ) {
        return { resolved: "", safe: false };
    }

    if (hasLeadingDashComponent(normalized)) {
        return { resolved: "", safe: false };
    }

    const absolutePath = resolve(root, normalized);
    const resolvedRoot = safeRoot(root);
    if (absolutePath !== resolvedRoot && !absolutePath.startsWith(`${resolvedRoot}${sep}`)) {
        return { resolved: "", safe: false };
    }

    let probe = root;
    for (const segment of normalized.split(/[\\/]/)) {
        if (segment === "" || segment === ".") {
            continue;
        }
        probe = resolve(probe, segment);
        try {
            if (lstatSync(probe).isSymbolicLink()) {
                return { resolved: "", safe: false };
            }
        } catch {
            break;
        }
    }

    try {
        const real = realpathSync(absolutePath);
        if (real !== resolvedRoot && !real.startsWith(`${resolvedRoot}${sep}`)) {
            return { resolved: "", safe: false };
        }
        if (!statSync(real).isFile()) {
            return { resolved: "", safe: false };
        }
        return { resolved: real, safe: true };
    } catch {
        return { resolved: "", safe: false };
    }
};

const resolveSafeManifestFile = (root: string, path: string): string | null => {
    const resolved = resolveSafeManifestPath(root, path);
    return resolved.safe ? resolved.resolved : null;
};
/**
 * Renders a collection of validation findings in structured diagnostic format.
 * Handles both findings with location/fix metadata and locationless findings with only severity/category/message.
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
    const absolutePath = resolveSafeManifestFile(root, file);
    if (absolutePath === null) {
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
    const absolutePath = resolveSafeManifestFile(root, edit.file);
    if (absolutePath === null) {
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
