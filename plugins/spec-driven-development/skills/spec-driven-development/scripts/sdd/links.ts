import { existsSync, statSync } from "node:fs";
import path from "node:path";

import { isRecord } from "./infrastructure.js";
import { URL_SCHEME_RE } from "./shared.js";
import type { JsonRecord } from "./shared.js";

const findSpecRootFrom = (cursor: string): string | undefined => {
  if (path.basename(cursor) === "spec") {
    return cursor;
  }
  const parent = path.dirname(cursor);
  return parent === cursor ? undefined : findSpecRootFrom(parent);
};

/**
 * Finds the closest directory named `spec` at or above the input path;
 * undefined when no ancestor is a spec root.
 */
export const findSpecRoot = (inputPath: string): string | undefined =>
  findSpecRootFrom(path.resolve(inputPath));

/**
 * Resolves the validation pair: spec root plus the path to scan. When the
 * input sits inside a spec root the scan target is the input itself;
 * otherwise the input must contain a spec directory.
 */
export const resolveValidationRoots = (
  specPath: string
): readonly [string, string] | undefined => {
  const resolved = path.resolve(specPath);
  const inSpecRoot = findSpecRoot(resolved);
  if (inSpecRoot) {
    return [inSpecRoot, resolved];
  }
  const localSpecRoot = path.join(resolved, "spec");
  if (existsSync(localSpecRoot) && statSync(localSpecRoot).isDirectory()) {
    return [localSpecRoot, localSpecRoot];
  }
  return undefined;
};

/**
 * Converts a link value into absolute path candidates for existence checks;
 * relative `spec/...` links resolve against the spec root in addition to the
 * base directory.
 */
export const resolveTargetPaths = (
  value: string,
  baseDir: string
): Set<string> => {
  const stripped = value.split("#", 1)[0]?.trim() ?? "";
  const resolved = new Set<string>();
  if (!stripped || URL_SCHEME_RE.test(stripped)) {
    return resolved;
  }
  if (path.isAbsolute(stripped)) {
    resolved.add(path.resolve(stripped));
    return resolved;
  }
  const parts = stripped.split(/[\\/]/u).filter((part) => part.length > 0);
  const specRoot = findSpecRoot(baseDir);
  if (parts[0] === "spec" && specRoot && !parts.includes("..")) {
    resolved.add(path.resolve(path.dirname(specRoot), ...parts));
    return resolved;
  }
  resolved.add(path.resolve(baseDir, ...parts));
  if (specRoot && !parts.includes("..")) {
    resolved.add(path.resolve(specRoot, ...parts));
  }
  return resolved;
};

/**
 * Reads the call entry's link path; empty for non-string paths and
 * non-record entries.
 */
export const extractCallPath = (rawCall: unknown): string => {
  if (typeof rawCall === "string") {
    return rawCall;
  }
  if (isRecord(rawCall)) {
    return String(rawCall["path"] ?? "");
  }
  return "";
};

/**
 * Stores a SPEC link's raw text and its source-resolved absolute path.
 */
export interface LinkTarget {
  readonly resolved: string;
  readonly raw: string;
}

/**
 * Extracts relative SPEC.md links from a frontmatter call list, dropping
 * duplicates and links that are absolute or scheme-qualified.
 */
export const extractLinkTargets = (
  data: JsonRecord,
  sourceFile: string
): readonly LinkTarget[] => {
  const rawCalls = data["call"];
  if (!Array.isArray(rawCalls)) {
    return [];
  }
  const seen = new Set<string>();
  const result: LinkTarget[] = [];
  for (const rawCall of rawCalls) {
    const text = extractCallPath(rawCall).trim();
    const stripped = text.split("#", 1)[0]?.trim() ?? "";
    if (
      !stripped ||
      URL_SCHEME_RE.test(stripped) ||
      path.isAbsolute(stripped) ||
      path.basename(stripped) !== "SPEC.md"
    ) {
      continue;
    }
    const resolved = path.resolve(path.dirname(sourceFile), stripped);
    if (!seen.has(resolved)) {
      seen.add(resolved);
      result.push({ raw: text, resolved });
    }
  }
  return result;
};
