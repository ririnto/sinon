import { existsSync, readFileSync, statSync } from "node:fs";
import path from "node:path";

import { optionBool, optionString, requireChoice } from "../args.js";
import {
  loadFrontmatterEntry,
  normalizeTag,
  parseFields
} from "../frontmatter.js";
import { fail, isRecord, stringifyJson } from "../infrastructure.js";
import { DOC_FILE_NAMES, VALID_FORMATS, VALID_KINDS } from "../shared.js";
import type { LoadEntry, MutableRecord, ParsedArgs } from "../shared.js";

const resolveDocPath = (kind: string, rawPath: string): string | undefined => {
  const docName = DOC_FILE_NAMES[kind] ?? "";
  let docPath = path.resolve(rawPath);
  if (existsSync(docPath) && statSync(docPath).isDirectory()) {
    docPath = path.join(docPath, docName);
  }
  if (!existsSync(docPath)) {
    fail(`FAIL: Document not found: ${docPath}`);
    return undefined;
  }
  if (!statSync(docPath).isFile()) {
    fail(`FAIL: Not a file: ${docPath}`);
    return undefined;
  }
  if (path.basename(docPath) !== docName) {
    fail(
      `FAIL: kind=${kind} requires ${docName} (got: ${path.basename(docPath)})`
    );
    return undefined;
  }
  return docPath;
};

const outputValue = (
  entry: LoadEntry,
  outputData: MutableRecord,
  fields: readonly string[] | undefined
): number => {
  if (!fields || fields.length !== 1) {
    fail("FAIL: --format value requires --fields with exactly one field");
    return 1;
  }
  const field = fields[0] ?? "";
  const value = field in entry.record ? entry.record[field] : outputData[field];
  if (Array.isArray(value) || isRecord(value)) {
    process.stdout.write(stringifyJson(value));
  } else {
    process.stdout.write(`${String(value ?? "")}\n`);
  }
  return 0;
};

const outputJson = (
  args: ParsedArgs,
  entry: LoadEntry,
  outputData: MutableRecord,
  docPath: string,
  kind: string,
  format: string,
  fields: readonly string[] | undefined
): number => {
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
};

export const cmdGetFrontmatter = (args: ParsedArgs): number => {
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
  const docPath = resolveDocPath(kind, rawPath);
  if (!docPath) {
    return 1;
  }
  if (format === "file") {
    process.stdout.write(readFileSync(docPath, "utf-8"));
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
    return outputValue(entry, outputData, fields);
  }
  return outputJson(args, entry, outputData, docPath, kind, format, fields);
};
