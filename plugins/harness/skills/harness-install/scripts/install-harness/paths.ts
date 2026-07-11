import { lstat, mkdir } from "node:fs/promises";
import path from "node:path";

import type { CiHost, InstallCandidate, InstallerConfig } from "./types.js";
import { fail } from "./types.js";

const isEnoent = (error: unknown): boolean =>
  error instanceof Error && "code" in error && error.code === "ENOENT";

const lstatOrAbsent = async (targetPath: string) => {
  try {
    return await lstat(targetPath);
  } catch (error) {
    if (isEnoent(error)) {
      return null;
    }
    throw error;
  }
};

/** Check parent components in order so no lookup crosses a symlink. */
const checkParentComponents = async (
  parents: readonly string[],
  index = 0
): Promise<void> => {
  if (index >= parents.length) {
    return;
  }
  const checkedParent = parents[index];
  if (checkedParent === undefined) {
    return;
  }
  const stat = await lstatOrAbsent(checkedParent);
  if (stat?.isSymbolicLink() === true) {
    fail(
      `[safe_parent] refusing symlink directory component: ${checkedParent}`
    );
  }
  if (stat !== null && !stat.isDirectory()) {
    fail(`[safe_parent] parent component is not a directory: ${checkedParent}`);
  }
  await checkParentComponents(parents, index + 1);
};

export const ensureSafeRelativePath = (targetPath: string): void => {
  const cleanPath = targetPath.startsWith("./")
    ? targetPath.slice(2)
    : targetPath;
  if (
    cleanPath === "" ||
    cleanPath === "." ||
    cleanPath.startsWith("/") ||
    cleanPath.includes("\\")
  ) {
    fail(
      `unsafe target path: ${targetPath} (must be relative, non-empty, no .. references)`
    );
  }
  const parts = cleanPath.split("/");
  if (parts.some((part) => part === "" || part === "." || part === "..")) {
    fail(
      `unsafe target path: ${targetPath} (must be relative, non-empty, no .. references)`
    );
  }
};

export const normalizeRequestedTargetPath = (requestedPath: string): string => {
  const normalized = requestedPath.startsWith("./")
    ? requestedPath.slice(2)
    : requestedPath;
  ensureSafeRelativePath(normalized);
  if (normalized.endsWith("/")) {
    fail(
      `unsafe target path: ${normalized} (must be a file path, not a directory)`
    );
  }
  return normalized;
};

/** Check destination parents without creating them. */
export const checkSafeParentDir = async (targetPath: string): Promise<void> => {
  const parent = path.dirname(targetPath);
  if (parent === "" || parent === ".") {
    return;
  }
  ensureSafeRelativePath(parent);
  let current = "";
  const parents = [];
  for (const part of parent.split("/")) {
    current = current === "" ? part : `${current}/${part}`;
    parents.push(current);
  }
  await checkParentComponents(parents);
};

/** Check a file destination without following or creating path components. */
export const checkSafeFileDestination = async (
  targetPath: string
): Promise<void> => {
  const cleanPath = targetPath.startsWith("./")
    ? targetPath.slice(2)
    : targetPath;
  ensureSafeRelativePath(cleanPath);
  await checkSafeParentDir(cleanPath);
  const stat = await lstatOrAbsent(cleanPath);
  if (stat?.isSymbolicLink() === true) {
    fail(`[safe_destination] refusing symlink file destination: ${cleanPath}`);
  }
  if (stat?.isDirectory()) {
    fail(
      `[safe_destination] refusing directory file destination: ${cleanPath}`
    );
  }
};

/** Check a file destination, then create its missing parent directories. */
export const ensureSafeFileDestination = async (
  targetPath: string
): Promise<void> => {
  await checkSafeFileDestination(targetPath);
  const cleanPath = targetPath.startsWith("./")
    ? targetPath.slice(2)
    : targetPath;
  const parent = path.dirname(cleanPath);
  if (parent !== "" && parent !== ".") {
    await mkdir(parent, { recursive: true });
  }
};

/** Check a parent destination, then create its missing directories. */
export const ensureSafeParentDir = async (
  targetPath: string
): Promise<void> => {
  await checkSafeParentDir(targetPath);
  const parent = path.dirname(targetPath);
  if (parent !== "" && parent !== ".") {
    await mkdir(parent, { recursive: true });
  }
};

export const isCommonSkipPath = (rel: string): boolean =>
  rel === "AGENTS.md" || rel === "CLAUDE.md";

export const isHostTemplatePath = (rel: string, ciHost: CiHost): boolean => {
  if (rel.startsWith(".github/ISSUE_TEMPLATE/")) {
    return ciHost !== "github" && ciHost !== "both";
  }
  if (rel === ".github/pull_request_template.md") {
    return ciHost !== "github" && ciHost !== "both";
  }
  if (rel.startsWith(".gitlab/issue_templates/")) {
    return ciHost !== "gitlab" && ciHost !== "both";
  }
  if (rel.startsWith(".gitlab/merge_request_templates/")) {
    return ciHost !== "gitlab" && ciHost !== "both";
  }
  return false;
};

export const isDirectTemplateEntry = (rel: string): boolean =>
  rel.startsWith("docs/templates/");

export const requiredSelectedPath = (config: InstallerConfig): string => {
  if (config.selectedPath !== null) {
    return config.selectedPath;
  }
  return fail(`--${config.action} requires a path argument.`);
};

export const requiredSrc = (candidate: InstallCandidate): string => {
  if (candidate.src !== undefined) {
    return candidate.src;
  }
  return fail(`candidate has no source: ${candidate.dst}`);
};

export const requiredRealTarget = (candidate: InstallCandidate): string => {
  if (candidate.realTarget !== undefined) {
    return candidate.realTarget;
  }
  return fail(`candidate has no root contract target: ${candidate.dst}`);
};

export const toPosixRelative = (root: string, filePath: string): string =>
  path.relative(root, filePath).split(path.sep).join("/");

export const insideTarget = (root: string, rel: string): string =>
  path.normalize(path.join(root, rel));
