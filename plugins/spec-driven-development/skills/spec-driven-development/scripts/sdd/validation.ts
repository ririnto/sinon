import { existsSync, readFileSync } from "node:fs";

import { extractFrontmatterFromText } from "./frontmatter.js";
import { isRecord, parseYamlRecord } from "./infrastructure.js";
import { extractLinkTargets } from "./links.js";
import {
  CHANGELOG_ENTRY_RE,
  ISO_DATE_RE,
  RELATIVE_SPEC_LINK_RE,
  SPEC_STATUSES
} from "./shared.js";
import type {
  FrontmatterBlock,
  JsonRecord,
  ValidationResult
} from "./shared.js";

const REQUIRED_FIELDS = new Map<string, readonly string[]>([
  ["contract", ["title", "description", "last_updated"]],
  ["research", ["title", "description", "last_updated", "subject", "tag"]],
  ["spec", ["title", "description", "last_updated", "status", "call"]]
]);

const requiredFieldsForKind = (kind: string): readonly string[] =>
  REQUIRED_FIELDS.get(kind) ?? ["title", "description", "last_updated"];

const STRING_FIELDS = ["title", "description", "last_updated"] as const;

const validateRequiredFields = (
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] => {
  const errors: string[] = [];
  for (const key of requiredFieldsForKind(kind)) {
    if (!(key in data)) {
      errors.push(
        `FAIL [${filePath}]: frontmatter missing required field: ${key}`
      );
    }
  }
  return errors;
};

const validateStringFields = (
  filePath: string,
  data: JsonRecord
): readonly string[] => {
  const errors: string[] = [];
  for (const key of STRING_FIELDS) {
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
  return errors;
};

const extractCallLink = (item: unknown): string | undefined => {
  if (typeof item === "string") {
    return item;
  }
  if (isRecord(item) && typeof item["path"] === "string") {
    return item["path"];
  }
  return undefined;
};

const validateSpecFields = (
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] => {
  if (kind !== "spec") {
    return [];
  }
  const errors: string[] = [];
  if (
    typeof data["status"] === "string" &&
    !SPEC_STATUSES.has(data["status"])
  ) {
    errors.push(
      `FAIL [${filePath}]: status is not an allowed value: ${data["status"]}`
    );
  }
  if (Array.isArray(data["call"])) {
    for (const item of data["call"]) {
      const link = extractCallLink(item);
      if (!link || !RELATIVE_SPEC_LINK_RE.test(link)) {
        errors.push(
          `FAIL [${filePath}]: call entries must be relative SPEC.md links`
        );
      }
    }
  } else {
    errors.push(`FAIL [${filePath}]: frontmatter field must be an array: call`);
  }
  return errors;
};

const validateTagField = (
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] => {
  if (
    (kind === "spec" || kind === "research") &&
    "tag" in data &&
    !Array.isArray(data["tag"])
  ) {
    return [`FAIL [${filePath}]: frontmatter field must be an array: tag`];
  }
  return [];
};

const validateResearchSubject = (
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] => {
  if (kind !== "research") {
    return [];
  }
  const { subject } = data;
  if (
    !isRecord(subject) ||
    typeof subject["name"] !== "string" ||
    !subject["name"] ||
    typeof subject["version"] !== "string" ||
    !subject["version"]
  ) {
    return [
      `FAIL [${filePath}]: subject.name and subject.version are required strings`
    ];
  }
  return [];
};

export const validateFrontmatterShape = (
  filePath: string,
  kind: string,
  data: JsonRecord
): readonly string[] => [
  ...validateRequiredFields(filePath, kind, data),
  ...validateStringFields(filePath, data),
  ...validateSpecFields(filePath, kind, data),
  ...validateTagField(filePath, kind, data),
  ...validateResearchSubject(filePath, kind, data)
];

export const validateSpecLinks = (
  filePath: string,
  data: JsonRecord
): readonly string[] => {
  const errors: string[] = [];
  for (const target of extractLinkTargets(data, filePath)) {
    if (!existsSync(target.resolved)) {
      errors.push(`FAIL [${filePath}]: call target not found: ${target.raw}`);
    }
  }
  return errors;
};

export const validateDocument = (
  filePath: string,
  kind: string
): ValidationResult => {
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
    ...(kind === "spec" ? validateSpecLinks(filePath, data) : [])
  );
  return { errors, passed: errors.length === 0 };
};

export const validateChangelogFile = (filePath: string): ValidationResult => {
  const text = readFileSync(filePath, "utf-8");
  const errors: string[] = [];
  const dates = [...text.matchAll(CHANGELOG_ENTRY_RE)].map(
    (match) => match.groups?.date ?? ""
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
  return { errors, passed: errors.length === 0 };
};
