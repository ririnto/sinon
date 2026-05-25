#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import {
  existsSync,
  lstatSync,
  readdirSync,
  readFileSync,
  readlinkSync,
  statSync,
} from "node:fs";
import { join, resolve } from "node:path";
import type { Severity } from "./severity";
import {
  asRecord,
  createManifest,
  isEnabledFromManifest,
  severityFromManifest,
  type HarnessManifest,
  type Manifest,
} from "./manifest";
import type { Finding } from "../rules/harness-check-rule";

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
  collectFilesUnder(
    path: string,
  ): readonly [readonly string[], readonly Finding[]];
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
    return join(rootDirectory, path);
  }

  function isWithinRoot(path: string): boolean {
    const resolvedPath = resolve(pathOf(path));
    const resolvedRoot = resolve(pathOf("."));
    return (
      resolvedPath === resolvedRoot || resolvedPath.startsWith(`${resolvedRoot}/`)
    );
  }

  function read(path: string): string {
    const target = allowedRootContractTarget(path);
    const filePath = target ?? pathOf(path);
    if (!existsSync(filePath)) {
      return "";
    }
    return readFileSync(filePath, "utf8");
  }

  function firstLine(path: string): string {
    return read(path).split(/\r?\n/, 1)[0] ?? "";
  }

  function isFile(path: string): boolean {
    if (isSymlink(path) && allowedRootContractTarget(path) === null) {
      return false;
    }
    if (!existsSync(pathOf(path))) {
      return false;
    }
    return statSync(pathOf(path)).isFile();
  }

  function isDirectory(path: string): boolean {
    if (isSymlink(path)) {
      return false;
    }
    if (!existsSync(pathOf(path))) {
      return false;
    }
    return statSync(pathOf(path)).isDirectory();
  }

  function isExecutablePath(path: string): boolean {
    const target = allowedRootContractTarget(path);
    const filePath = target ?? pathOf(path);
    if (!existsSync(filePath)) {
      return false;
    }
    return (statSync(filePath).mode & 0o100) !== 0;
  }

  function isSymlink(path: string): boolean {
    if (!existsSync(pathOf(path))) {
      return false;
    }
    return lstatSync(pathOf(path)).isSymbolicLink();
  }

  function allowedRootContractTarget(path: string): string | null {
    if (path !== "AGENTS.md" && path !== "CLAUDE.md") {
      return null;
    }
    const expectedPath = pathOf(path);
    if (!existsSync(expectedPath)) {
      return null;
    }
    const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
    const link = readlinkSync(expectedPath);
    if (link !== expected) {
      return null;
    }
    const expectedFullPath = pathOf(expected);
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
    return Array.isArray(value)
      ? value.filter((item): item is string => typeof item === "string")
      : [];
  }

  function readJsonObject(value: unknown): Record<string, unknown> {
    return typeof value === "object" && value !== null
      ? (value as Record<string, unknown>)
      : {};
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
    const parameters = readJsonObject(
      readJsonObject(manifest.raw[category]).parameters,
    );

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

    const filesAfterInclude = includePaths.length > 0
      ? (() => {
          const included = new Set<string>();
          for (const file of [...collected].sort()) {
            for (const pattern of includePaths) {
              for (const match of new Bun.Glob(pattern).scanSync(".")) {
                if (file === match) {
                  included.add(file);
                  break;
                }
              }
              if (included.has(file)) {
                break;
              }
              for (const match of new Bun.Glob(`${pattern}/**/*`).scanSync(".")) {
                if (file === match) {
                  included.add(file);
                  break;
                }
              }
            }
          }
          return [...included].sort();
      })()
      : [...collected].sort();

    const filesAfterExclude = excludePaths.length > 0
      ? (() => {
          const excluded = new Set<string>();
          for (const file of filesAfterInclude) {
            const shouldSkip = excludePaths.some((pattern) => {
              for (const match of new Bun.Glob(pattern).scanSync(".")) {
                if (file === match) {
                  return true;
                }
              }
              for (const match of new Bun.Glob(`${pattern}/**/*`).scanSync(".")) {
                if (file === match) {
                  return true;
                }
              }
              return false;
            });
            if (!shouldSkip) {
              excluded.add(file);
            }
          }
          return [...excluded].sort();
      })()
      : filesAfterInclude;

    return filesAfterExclude;
  }

  function walkDirectory(
    path: string,
  ): readonly [readonly string[], readonly Finding[]] {
    if (isSymlink(path)) {
      return [[], [{
        severity: "ERROR",
        category: "symlinkSafety",
        message: `symlink scan root is not allowed: ${path}`,
      }]];
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
        return [[[child], [{
          severity: "ERROR",
          category: "symlinkSafety",
          message: `symlink scan entry is not allowed: ${child}`,
        }]]] as const;
      }
      return [statSync(full).isDirectory() ? walkDirectory(child) : [[child], []]];
    });
    const allFiles = childResults.flatMap(([files]) => files);
    const allFindings = childResults.flatMap(([, findings]) => findings);
    return [allFiles, allFindings];
  }

  function collectFilesUnder(
    path: string,
  ): readonly [readonly string[], readonly Finding[]] {
    const earlyFindings = [
      ...(!isWithinRoot(path) ? [{
        severity: "ERROR" as const,
        category: "symlinkSafety",
        message: `source path escapes repository root: ${path}`,
      }] : []),
      ...(isSymlink(path) && allowedRootContractTarget(path) === null ? [{
        severity: "ERROR" as const,
        category: "symlinkSafety",
        message: `symlink path is not allowed: ${path}`,
      }] : []),
    ];
    if (0 < earlyFindings.length) {
      return [[], earlyFindings];
    }
    return isFile(path) ? [[path], []] : walkDirectory(path);
  }

  return {
    stack,
    root: rootDirectory,
    manifest,
    isEnabled(
      manifestOrCategory: HarnessManifest | string,
      category?: string,
    ): boolean {
      if (typeof manifestOrCategory === "string") {
        return manifest.isEnabled(manifestOrCategory);
      }
      if (typeof category === "string") {
        return isEnabledFromManifest(asRecord(manifestOrCategory), category);
      }
      return true;
    },
    severityOf(
      manifestOrCategory: HarnessManifest | string,
      category?: string,
    ): Severity {
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
