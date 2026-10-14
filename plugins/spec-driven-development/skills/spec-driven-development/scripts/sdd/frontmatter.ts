import { readFileSync } from "node:fs";
import path from "node:path";

import { isRecord, parseYamlRecord } from "./infrastructure.js";
import { DOC_FILE_NAMES, FRONTMATTER_DELIMITER_RE } from "./shared.js";
import type {
  FilterRule,
  FrontmatterBlock,
  JsonRecord,
  LoadEntryResult,
  MutableRecord
} from "./shared.js";

/**
 * Extracts the YAML frontmatter block from document text.
 *
 * @returns The block, or undefined when the text does not start with a
 * frontmatter header.
 * @throws {Error} When the opening delimiter has no closing delimiter.
 */
export const extractFrontmatterFromText = (
  text: string
): FrontmatterBlock | undefined => {
  const lines = text.split(/\r?\n/u);
  const first = lines[0].replace(/^\uFEFF/u, "").trim();
  if (first !== "---") {
    return undefined;
  }
  for (let index = 1; index < lines.length; index += 1) {
    if (FRONTMATTER_DELIMITER_RE.test(lines[index])) {
      return { endLine: index + 1, yaml: lines.slice(1, index).join("\n") };
    }
  }
  throw new Error("Unterminated YAML frontmatter");
};

/**
 * Extracts the YAML frontmatter block from a file's contents.
 */
export const extractFrontmatterFromFile = (
  filePath: string
): FrontmatterBlock | undefined =>
  extractFrontmatterFromText(readFileSync(filePath, "utf-8"));

/**
 * Normalizes a single tag or tag array into trimmed, non-empty strings.
 */
export const normalizeTag = (value: unknown): readonly string[] => {
  let tags: string[];
  if (Array.isArray(value)) {
    tags = value
      .map((item) => String(item).trim())
      .filter((item) => item.length > 0);
  } else if (value === undefined || value === null) {
    tags = [];
  } else {
    const scalar = String(value).trim();
    tags = scalar ? [scalar] : [];
  }
  return tags;
};

/**
 * Parses a comma-separated output field list; undefined for empty input and
 * lists with no usable fields.
 */
export const parseFields = (
  rawFields: string | undefined
): readonly string[] | undefined => {
  let fields: readonly string[] | undefined;
  if (rawFields !== undefined && rawFields.length > 0) {
    const parsed = rawFields
      .split(",")
      .map((field) => (field.trim() === "tags" ? "tag" : field.trim()))
      .filter((field) => field.length > 0);
    fields = parsed.length > 0 ? parsed : undefined;
  }
  return fields;
};

/**
 * Renders the subject record as a `name@version` string; empty when either
 * part is missing.
 */
export const subjectString = (data: JsonRecord): string => {
  const { subject } = data;
  let text = "";
  if (isRecord(subject)) {
    const name = String(subject.name ?? "");
    const version = String(subject.version ?? "");
    text = name || version ? `${name}@${version}` : "";
  }
  return text;
};

/**
 * Returns the document kind matching the file's basename; empty when the
 * name is not a known document file name.
 */
export const toKindLabel = (filePath: string): string => {
  const base = path.basename(filePath);
  for (const [kind, fileName] of Object.entries(DOC_FILE_NAMES)) {
    if (base === fileName) {
      return kind;
    }
  }
  return "";
};

/**
 * Reports whether a file path matches the document kind; `any` matches all.
 */
export const matchesKind = (filePath: string, kind: string): boolean =>
  kind === "any" || path.basename(filePath) === DOC_FILE_NAMES[kind];

/**
 * Builds a list-output record from the original frontmatter, filling absent
 * fields with empty values.
 */
export const buildRecord = (
  filePath: string,
  data: MutableRecord,
  endLine: number
): MutableRecord => {
  const tags = normalizeTag(data.tag ?? data.tags);
  return {
    created: String(data.created ?? ""),
    description: String(data.description ?? ""),
    file: filePath,
    frontmatter_end_line: endLine,
    kind: toKindLabel(filePath),
    last_updated: String(data.last_updated ?? ""),
    status: String(data.status ?? ""),
    subject: subjectString(data),
    tag: tags,
    title: String(data.title ?? ""),
    updated: String(data.updated ?? "")
  };
};

/**
 * Parses a file's YAML frontmatter into a discriminated result: entry,
 * missing, or error carrying the failure message.
 */
export const loadFrontmatterEntry = (filePath: string): LoadEntryResult => {
  try {
    const block = extractFrontmatterFromFile(filePath);
    if (!block) {
      return { kind: "missing" };
    }
    if (!block.yaml.trim()) {
      return { kind: "error", message: "Empty YAML frontmatter" };
    }
    const data = parseYamlRecord(block.yaml);
    if (!data) {
      return { kind: "error", message: "Invalid YAML frontmatter" };
    }
    const tags = normalizeTag(data.tag ?? data.tags);
    const record = buildRecord(filePath, data, block.endLine);
    return {
      entry: {
        data,
        endLine: block.endLine,
        record,
        subjectStr: subjectString(data),
        tags,
        yamlBody: block.yaml
      },
      kind: "entry"
    };
  } catch (error) {
    if (error instanceof Error) {
      return { kind: "error", message: error.message };
    }
    throw error;
  }
};

/**
 * Reports whether the record and frontmatter satisfy every filter; a key is
 * read from the record first and falls back to the raw frontmatter.
 */
export const matchesFilters = (
  record: JsonRecord,
  frontmatter: JsonRecord,
  filters: readonly FilterRule[]
): boolean => {
  for (const [key, values] of filters) {
    const current = key in record ? record[key] : frontmatter[key];
    if (key === "tag") {
      const tagValues = Array.isArray(current) ? current.map(String) : [];
      if (!values.some((value) => tagValues.includes(value))) {
        return false;
      }
    } else if (Array.isArray(current)) {
      const currentValues = new Set(current.map(String));
      if (!values.some((value) => currentValues.has(value))) {
        return false;
      }
    } else if (!values.includes(String(current))) {
      return false;
    }
  }
  return true;
};
