#!/usr/bin/env bun
// -*- coding: utf-8 -*-
/* eslint-disable complexity, func-style, no-negated-condition, no-nested-ternary, no-shadow, prefer-named-capture-group, require-unicode-regexp, unicorn/no-negated-condition */

import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

declare const Bun: {
  readonly argv: readonly string[];
  readonly main: string;
  readonly YAML: {
    readonly parse: (text: string) => unknown;
  };
};

type JsonRecord = Readonly<Record<string, unknown>>;
type MutableRecord = Record<string, unknown>;
interface FrontmatterBlock {
  readonly yaml: string;
  readonly endLine: number;
}
interface LoadEntry {
  readonly yamlBody: string;
  readonly endLine: number;
  readonly data: MutableRecord;
  readonly record: MutableRecord;
  readonly subjectStr: string;
  readonly tags: readonly string[];
}
interface ValidationResult {
  readonly errors: readonly string[];
  readonly passed: boolean;
}
interface ParsedArgs {
  readonly command: string;
  readonly positionals: readonly string[];
  readonly options: Record<string, string | boolean | readonly string[]>;
}

const VALID_KINDS = ["spec", "research", "contract"] as const;
const LIST_KINDS = ["any", "spec", "research", "contract"] as const;
const VALID_FORMATS = ["json", "jsonl", "yaml", "value", "file"] as const;
const DOC_FILE_NAMES: Record<string, string> = {
  contract: "CONTRACT.md",
  research: "RESEARCH.md",
  spec: "SPEC.md"
};
const SPEC_STATUSES = new Set([
  "draft",
  "review",
  "approved",
  "wip",
  "implemented",
  "deprecated",
  "superseded",
  "removed"
]);
const FRONTMATTER_DELIMITER_RE = /^---[ \t]*$/;
const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const URL_SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;
const RELATIVE_SPEC_LINK_RE =
  /^(?![a-zA-Z][a-zA-Z0-9+.-]*:)(?!\/)(?![A-Za-z]:[\\/])(?:\.\/|\.\.\/|[^/][^/]*\/)*SPEC\.md(?:#[^\s]+)?$/;
const CHANGELOG_ENTRY_RE =
  /^[ \t]{0,3}##[ \t]+(\d{4}-\d{2}-\d{2})[ \t]+-[ \t]+\S.*$/gm;
const TODO_RE = /(?<![A-Za-z0-9_./#-])todo:(?!\/\/)/i;
const PLACEHOLDER_RE = /\{\{[^}]+\}\}/;
const MANUAL_NUMBERED_HEADING_RE = /^[ \t]{0,3}#{2,6}\s+\d+\.\s+\S.*$/m;
const MARKDOWNLINT_DIRECTIVE_RE = /<!--[ \t]*markdownlint-[a-z0-9_-]+\b/i;
const REVERSE_LINK_RE =
  /^(#{2,6}\s+|[ \t]*[-*]\s*)(Called By|Incoming Links|Inbound Links|Inbound References|Backlinks)\b/im;
const DEPRECATED_LINK_SECTION_RE =
  /^#{2,6}\s+(?:Deprecated\s+)?Link(?:-| )Maintenance(?:\s+\(Deprecated\))?\s*$/im;
const SCAFFOLDING_LINES = new Set([
  "Describe why this SPEC is needed and what problem it solves.",
  "Describe the role this SPEC plays in the system.",
  "Describe the capability and boundary this SPEC covers.",
  "Summarize scope and key concepts.",
  "Define functional requirements.",
  "Ensure every requirement is verifiable.",
  "Add at least one concrete verification example for this requirement.",
  "List the scenarios that verify this requirement.",
  "Describe major scenarios.",
  "State which requirement or requirements each flow satisfies.",
  "Describe the primary success path step by step.",
  "Describe valid alternate paths and branching behavior.",
  "Describe failure paths, error triggers, and expected outcomes.",
  "Define core data models or entities.",
  "For each entity, include purpose, key fields, and invariants.",
  "Document externally meaningful constraints and guarantees.",
  "Add domain, compatibility, security, performance, interoperability, or operational constraints when they materially affect behavior.",
  "Avoid unnecessary language, framework, library, or code-style constraints here unless they are explicitly requested or materially required."
]);

function cliName(): string {
  return process.env["SDD_CLI_NAME"] ?? "sdd";
}

function fail(message: string): void {
  console.error(message);
}

function warn(message: string): void {
  console.error(`WARN: ${message}`);
}

function isRecord(value: unknown): value is MutableRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function resolveDefaultSpecPath(): string | undefined {
  if (existsSync("spec") && statSync("spec").isDirectory()) {
    return "spec";
  }
  const envPath = process.env["SDD_SPEC_DIR"];
  if (envPath && existsSync(envPath) && statSync(envPath).isDirectory()) {
    return envPath;
  }
  return undefined;
}

function skillRoot(): string {
  const override = process.env["SDD_SKILL_ROOT"];
  if (override) {
    return path.resolve(override);
  }
  return path.resolve(path.dirname(Bun.main), "..");
}

function parseYamlRecord(text: string): MutableRecord | undefined {
  try {
    const parsed = Bun.YAML.parse(text) as unknown;
    return isRecord(parsed) ? parsed : undefined;
  } catch (error) {
    if (error instanceof Error) {
      return undefined;
    }
    throw error;
  }
}

function extractFrontmatterFromText(
  text: string
): FrontmatterBlock | undefined {
  const lines = text.split(/\r?\n/);
  const first = (lines[0] ?? "").replace(/^\uFEFF/, "").trim();
  if (first !== "---") {
    return undefined;
  }
  for (let index = 1; index < lines.length; index += 1) {
    if (FRONTMATTER_DELIMITER_RE.test(lines[index] ?? "")) {
      return { endLine: index + 1, yaml: lines.slice(1, index).join("\n") };
    }
  }
  throw new Error("Unterminated YAML frontmatter");
}

function extractFrontmatterFromFile(
  filePath: string
): FrontmatterBlock | undefined {
  return extractFrontmatterFromText(readFileSync(filePath, "utf-8"));
}

function normalizeTag(value: unknown): readonly string[] {
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
}

function stringifyJson(value: unknown, indent?: number): string {
  return JSON.stringify(value, undefined, indent);
}

function parseFields(
  rawFields: string | undefined
): readonly string[] | undefined {
  if (!rawFields) {
    return undefined;
  }
  const fields = rawFields
    .split(",")
    .map((field) => (field.trim() === "tags" ? "tag" : field.trim()))
    .filter((field) => field.length > 0);
  return fields.length > 0 ? fields : undefined;
}

function collectFiles(
  root: string,
  predicate: (filePath: string) => boolean
): readonly string[] {
  const out: string[] = [];
  function walk(dirPath: string): void {
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
  }
  walk(root);
  return out;
}

function collectMarkdownFiles(root: string): readonly string[] {
  return collectFiles(root, (filePath) => filePath.endsWith(".md"));
}

function listByBasename(root: string, basename: string): readonly string[] {
  return collectFiles(root, (filePath) => path.basename(filePath) === basename);
}

function matchesKind(filePath: string, kind: string): boolean {
  return kind === "any" || path.basename(filePath) === DOC_FILE_NAMES[kind];
}

function toKindLabel(filePath: string): string {
  const base = path.basename(filePath);
  for (const [kind, fileName] of Object.entries(DOC_FILE_NAMES)) {
    if (base === fileName) {
      return kind;
    }
  }
  return "";
}

function subjectString(data: JsonRecord): string {
  const { subject } = data;
  if (!isRecord(subject)) {
    return "";
  }
  const name = String(subject["name"] ?? "");
  const version = String(subject["version"] ?? "");
  return name || version ? `${name}@${version}` : "";
}

function buildRecord(
  filePath: string,
  data: MutableRecord,
  endLine: number
): MutableRecord {
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
}

function loadFrontmatterEntry(
  filePath: string
): LoadEntry | string | undefined {
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
}

function sanitizeTsvCell(value: unknown): string {
  if (value === undefined || value === null) {
    return "";
  }
  const text = Array.isArray(value)
    ? value.join(",")
    : isRecord(value)
      ? stringifyJson(value)
      : String(value);
  return text.replaceAll("\t", " ").replaceAll("\r", " ").replaceAll("\n", " ");
}

function findSpecRoot(inputPath: string): string | undefined {
  let cursor = path.resolve(inputPath);
  while (true) {
    if (path.basename(cursor) === "spec") {
      return cursor;
    }
    const parent = path.dirname(cursor);
    if (parent === cursor) {
      return undefined;
    }
    cursor = parent;
  }
}

function resolveValidationRoots(
  specPath: string
): readonly [string, string] | undefined {
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
}

function resolveTargetPaths(value: string, baseDir: string): Set<string> {
  const stripped = value.split("#", 1)[0]?.trim() ?? "";
  const resolved = new Set<string>();
  if (!stripped || URL_SCHEME_RE.test(stripped)) {
    return resolved;
  }
  if (path.isAbsolute(stripped)) {
    resolved.add(path.resolve(stripped));
    return resolved;
  }
  const parts = stripped.split(/[\\/]/).filter((part) => part.length > 0);
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
}

function extractLinkTargets(
  data: JsonRecord,
  sourceFile: string
): readonly { readonly resolved: string; readonly raw: string }[] {
  const rawCalls = data["call"];
  if (!Array.isArray(rawCalls)) {
    return [];
  }
  const seen = new Set<string>();
  const result: { readonly resolved: string; readonly raw: string }[] = [];
  for (const rawCall of rawCalls) {
    const candidate =
      typeof rawCall === "string"
        ? rawCall
        : isRecord(rawCall)
          ? String(rawCall["path"] ?? "")
          : "";
    const text = candidate.trim();
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
}

function matchesFilters(
  record: JsonRecord,
  frontmatter: JsonRecord,
  filters: readonly (readonly [string, readonly string[]])[]
): boolean {
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
}

function validateFrontmatterShape(
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] {
  const errors: string[] = [];
  const required =
    kind === "spec"
      ? ["title", "description", "last_updated", "status", "call"]
      : kind === "research"
        ? ["title", "description", "last_updated", "subject", "tag"]
        : ["title", "description", "last_updated"];
  for (const key of required) {
    if (!(key in data)) {
      errors.push(
        `FAIL [${filePath}]: frontmatter missing required field: ${key}`
      );
    }
  }
  for (const key of ["title", "description", "last_updated"]) {
    if (key in data && (typeof data[key] !== "string" || !data[key])) {
      errors.push(
        `FAIL [${filePath}]: frontmatter field must be a non-empty string: ${key}`
      );
    }
  }
  if (
    typeof data["last_updated"] === "string" &&
    !ISO_DATE_RE.test(data["last_updated"])
  ) {
    errors.push(`FAIL [${filePath}]: last_updated must be YYYY-MM-DD`);
  }
  if (kind === "spec") {
    if (
      typeof data["status"] === "string" &&
      !SPEC_STATUSES.has(data["status"])
    ) {
      errors.push(
        `FAIL [${filePath}]: status is not an allowed value: ${data["status"]}`
      );
    }
    if (!Array.isArray(data["call"])) {
      errors.push(
        `FAIL [${filePath}]: frontmatter field must be an array: call`
      );
    } else {
      for (const item of data["call"]) {
        const link =
          typeof item === "string"
            ? item
            : isRecord(item) && typeof item["path"] === "string"
              ? item["path"]
              : undefined;
        if (!link || !RELATIVE_SPEC_LINK_RE.test(link)) {
          errors.push(
            `FAIL [${filePath}]: call entries must be relative SPEC.md links`
          );
        }
      }
    }
  }
  if (
    (kind === "spec" || kind === "research") &&
    "tag" in data &&
    !Array.isArray(data["tag"])
  ) {
    errors.push(`FAIL [${filePath}]: frontmatter field must be an array: tag`);
  }
  if (kind === "research") {
    const { subject } = data;
    if (
      !isRecord(subject) ||
      typeof subject["name"] !== "string" ||
      !subject["name"] ||
      typeof subject["version"] !== "string" ||
      !subject["version"]
    ) {
      errors.push(
        `FAIL [${filePath}]: subject.name and subject.version are required strings`
      );
    }
  }
  return errors;
}

function validateMarkdownText(
  filePath: string,
  text: string
): readonly string[] {
  const errors: string[] = [];
  if (TODO_RE.test(text)) {
    errors.push(`FAIL [${filePath}]: unresolved todo marker found`);
  }
  if (PLACEHOLDER_RE.test(text)) {
    errors.push(`FAIL [${filePath}]: unresolved placeholder found`);
  }
  if (MARKDOWNLINT_DIRECTIVE_RE.test(text)) {
    errors.push(
      `FAIL [${filePath}]: markdownlint inline directives are not allowed`
    );
  }
  if (MANUAL_NUMBERED_HEADING_RE.test(text)) {
    errors.push(`FAIL [${filePath}]: manual numbered Markdown heading found`);
  }
  if (REVERSE_LINK_RE.test(text)) {
    errors.push(
      `FAIL [${filePath}]: reverse-link sections are not allowed; use call frontmatter`
    );
  }
  if (DEPRECATED_LINK_SECTION_RE.test(text)) {
    errors.push(
      `FAIL [${filePath}]: deprecated Link Maintenance section found`
    );
  }
  for (const line of text.split(/\r?\n/)) {
    if (SCAFFOLDING_LINES.has(line.trim())) {
      errors.push(
        `FAIL [${filePath}]: unresolved scaffolding text: ${line.trim()}`
      );
    }
  }
  return errors;
}

function validateSpecLinks(
  filePath: string,
  data: JsonRecord
): readonly string[] {
  const errors: string[] = [];
  for (const target of extractLinkTargets(data, filePath)) {
    if (!existsSync(target.resolved)) {
      errors.push(`FAIL [${filePath}]: call target not found: ${target.raw}`);
    }
  }
  return errors;
}

function validateDocument(filePath: string, kind: string): ValidationResult {
  const text = readFileSync(filePath, "utf-8");
  const errors: string[] = [];
  let block: FrontmatterBlock | undefined;
  try {
    block = extractFrontmatterFromText(text);
  } catch (error) {
    if (error instanceof Error) {
      return {
        errors: [`FAIL [${filePath}]: ${error.message}`],
        passed: false
      };
    }
    throw error;
  }
  if (!block) {
    return {
      errors: [`FAIL [${filePath}]: No YAML frontmatter found`],
      passed: false
    };
  }
  const data = parseYamlRecord(block.yaml);
  if (!data) {
    return {
      errors: [`FAIL [${filePath}]: Invalid YAML frontmatter`],
      passed: false
    };
  }
  errors.push(
    ...validateFrontmatterShape(filePath, kind, data),
    ...validateMarkdownText(filePath, text),
    ...(kind === "spec" ? validateSpecLinks(filePath, data) : [])
  );
  return { errors, passed: errors.length === 0 };
}

function validateChangelogFile(filePath: string): ValidationResult {
  const text = readFileSync(filePath, "utf-8");
  const errors: string[] = [];
  const dates = [...text.matchAll(CHANGELOG_ENTRY_RE)].map(
    (match) => match[1] ?? ""
  );
  if (dates.length === 0) {
    errors.push(`FAIL [${filePath}]: CHANGELOG.md must include dated entries`);
  }
  const sorted = [...dates].toSorted((left, right) =>
    right.localeCompare(left)
  );
  if (dates.some((date, index) => date !== sorted[index])) {
    errors.push(
      `FAIL [${filePath}]: CHANGELOG.md entries must be newest first`
    );
  }
  errors.push(...validateMarkdownText(filePath, text));
  return { errors, passed: errors.length === 0 };
}

function generateMermaid(specRoot: string): string {
  const specFiles = listByBasename(specRoot, "SPEC.md");
  const lines = ["flowchart TD"];
  const ids = new Map<string, string>();
  for (const filePath of specFiles) {
    const rel = path.relative(specRoot, filePath).split(path.sep).join("/");
    const id = rel.replaceAll(/[^A-Za-z0-9_]/g, "_");
    const entry = loadFrontmatterEntry(filePath);
    const title =
      entry !== undefined && typeof entry !== "string"
        ? String(entry.data["title"] ?? path.basename(path.dirname(filePath)))
        : path.basename(path.dirname(filePath));
    ids.set(filePath, id);
    lines.push(`  ${id}["${title.replaceAll('"', "'")}"]`);
  }
  const edges = new Set<string>();
  for (const filePath of specFiles) {
    const from = ids.get(filePath);
    const entry = loadFrontmatterEntry(filePath);
    if (!from || entry === undefined || typeof entry === "string") {
      continue;
    }
    for (const target of extractLinkTargets(entry.data, filePath)) {
      const to = ids.get(target.resolved);
      if (to) {
        edges.add(`  ${from} --> ${to}`);
      }
    }
  }
  lines.push(...[...edges].toSorted());
  return lines.join("\n");
}

function parseArgs(argv: readonly string[]): ParsedArgs | undefined {
  const [command, ...rest] = argv;
  if (!command) {
    return undefined;
  }
  const options: Record<string, string | boolean | string[]> = {};
  const positionals: string[] = [];
  for (let index = 0; index < rest.length; index += 1) {
    const arg = rest[index] ?? "";
    if (!arg.startsWith("--")) {
      positionals.push(arg);
      continue;
    }
    const rawName = arg.slice(2);
    const [name, inlineValue] = rawName.split("=", 2);
    const multi = name === "filter" || name === "tag";
    const booleanFlag = [
      "jsonl",
      "include-yaml",
      "best-effort",
      "count",
      "help"
    ].includes(name ?? "");
    const value = inlineValue ?? (booleanFlag ? "true" : rest[index + 1]);
    if (!booleanFlag && inlineValue === undefined) {
      index += 1;
    }
    if (!name || value === undefined) {
      return undefined;
    }
    if (multi) {
      const previous = options[name];
      options[name] = Array.isArray(previous) ? [...previous, value] : [value];
    } else {
      options[name] = booleanFlag ? value === "true" : value;
    }
  }
  return { command, options, positionals };
}

function optionString(args: ParsedArgs, name: string): string | undefined {
  const value = args.options[name];
  return typeof value === "string" ? value : undefined;
}

function optionList(args: ParsedArgs, name: string): readonly string[] {
  const value = args.options[name];
  return Array.isArray(value) ? value.map(String) : [];
}

function optionBool(args: ParsedArgs, name: string): boolean {
  return args.options[name] === true;
}

function requireChoice(
  value: string,
  choices: readonly string[],
  label: string
): boolean {
  if (choices.includes(value)) {
    return true;
  }
  fail(`FAIL: invalid ${label}: ${value}`);
  return false;
}

function commandSpecPath(
  args: ParsedArgs,
  index: number,
  label: string
): string | undefined {
  const explicit = args.positionals[index];
  if (explicit) {
    return explicit;
  }
  const fallback = resolveDefaultSpecPath();
  if (fallback) {
    return fallback;
  }
  fail(`FAIL: ${label} is required (no ./spec directory or $SDD_SPEC_DIR set)`);
  return undefined;
}

function cmdGetFrontmatter(args: ParsedArgs): number {
  const kind = args.positionals[0] ?? "";
  const rawPath = args.positionals[1] ?? "";
  const format = optionString(args, "format") ?? "json";
  if (
    !requireChoice(kind, VALID_KINDS, "kind") ||
    !requireChoice(format, VALID_FORMATS, "format")
  ) {
    return 1;
  }
  if (!rawPath) {
    fail("FAIL: path is required");
    return 1;
  }
  const docName = DOC_FILE_NAMES[kind] ?? "";
  let docPath = path.resolve(rawPath);
  if (existsSync(docPath) && statSync(docPath).isDirectory()) {
    docPath = path.join(docPath, docName);
  }
  if (!existsSync(docPath)) {
    fail(`FAIL: Document not found: ${docPath}`);
    return 1;
  }
  if (!statSync(docPath).isFile()) {
    fail(`FAIL: Not a file: ${docPath}`);
    return 1;
  }
  if (path.basename(docPath) !== docName) {
    fail(
      `FAIL: kind=${kind} requires ${docName} (got: ${path.basename(docPath)})`
    );
    return 1;
  }
  const text = readFileSync(docPath, "utf-8");
  if (format === "file") {
    process.stdout.write(text);
    return 0;
  }
  const entry = loadFrontmatterEntry(docPath);
  if (typeof entry === "string") {
    fail(`FAIL: ${entry}: ${docPath}`);
    return 1;
  }
  if (!entry) {
    fail(`FAIL: No YAML frontmatter found: ${docPath}`);
    return 1;
  }
  const outputData: MutableRecord = { ...entry.data };
  if ("tag" in outputData || "tags" in outputData) {
    outputData["tag"] = normalizeTag(outputData["tag"] ?? outputData["tags"]);
  }
  if (format === "yaml") {
    console.log(entry.yamlBody);
    return 0;
  }
  const fields = parseFields(optionString(args, "fields"));
  if (format === "value") {
    if (!fields || fields.length !== 1) {
      fail("FAIL: --format value requires --fields with exactly one field");
      return 1;
    }
    const field = fields[0] ?? "";
    const value =
      field in entry.record ? entry.record[field] : outputData[field];
    process.stdout.write(
      Array.isArray(value) || isRecord(value)
        ? stringifyJson(value)
        : String(value ?? "")
    );
    if (
      value !== undefined &&
      value !== null &&
      !Array.isArray(value) &&
      !isRecord(value)
    ) {
      process.stdout.write("\n");
    }
    return 0;
  }
  const out: MutableRecord = {
    file: docPath,
    frontmatter_end_line: entry.endLine,
    kind
  };
  if (fields) {
    for (const field of fields) {
      if (!["file", "kind", "frontmatter_end_line"].includes(field)) {
        out[field] = outputData[field];
      }
    }
  } else {
    Object.assign(out, outputData);
  }
  if (optionBool(args, "include-yaml")) {
    out["frontmatter_yaml"] = entry.yamlBody;
  }
  console.log(stringifyJson(out, format === "jsonl" ? undefined : 2));
  return 0;
}

function cmdListFrontmatter(args: ParsedArgs): number {
  const specPath = commandSpecPath(args, 0, "spec_path");
  if (!specPath) {
    return 1;
  }
  const kind = optionString(args, "kind") ?? "any";
  if (!requireChoice(kind, LIST_KINDS, "kind")) {
    return 1;
  }
  const jsonl = optionBool(args, "jsonl");
  let includeYaml = optionBool(args, "include-yaml");
  if (includeYaml && !jsonl) {
    warn(
      "--include-yaml is ignored unless --jsonl is set (continuing in table mode)"
    );
    includeYaml = false;
  }
  if (!existsSync(specPath) || !statSync(specPath).isDirectory()) {
    fail(`FAIL: Path is not a directory: ${specPath}`);
    return 1;
  }
  const files = collectMarkdownFiles(path.resolve(specPath)).filter(
    (filePath) => matchesKind(filePath, kind)
  );
  if (files.length === 0) {
    fail(`FAIL: No markdown files found under ${specPath}`);
    return 1;
  }
  const filters: (readonly [string, readonly string[]])[] = [];
  for (const rule of [
    ...optionList(args, "filter"),
    ...optionList(args, "tag").map((tag) => `tag=${tag}`)
  ]) {
    const eq = rule.indexOf("=");
    const key =
      (eq !== -1 ? rule.slice(0, eq) : "").trim() === "tags"
        ? "tag"
        : (eq !== -1 ? rule.slice(0, eq) : "").trim();
    const values =
      eq !== -1
        ? rule
            .slice(eq + 1)
            .split(",")
            .map((value) => value.trim())
            .filter((value) => value.length > 0)
        : [];
    if (!key || values.length === 0) {
      fail(`FAIL: Invalid filter: ${rule}`);
      return 1;
    }
    filters.push([key, values]);
  }
  const fields = parseFields(optionString(args, "fields"));
  const inboundOf = optionString(args, "inbound-of");
  const targetCandidates = inboundOf
    ? resolveTargetPaths(inboundOf, path.resolve(specPath))
    : undefined;
  const output: string[] = [];
  if (!jsonl) {
    output.push(
      inboundOf
        ? "target\tsource\traw_link"
        : fields
          ? ["file", ...fields.filter((field) => field !== "file")].join("\t")
          : "file\ttitle\tstatus\tlast_updated\tupdated\tcreated\ttag\tsubject"
    );
  }
  let failures = 0;
  for (const filePath of files) {
    const entry = loadFrontmatterEntry(filePath);
    if (typeof entry === "string") {
      failures += 1;
      fail(`FAIL [${filePath}]: ${entry}`);
      if (!optionBool(args, "best-effort")) {
        return 1;
      }
      continue;
    }
    if (!entry || !matchesFilters(entry.record, entry.data, filters)) {
      continue;
    }
    if (inboundOf) {
      for (const target of extractLinkTargets(entry.data, filePath)) {
        if (targetCandidates && !targetCandidates.has(target.resolved)) {
          continue;
        }
        const row: MutableRecord = {
          frontmatter_end_line: entry.endLine,
          raw_link: target.raw,
          source: filePath,
          target: inboundOf
        };
        if (includeYaml) {
          row["frontmatter_yaml"] = entry.yamlBody;
        }
        output.push(
          jsonl
            ? stringifyJson(row)
            : [inboundOf, filePath, target.raw].map(sanitizeTsvCell).join("\t")
        );
      }
      continue;
    }
    if (jsonl) {
      const row: MutableRecord = fields
        ? { file: filePath }
        : {
            ...entry.data,
            file: filePath,
            frontmatter_end_line: entry.endLine
          };
      if (fields) {
        for (const field of fields) {
          if (field !== "file") {
            row[field] =
              field === "subject_str"
                ? entry.subjectStr
                : (entry.record[field] ?? entry.data[field] ?? "");
          }
        }
      }
      if (includeYaml) {
        row["frontmatter_yaml"] = entry.yamlBody;
      }
      output.push(stringifyJson(row));
    } else if (fields) {
      output.push(
        [
          entry.record["file"],
          ...fields
            .filter((field) => field !== "file")
            .map((field) => entry.record[field] ?? entry.data[field] ?? "")
        ]
          .map(sanitizeTsvCell)
          .join("\t")
      );
    } else {
      output.push(
        [
          entry.record["file"],
          entry.record["title"],
          entry.record["status"],
          entry.record["last_updated"],
          entry.record["updated"],
          entry.record["created"],
          entry.tags.join(","),
          entry.subjectStr
        ]
          .map(sanitizeTsvCell)
          .join("\t")
      );
    }
  }
  console.log(output.join("\n"));
  if (failures > 0 && optionBool(args, "best-effort")) {
    warn(
      `Skipped ${failures} file(s) due to invalid, empty, or unterminated YAML frontmatter (--best-effort)`
    );
  }
  return 0;
}

function cmdListTags(args: ParsedArgs): number {
  const specPath = commandSpecPath(args, 0, "spec_path");
  if (!specPath) {
    return 1;
  }
  const kind = optionString(args, "kind") ?? "any";
  if (!requireChoice(kind, LIST_KINDS, "kind")) {
    return 1;
  }
  if (!existsSync(specPath) || !statSync(specPath).isDirectory()) {
    fail(`FAIL: Path is not a directory: ${specPath}`);
    return 1;
  }
  const counter = new Map<string, number>();
  for (const filePath of collectMarkdownFiles(path.resolve(specPath)).filter(
    (filePath) => matchesKind(filePath, kind)
  )) {
    const entry = loadFrontmatterEntry(filePath);
    if (typeof entry === "string") {
      fail(`FAIL [${filePath}]: ${entry}`);
      return 1;
    }
    if (!entry) {
      continue;
    }
    for (const tag of entry.tags) {
      counter.set(tag, (counter.get(tag) ?? 0) + 1);
    }
  }
  for (const tag of [...counter.keys()].toSorted()) {
    console.log(
      optionBool(args, "count") ? `${tag}\t${counter.get(tag) ?? 0}` : tag
    );
  }
  return 0;
}

function cmdGenerateDiagram(args: ParsedArgs): number {
  const specRoot = commandSpecPath(args, 0, "spec_root");
  if (!specRoot) {
    return 1;
  }
  if (!existsSync(specRoot) || !statSync(specRoot).isDirectory()) {
    fail(`FAIL: Directory not found: ${specRoot}`);
    return 1;
  }
  console.log(generateMermaid(path.resolve(specRoot)));
  return 0;
}

function cmdValidate(args: ParsedArgs): number {
  const specPathArg = commandSpecPath(args, 0, "spec_path");
  if (!specPathArg) {
    return 1;
  }
  if (!existsSync(specPathArg) || !statSync(specPathArg).isDirectory()) {
    fail(`FAIL: Path is not a directory: ${specPathArg}`);
    return 1;
  }
  const roots = resolveValidationRoots(specPathArg);
  if (!roots) {
    fail(
      `FAIL: Path must be under spec/ or contain a spec/ directory: ${specPathArg}`
    );
    return 1;
  }
  const [specRoot, scanRoot] = roots;
  for (const schemaName of [
    "spec-frontmatter.schema.json",
    "research-frontmatter.schema.json",
    "contract-frontmatter.schema.json"
  ]) {
    const schemaPath = path.join(skillRoot(), "assets", "schemas", schemaName);
    if (!existsSync(schemaPath)) {
      fail(`FAIL: Schema not found: ${schemaPath}`);
      return 1;
    }
  }
  const specFiles = listByBasename(scanRoot, "SPEC.md");
  const researchFiles = listByBasename(scanRoot, "RESEARCH.md");
  const contractFiles = listByBasename(scanRoot, "CONTRACT.md");
  if (
    specFiles.length === 0 &&
    researchFiles.length === 0 &&
    contractFiles.length === 0
  ) {
    fail(
      `FAIL: No SPEC.md, RESEARCH.md, or CONTRACT.md files found under ${scanRoot}`
    );
    return 1;
  }
  let total = 0;
  let passed = 0;
  let failed = 0;
  let changelogLayoutFailures = 0;
  const apply = (result: ValidationResult): void => {
    total += 1;
    for (const error of result.errors) {
      fail(error);
    }
    if (result.passed) {
      passed += 1;
    } else {
      failed += 1;
    }
  };
  for (const filePath of specFiles) {
    apply(validateDocument(filePath, "spec"));
  }
  for (const filePath of researchFiles) {
    apply(validateDocument(filePath, "research"));
  }
  for (const filePath of contractFiles) {
    apply(validateDocument(filePath, "contract"));
  }
  const changelogPath = path.join(specRoot, "CHANGELOG.md");
  if (existsSync(changelogPath)) {
    const result = validateChangelogFile(changelogPath);
    if (!result.passed) {
      changelogLayoutFailures += 1;
    }
    apply(result);
  }
  console.log("Validation Summary");
  console.log(`- Total checks: ${total}`);
  console.log(`- Passed: ${passed}`);
  console.log(`- Failed: ${failed}`);
  console.log(`- Changelog layout failures: ${changelogLayoutFailures}`);
  if (failed > 0) {
    return 1;
  }
  console.log(`OK: Validation complete for ${scanRoot}`);
  return 0;
}

function printHelp(): void {
  const name = cliName();
  console.error(`usage: ${name} <command> [options]`);
  console.error("");
  console.error("Available subcommands:");
  console.error(
    "  get-frontmatter <kind> <path> [--format json|jsonl|yaml|value|file] [--fields a,b] [--include-yaml]"
  );
  console.error(
    "  list-frontmatter [spec_path] [--kind any|spec|research|contract] [--jsonl] [--fields a,b] [--filter KEY=V1,V2] [--tag VALUE] [--inbound-of SPEC_PATH] [--best-effort]"
  );
  console.error(
    "  list-tags [spec_path] [--kind any|spec|research|contract] [--count]"
  );
  console.error("  generate-diagram [spec_root]");
  console.error("  validate [spec_path]");
}

function main(argv: readonly string[]): number {
  const args = parseArgs(argv);
  if (
    !args ||
    args.command === "--help" ||
    args.command === "-h" ||
    optionBool(args, "help")
  ) {
    printHelp();
    return args ? 0 : 1;
  }
  switch (args.command) {
    case "get-frontmatter": {
      return cmdGetFrontmatter(args);
    }
    case "list-frontmatter": {
      return cmdListFrontmatter(args);
    }
    case "list-tags": {
      return cmdListTags(args);
    }
    case "generate-diagram": {
      return cmdGenerateDiagram(args);
    }
    case "validate": {
      return cmdValidate(args);
    }
    default: {
      fail(`FAIL: unknown command: ${args.command}`);
      printHelp();
      return 1;
    }
  }
}

process.exitCode = main(Bun.argv.slice(2));
