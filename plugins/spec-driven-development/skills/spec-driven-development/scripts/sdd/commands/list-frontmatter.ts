import { existsSync, statSync } from "node:fs";
import path from "node:path";

import {
  commandSpecPath,
  optionBool,
  optionString,
  requireChoice
} from "../args.js";
import { buildFilters } from "../filters.js";
import {
  loadFrontmatterEntry,
  matchesFilters,
  matchesKind,
  parseFields
} from "../frontmatter.js";
import {
  collectMarkdownFiles,
  fail,
  sanitizeTsvCell,
  stringifyJson,
  warn
} from "../infrastructure.js";
import { extractLinkTargets, resolveTargetPaths } from "../links.js";
import type { LinkTarget } from "../links.js";
import { LIST_KINDS } from "../shared.js";
import type { LoadEntry, MutableRecord, ParsedArgs } from "../shared.js";

const buildListHeader = (
  inboundOf: string | undefined,
  fields: readonly string[] | undefined
): string => {
  if (inboundOf) {
    return "target\tsource\traw_link";
  }
  if (fields) {
    return ["file", ...fields.filter((field) => field !== "file")].join("\t");
  }
  return "file\ttitle\tstatus\tlast_updated\tupdated\tcreated\ttag\tsubject";
};

const formatInboundRow = (
  entry: LoadEntry,
  filePath: string,
  target: LinkTarget,
  inboundOf: string,
  includeYaml: boolean,
  jsonl: boolean
): string => {
  const row: MutableRecord = {
    frontmatter_end_line: entry.endLine,
    raw_link: target.raw,
    source: filePath,
    target: inboundOf
  };
  if (includeYaml) {
    row["frontmatter_yaml"] = entry.yamlBody;
  }
  return jsonl
    ? stringifyJson(row)
    : [inboundOf, filePath, target.raw].map(sanitizeTsvCell).join("\t");
};

const formatJsonlRow = (
  entry: LoadEntry,
  filePath: string,
  fields: readonly string[] | undefined,
  includeYaml: boolean
): string => {
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
  return stringifyJson(row);
};

const formatFieldsTsvRow = (
  entry: LoadEntry,
  fields: readonly string[]
): string =>
  [
    entry.record["file"],
    ...fields
      .filter((field) => field !== "file")
      .map((field) => entry.record[field] ?? entry.data[field] ?? "")
  ]
    .map(sanitizeTsvCell)
    .join("\t");

const formatDefaultTsvRow = (entry: LoadEntry): string =>
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
    .join("\t");

const formatStandardRow = (
  entry: LoadEntry,
  filePath: string,
  fields: readonly string[] | undefined,
  jsonl: boolean,
  includeYaml: boolean
): string => {
  if (jsonl) {
    return formatJsonlRow(entry, filePath, fields, includeYaml);
  }
  if (fields) {
    return formatFieldsTsvRow(entry, fields);
  }
  return formatDefaultTsvRow(entry);
};

const collectInboundRows = (
  entry: LoadEntry,
  filePath: string,
  inboundOf: string,
  targetCandidates: Set<string> | undefined,
  includeYaml: boolean,
  jsonl: boolean
): readonly string[] => {
  const rows: string[] = [];
  for (const target of extractLinkTargets(entry.data, filePath)) {
    if (targetCandidates && !targetCandidates.has(target.resolved)) {
      continue;
    }
    rows.push(
      formatInboundRow(entry, filePath, target, inboundOf, includeYaml, jsonl)
    );
  }
  return rows;
};

export const cmdListFrontmatter = (args: ParsedArgs): number => {
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
    (candidate) => matchesKind(candidate, kind)
  );
  if (files.length === 0) {
    fail(`FAIL: No markdown files found under ${specPath}`);
    return 1;
  }
  const filters = buildFilters(args);
  if (typeof filters === "number") {
    return filters;
  }
  const fields = parseFields(optionString(args, "fields"));
  const inboundOf = optionString(args, "inbound-of");
  const targetCandidates = inboundOf
    ? resolveTargetPaths(inboundOf, path.resolve(specPath))
    : undefined;
  const output: string[] = [];
  if (!jsonl) {
    output.push(buildListHeader(inboundOf, fields));
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
      output.push(
        ...collectInboundRows(
          entry,
          filePath,
          inboundOf,
          targetCandidates,
          includeYaml,
          jsonl
        )
      );
      continue;
    }
    output.push(formatStandardRow(entry, filePath, fields, jsonl, includeYaml));
  }
  console.log(output.join("\n"));
  if (failures > 0 && optionBool(args, "best-effort")) {
    warn(
      `Skipped ${failures} file(s) due to invalid, empty, or unterminated YAML frontmatter (--best-effort)`
    );
  }
  return 0;
};
