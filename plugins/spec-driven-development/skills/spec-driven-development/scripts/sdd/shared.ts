export type JsonRecord = Readonly<Record<string, unknown>>;
export type MutableRecord = Record<string, unknown>;

export interface FrontmatterBlock {
  readonly yaml: string;
  readonly endLine: number;
}

export interface LoadEntry {
  readonly yamlBody: string;
  readonly endLine: number;
  readonly data: MutableRecord;
  readonly record: MutableRecord;
  readonly subjectStr: string;
  readonly tags: readonly string[];
}

export interface ValidationResult {
  readonly errors: readonly string[];
  readonly passed: boolean;
}

export interface ParsedArgs {
  readonly command: string;
  readonly positionals: readonly string[];
  readonly options: Record<string, string | boolean | readonly string[]>;
}

export const VALID_KINDS = ["spec", "research", "contract"] as const;
export const LIST_KINDS = ["any", "spec", "research", "contract"] as const;
export const VALID_FORMATS = [
  "json",
  "jsonl",
  "yaml",
  "value",
  "file"
] as const;

export const DOC_FILE_NAMES: Record<string, string> = {
  contract: "CONTRACT.md",
  research: "RESEARCH.md",
  spec: "SPEC.md"
};

export const SPEC_STATUSES = new Set([
  "draft",
  "review",
  "approved",
  "wip",
  "implemented",
  "deprecated",
  "superseded",
  "removed"
]);

export const FRONTMATTER_DELIMITER_RE = /^---[ \t]*$/u;
export const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/u;
export const URL_SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/u;
export const RELATIVE_SPEC_LINK_RE =
  /^(?![a-zA-Z][a-zA-Z0-9+.-]*:)(?!\/)(?![A-Za-z]:[\\/])(?:\.\/|\.\.\/|[^/][^/]*\/)*SPEC\.md(?:#[^\s]+)?$/u;
export const CHANGELOG_ENTRY_RE =
  /^[ \t]{0,3}##[ \t]+(?<date>\d{4}-\d{2}-\d{2})[ \t]+-[ \t]+\S.*$/gmu;
