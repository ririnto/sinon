import { readFileSync } from "node:fs";
import path from "node:path";

import { isRecord, parseYamlRecord } from "./infrastructure.js";
import { DOC_FILE_NAMES, FRONTMATTER_DELIMITER_RE } from "./shared.js";
import type {
  FrontmatterBlock,
  JsonRecord,
  LoadEntry,
  MutableRecord
} from "./shared.js";

export const extractFrontmatterFromText = (
  text: string
): FrontmatterBlock | undefined => {
  const lines = text.split(/\r?\n/u);
  const first = (lines[0] ?? "").replace(/^\uFEFF/u, "").trim();
  if (first !== "---") {
    return undefined;
  }
  for (let index = 1; index < lines.length; index += 1) {
    if (FRONTMATTER_DELIMITER_RE.test(lines[index] ?? "")) {
      return { endLine: index + 1, yaml: lines.slice(1, index).join("\n") };
    }
  }
  throw new Error("Unterminated YAML frontmatter");
};

export const extractFrontmatterFromFile = (
  filePath: string
): FrontmatterBlock | undefined =>
  extractFrontmatterFromText(readFileSync(filePath, "utf-8"));

export const normalizeTag = (value: unknown): readonly string[] => {
  if (Array.isArray(value)) {
    return value
      .map((item) => String(item).trim())
      .filter((item) => item.length > 0);
  }
  if (value === undefined || value === null) {
    return [];
  }
  const scalar = String(value).trim();
  return scalar ? [scalar] : [];
};

export const parseFields = (
  rawFields: string | undefined
): readonly string[] | undefined => {
  if (!rawFields) {
    return undefined;
  }
  const fields = rawFields
    .split(",")
    .map((field) => (field.trim() === "tags" ? "tag" : field.trim()))
    .filter((field) => field.length > 0);
  return fields.length > 0 ? fields : undefined;
};

export const subjectString = (data: JsonRecord): string => {
  const { subject } = data;
  if (!isRecord(subject)) {
    return "";
  }
  const name = String(subject["name"] ?? "");
  const version = String(subject["version"] ?? "");
  return name || version ? `${name}@${version}` : "";
};

export const toKindLabel = (filePath: string): string => {
  const base = path.basename(filePath);
  for (const [kind, fileName] of Object.entries(DOC_FILE_NAMES)) {
    if (base === fileName) {
      return kind;
    }
  }
  return "";
};

export const matchesKind = (filePath: string, kind: string): boolean =>
  kind === "any" || path.basename(filePath) === DOC_FILE_NAMES[kind];

export const buildRecord = (
  filePath: string,
  data: MutableRecord,
  endLine: number
): MutableRecord => {
  const tags = normalizeTag(data["tag"] ?? data["tags"]);
  return {
    created: String(data["created"] ?? ""),
    description: String(data["description"] ?? ""),
    file: filePath,
    frontmatter_end_line: endLine,
    kind: toKindLabel(filePath),
    last_updated: String(data["last_updated"] ?? ""),
    status: String(data["status"] ?? ""),
    subject: subjectString(data),
    tag: tags,
    title: String(data["title"] ?? ""),
    updated: String(data["updated"] ?? "")
  };
};

export const loadFrontmatterEntry = (
  filePath: string
): LoadEntry | string | undefined => {
  try {
    const block = extractFrontmatterFromFile(filePath);
    if (!block) {
      return undefined;
    }
    if (!block.yaml.trim()) {
      return "Empty YAML frontmatter";
    }
    const data = parseYamlRecord(block.yaml);
    if (!data) {
      return "Invalid YAML frontmatter";
    }
    const tags = normalizeTag(data["tag"] ?? data["tags"]);
    const record = buildRecord(filePath, data, block.endLine);
    return {
      data,
      endLine: block.endLine,
      record,
      subjectStr: subjectString(data),
      tags,
      yamlBody: block.yaml
    };
  } catch (error) {
    if (error instanceof Error) {
      return error.message;
    }
    throw error;
  }
};

export const matchesFilters = (
  record: JsonRecord,
  frontmatter: JsonRecord,
  filters: readonly (readonly [string, readonly string[]])[]
): boolean => {
  for (const [key, values] of filters) {
    const current = key in record ? record[key] : frontmatter[key];
    if (key === "tag") {
      const tagValues = Array.isArray(current) ? current.map(String) : [];
      if (!values.some((value) => tagValues.includes(value))) {
        return false;
      }
      continue;
    }
    if (Array.isArray(current)) {
      const currentValues = new Set(current.map(String));
      if (!values.some((value) => currentValues.has(value))) {
        return false;
      }
      continue;
    }
    if (!values.includes(String(current))) {
      return false;
    }
  }
  return true;
};
