import { existsSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

import type { MutableRecord } from "./shared.js";

declare const Bun: {
  readonly main: string;
  readonly YAML: {
    readonly parse: (text: string) => unknown;
  };
};

export const cliName = (): string => process.env["SDD_CLI_NAME"] ?? "sdd";

export const fail = (message: string): void => {
  console.error(message);
};

export const warn = (message: string): void => {
  console.error(`WARN: ${message}`);
};

export const isRecord = (value: unknown): value is MutableRecord =>
  typeof value === "object" && value !== null && !Array.isArray(value);

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

export const skillRoot = (): string => {
  const override = process.env["SDD_SKILL_ROOT"];
  if (override) {
    return path.resolve(override);
  }
  return path.resolve(path.dirname(Bun.main), "..");
};

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

export const stringifyJson = (value: unknown, indent?: number): string =>
  JSON.stringify(value, undefined, indent);

const formatCellText = (value: unknown): string => {
  if (Array.isArray(value)) {
    return value.join(",");
  }
  if (isRecord(value)) {
    return stringifyJson(value);
  }
  return String(value);
};

export const sanitizeTsvCell = (value: unknown): string => {
  if (value === undefined || value === null) {
    return "";
  }
  const text = formatCellText(value);
  return text.replaceAll("\t", " ").replaceAll("\r", " ").replaceAll("\n", " ");
};

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

export const collectMarkdownFiles = (root: string): readonly string[] =>
  collectFiles(root, (filePath) => filePath.endsWith(".md"));

export const listByBasename = (
  root: string,
  basename: string
): readonly string[] =>
  collectFiles(root, (filePath) => path.basename(filePath) === basename);
