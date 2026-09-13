import { existsSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

import type { MutableRecord } from "./shared.js";

/**
 * Returns the executable name from the environment or the default.
 */
export const cliName = (): string => process.env["SDD_CLI_NAME"] ?? "sdd";

/**
 * Prints an error message to standard error.
 */
export const fail = (message: string): void => {
  console.error(message);
};

/**
 * Prints a warning message to standard error.
 */
export const warn = (message: string): void => {
  console.error(`WARN: ${message}`);
};

/**
 * Reports whether a value is an object record rather than an array or null.
 */
export const isRecord = (value: unknown): value is MutableRecord =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/**
 * Finds the default spec directory from $SDD_SPEC_DIR or ./spec, returning
 * the first path that exists as a directory.
 */
export const resolveDefaultSpecPath = (): string | undefined => {
  if (existsSync("spec") && statSync("spec").isDirectory()) {
    return "spec";
  }
  const envPath = process.env["SDD_SPEC_DIR"];
  if (envPath && existsSync(envPath) && statSync(envPath).isDirectory()) {
    return envPath;
  }
  return undefined;
};

/**
 * Parses a YAML string into an object record; undefined for non-records and
 * malformed YAML.
 */
export const parseYamlRecord = (text: string): MutableRecord | undefined => {
  try {
    const parsed = Bun.YAML.parse(text);
    return isRecord(parsed) ? parsed : undefined;
  } catch (error) {
    if (error instanceof Error) {
      return undefined;
    }
    throw error;
  }
};

const formatCellText = (value: unknown): string => {
  if (Array.isArray(value)) {
    return value.join(",");
  }
  if (isRecord(value)) {
    return JSON.stringify(value);
  }
  return String(value);
};

/**
 * Converts a value to a TSV-safe string by replacing tab and newline
 * characters with spaces.
 */
export const sanitizeTsvCell = (value: unknown): string => {
  if (value === undefined || value === null) {
    return "";
  }
  const text = formatCellText(value);
  return text.replaceAll("\t", " ").replaceAll("\r", " ").replaceAll("\n", " ");
};

/**
 * Collects files matching a predicate in depth-first sorted order; the
 * returned paths are absolute.
 */
export const collectFiles = (
  root: string,
  predicate: (filePath: string) => boolean
): readonly string[] => {
  const out: string[] = [];
  const walk = (dirPath: string): void => {
    const entries = readdirSync(dirPath, { withFileTypes: true }).toSorted(
      (left, right) => left.name.localeCompare(right.name)
    );
    for (const entry of entries) {
      const fullPath = path.join(dirPath, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.isFile() && predicate(fullPath)) {
        out.push(path.resolve(fullPath));
      }
    }
  };
  walk(root);
  return out;
};

/**
 * Collects Markdown files under a directory recursively.
 */
export const collectMarkdownFiles = (root: string): readonly string[] =>
  collectFiles(root, (filePath) => filePath.endsWith(".md"));

/**
 * Collects files whose basename equals the given name, recursively.
 */
export const listByBasename = (
  root: string,
  basename: string
): readonly string[] =>
  collectFiles(root, (filePath) => path.basename(filePath) === basename);
