#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { existsSync, lstatSync, readdirSync, readFileSync, readlinkSync, realpathSync, statSync } from "node:fs";
import { isAbsolute, join, normalize, resolve, sep } from "node:path";
import type { Finding } from "../rules/harness-check-rule";
import type { HarnessManifest, Manifest } from "./manifest";
import { asRecord, createManifest, isEnabledFromManifest, severityFromManifest } from "./manifest";
import type { Severity } from "./severity";

/**
 * Result of resolving a manifest-controlled relative path.
 *
 * When the path is unsafe, `resolved` is the empty string and `safe` is `false`.
 */
interface SafePath {
    readonly resolved: string;
    readonly safe: boolean;
}

/**
 * A path component that starts with a dash (`-...`) is rejected because it
 * could be interpreted as a command-line flag by downstream consumers.
 */
function hasLeadingDashComponent(path: string): boolean {
    return path.split(/[\\/]/).some((segment) => segment !== "" && segment.startsWith("-"));
}

/**
 * Check whether a path string is a safe relative manifest path.
 *
 * Rejected: empty, absolute, parent traversal (`..`), leading-dash components.
 * Also rejects paths whose resolved/real path escapes `rootDirectory`.
 * Also rejects any intermediate path component that is itself a symlink
 * (except the root-contract alias preserved by `allowedRootContractTarget`).
 */
function resolveSafeManifestPath(rootDirectory: string, path: string): SafePath {
    if (path === "" || isAbsolute(path)) {
        return { resolved: "", safe: false };
    }
    const normalized = normalize(path);
    if (normalized === "." || normalized === ".." || normalized.startsWith("..") || normalized.startsWith(`..${sep}`)) {
        return { resolved: "", safe: false };
    }
    if (hasLeadingDashComponent(normalized)) {
        return { resolved: "", safe: false };
    }
    const resolved = resolve(join(rootDirectory, normalized));
    const resolvedRoot = resolve(rootDirectory);
    if (resolved !== resolvedRoot && !resolved.startsWith(`${resolvedRoot}${sep}`)) {
        return { resolved: "", safe: false };
    }
    try {
        const realRoot = realpathSync(resolvedRoot);
        const real = realpathSync(resolved);
        if (real !== realRoot && !real.startsWith(`${realRoot}${sep}`)) {
            return { resolved: "", safe: false };
        }
    } catch {
        return { resolved: "", safe: false };
    }
    return { resolved, safe: true };
}

/**
 * Shared context passed to all rule instances.
 */
export interface RuleContext {
    readonly stack: string;
    root: string;
    manifest: Manifest;
    isEnabled(category: string): boolean;
    isEnabled(manifest: HarnessManifest, category: string): boolean;
    severityOf(category: string): Severity;
    severityOf(manifest: HarnessManifest, category: string): Severity;
    stringArray(value: unknown): readonly string[];
    stringValue(value: unknown): string;
    categoryObject(category: string): Record<string, unknown>;
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
    stackSources(category: string): readonly string[];
    walkDirectory(path: string): readonly [readonly string[], readonly Finding[]];
    collectFilesUnder(path: string): readonly [readonly string[], readonly Finding[]];
}

/**
 * Factory function to create a RuleContext for a given root directory and manifest.
 * All filesystem helpers close over the rootDirectory parameter.
 */
export function createRuleContext(
    rootDirectory: string,
    rawManifest: unknown,
    stack: string = "typescript",
): RuleContext {
    const manifest = createManifest(rawManifest);

    function pathOf(path: string): string {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        return safe.safe ? safe.resolved : join(rootDirectory, path);
    }
    function isWithinRoot(path: string): boolean {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        return safe.safe;
    }
    function read(path: string): string {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        if (!safe.safe) {
            return "";
        }
        const filePath = allowedRootContractTarget(path) ?? safe.resolved;
        return existsSync(filePath) ? readFileSync(filePath, "utf8") : "";
    }
    function firstLine(path: string): string {
        return read(path).split(/\r?\n/, 1)[0] ?? "";
    }
    function isFile(path: string): boolean {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        if (!safe.safe) {
            return false;
        }
        if (isSymlink(path) && allowedRootContractTarget(path) === null) {
            return false;
        }
        if (!existsSync(safe.resolved)) {
            return false;
        }
        return statSync(safe.resolved).isFile();
    }
    function isDirectory(path: string): boolean {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        if (!safe.safe) {
            return false;
        }
        if (isSymlink(path)) {
            return false;
        }
        if (!existsSync(safe.resolved)) {
            return false;
        }
        return statSync(safe.resolved).isDirectory();
    }
    function isExecutablePath(path: string): boolean {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        if (!safe.safe) {
            return false;
        }
        const target = allowedRootContractTarget(path);
        const filePath = target ?? safe.resolved;
        if (!existsSync(filePath)) {
            return false;
        }
        return (statSync(filePath).mode & 0o100) !== 0;
    }
    function isSymlink(path: string): boolean {
        const safe = resolveSafeManifestPath(rootDirectory, path);
        if (!safe.safe) {
            return false;
        }
        if (!existsSync(safe.resolved)) {
            return false;
        }
        return lstatSync(safe.resolved).isSymbolicLink();
    }
    function allowedRootContractTarget(path: string): string | null {
        if (path !== "AGENTS.md" && path !== "CLAUDE.md") {
            return null;
        }
        const expectedPath = join(rootDirectory, path);
        if (!existsSync(expectedPath) || !lstatSync(expectedPath).isSymbolicLink()) {
            return null;
        }
        const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
        if (readlinkSync(expectedPath) !== expected) {
            return null;
        }
        const expectedFullPath = join(rootDirectory, expected);
        if (!existsSync(expectedFullPath)) {
            return null;
        }
        if (lstatSync(expectedFullPath).isSymbolicLink()) {
            return null;
        }
        if (!statSync(expectedFullPath).isFile()) {
            return null;
        }
        return expectedFullPath;
    }

    function toStringValue(value: unknown): string {
        return typeof value === "string" ? value : "";
    }

    function readStringArray(value: unknown): readonly string[] {
        return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
    }

    function readJsonObject(value: unknown): Record<string, unknown> {
        return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
    }

    /**
     * Collect source files matching stack configuration.
     *
     * Expands glob entries via Bun.Glob, treats literal paths as directories,
     * filters by configured extensions, applies include/exclude patterns,
     * and skips node_modules and build directories.
     *
     * @param category Harness check category.
     * @return Sorted unique list of source file paths relative to root.
     */
    function stackSources(category: string): readonly string[] {
        const collected = new Set<string>();
        const parameters = readJsonObject(readJsonObject(manifest.raw[category]).parameters);

        const sourceDirs = readStringArray(parameters.sourceRoots);
        const extensions = new Set(readStringArray(parameters.extensions));
        const includePaths = readStringArray(parameters.includePaths);
        const excludePaths = readStringArray(parameters.excludePaths);

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
                if (!isWithinRoot(sourceDir)) {
                    continue;
                }
                if (sourceDir.includes("*")) {
                    for (const match of new Bun.Glob(sourceDir).scanSync(".")) {
                        collectGlobMatch(match);
                    }
                    for (const match of new Bun.Glob(`${sourceDir}/**/*`).scanSync(".")) {
                        collectGlobMatch(match);
                    }
                } else {
                    for (const file of walkDirGen(sourceDir)) {
                        collected.add(file);
                    }
                }
            }
        }

        const filesAfterInclude =
            includePaths.length > 0
                ? Array.from(collected)
                      .sort()
                      .filter((file) =>
                          includePaths.some((pattern) =>
                              [
                                  ...new Bun.Glob(pattern).scanSync("."),
                                  ...new Bun.Glob(`${pattern}/**/*`).scanSync("."),
                              ].includes(file),
                          ),
                      )
                : Array.from(collected).sort();

        return excludePaths.length > 0
            ? filesAfterInclude.filter(
                  (file) =>
                      !excludePaths.some((pattern) =>
                          [
                              ...new Bun.Glob(pattern).scanSync("."),
                              ...new Bun.Glob(`${pattern}/**/*`).scanSync("."),
                          ].includes(file),
                      ),
              )
            : filesAfterInclude;
    }

    function walkDirectory(path: string): readonly [readonly string[], readonly Finding[]] {
        if (isSymlink(path)) {
            return [
                [],
                [
                    {
                        severity: "ERROR",
                        category: "symlinkSafety",
                        message: `symlink scan root is not allowed: ${path}`,
                    },
                ],
            ];
        }
        if (isFile(path)) {
            return [[path], []];
        }
        if (!isDirectory(path)) {
            return [[], []];
        }
        const entries = readdirSync(pathOf(path));
        const childResults = entries.flatMap((entry: string) => {
            const child = `${path}/${entry}`;
            const full = pathOf(child);
            if (lstatSync(full).isSymbolicLink()) {
                return [
                    [
                        [child],
                        [
                            {
                                severity: "ERROR",
                                category: "symlinkSafety",
                                message: `symlink scan entry is not allowed: ${child}`,
                            },
                        ],
                    ],
                ] as const;
            }
            return [statSync(full).isDirectory() ? walkDirectory(child) : [[child], []]];
        });
        const allFiles = childResults.flatMap(([files]) => files);
        const allFindings = childResults.flatMap(([, findings]) => findings);
        return [allFiles, allFindings];
    }

    function collectFilesUnder(path: string): readonly [readonly string[], readonly Finding[]] {
        const earlyFindings: readonly Finding[] = [
            ...(!isWithinRoot(path)
                ? [
                      {
                          severity: "ERROR" as const,
                          category: "symlinkSafety",
                          message: `source path escapes repository root: ${path}`,
                      },
                  ]
                : []),
            ...(isSymlink(path) && allowedRootContractTarget(path) === null
                ? [
                      {
                          severity: "ERROR" as const,
                          category: "symlinkSafety",
                          message: `symlink path is not allowed: ${path}`,
                      },
                  ]
                : []),
        ];
        return earlyFindings.length > 0 ? [[], earlyFindings] : isFile(path) ? [[path], []] : walkDirectory(path);
    }

    return {
        stack,
        root: rootDirectory,
        manifest,
        isEnabled(manifestOrCategory: HarnessManifest | string, category?: string): boolean {
            if (typeof manifestOrCategory === "string") {
                return manifest.isEnabled(manifestOrCategory);
            }
            if (typeof category === "string") {
                return isEnabledFromManifest(asRecord(manifestOrCategory), category);
            }
            return true;
        },
        severityOf(manifestOrCategory: HarnessManifest | string, category?: string): Severity {
            if (typeof manifestOrCategory === "string") {
                return manifest.severityOf(manifestOrCategory);
            }
            if (typeof category === "string") {
                return severityFromManifest(asRecord(manifestOrCategory), category);
            }
            return "ERROR";
        },
        stringArray(value: unknown): readonly string[] {
            return readStringArray(value);
        },
        stringValue(value: unknown): string {
            return toStringValue(value);
        },
        categoryObject(category: string): Record<string, unknown> {
            return manifest.categoryObject(category);
        },
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
        stackSources,
        walkDirectory,
        collectFilesUnder,
    };
}
