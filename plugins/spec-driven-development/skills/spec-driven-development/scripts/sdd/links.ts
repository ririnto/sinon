import { existsSync, statSync } from "node:fs";
import path from "node:path";

import { isRecord } from "./infrastructure.js";
import { URL_SCHEME_RE } from "./shared.js";
import type { JsonRecord } from "./shared.js";

const findSpecRootFrom = (cursor: string): string | undefined => {
  let root: string | undefined;
  if (path.basename(cursor) === "spec") {
    root = cursor;
  } else {
    const parent = path.dirname(cursor);
    root = parent === cursor ? undefined : findSpecRootFrom(parent);
  }
  return root;
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
  let roots: readonly [string, string] | undefined;
  if (inSpecRoot !== undefined && inSpecRoot.length > 0) {
    roots = [inSpecRoot, resolved];
  } else {
    const localSpecRoot = path.join(resolved, "spec");
    if (existsSync(localSpecRoot) && statSync(localSpecRoot).isDirectory()) {
      roots = [localSpecRoot, localSpecRoot];
    }
  }
  return roots;
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
  const stripped = value.split("#", 1)[0].trim();
  const resolved = new Set<string>();
  if (stripped && !URL_SCHEME_RE.test(stripped)) {
    if (path.isAbsolute(stripped)) {
      resolved.add(path.resolve(stripped));
    } else {
      const parts = stripped.split(/[\\/]/u).filter((part) => part.length > 0);
      const specRoot = findSpecRoot(baseDir);
      if (
        parts[0] === "spec" &&
        specRoot !== undefined &&
        specRoot.length > 0 &&
        !parts.includes("..")
      ) {
        resolved.add(path.resolve(path.dirname(specRoot), ...parts));
      } else {
        resolved.add(path.resolve(baseDir, ...parts));
        if (
          specRoot !== undefined &&
          specRoot.length > 0 &&
          !parts.includes("..")
        ) {
          resolved.add(path.resolve(specRoot, ...parts));
        }
      }
    }
  }
  return resolved;
};

/**
 * Reads the call entry's link path; empty for non-string paths and
 * non-record entries.
 */
export const extractCallPath = (rawCall: unknown): string => {
  let callPath: string;
  if (typeof rawCall === "string") {
    callPath = rawCall;
  } else if (isRecord(rawCall)) {
    callPath = String(rawCall.path ?? "");
  } else {
    callPath = "";
  }
  return callPath;
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
  const rawCalls = data.call;
  const seen = new Set<string>();
  const result: LinkTarget[] = [];
  if (Array.isArray(rawCalls)) {
    for (const rawCall of rawCalls) {
      const text = extractCallPath(rawCall).trim();
      const stripped = text.split("#", 1)[0].trim();
      if (
        stripped &&
        !URL_SCHEME_RE.test(stripped) &&
        !path.isAbsolute(stripped) &&
        path.basename(stripped) === "SPEC.md"
      ) {
        const resolved = path.resolve(path.dirname(sourceFile), stripped);
        if (!seen.has(resolved)) {
          seen.add(resolved);
          result.push({ raw: text, resolved });
        }
      }
    }
  }
  return result;
};
