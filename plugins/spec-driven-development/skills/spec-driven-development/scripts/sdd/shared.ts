/**
 * Read-only record holding arbitrary parsed JSON values.
 */
export type JsonRecord = Readonly<Record<string, unknown>>;

/**
 * Record that parsing and output stages may mutate while building results.
 */
export type MutableRecord = Record<string, unknown>;

/**
 * Stores the YAML frontmatter body and the line number of its closing
 * delimiter.
 */
export interface FrontmatterBlock {
  readonly yaml: string;
  readonly endLine: number;
}

/**
 * Stores values derived from one document's frontmatter.
 */
export interface LoadEntry {
  readonly yamlBody: string;
  readonly endLine: number;
  readonly data: MutableRecord;
  readonly record: MutableRecord;
  readonly subjectStr: string;
  readonly tags: readonly string[];
}

/**
 * Discriminates frontmatter load results into entry, missing, and error.
 */
export type LoadEntryResult =
  | { readonly kind: "entry"; readonly entry: LoadEntry }
  | { readonly kind: "missing" }
  | { readonly kind: "error"; readonly message: string };

/**
 * One filter key with its allowed values.
 */
export type FilterRule = readonly [string, readonly string[]];

/**
 * Discriminates filter construction into success and error.
 */
export type FilterBuildResult =
  | { readonly kind: "filters"; readonly filters: readonly FilterRule[] }
  | { readonly kind: "error"; readonly message: string };

/**
 * Stores document validation errors and the overall pass flag.
 */
export interface ValidationResult {
  readonly errors: readonly string[];
  readonly passed: boolean;
}

/**
 * Stores the command, positionals, and options parsed from argv.
 */
export interface ParsedArgs {
  readonly command: string;
  readonly positionals: readonly string[];
  readonly options: Record<string, string | boolean | readonly string[]>;
}

/**
 * Allowed values for a single document kind.
 */
export const VALID_KINDS = ["spec", "research", "contract"] as const;

/**
 * Document kinds accepted by the list commands.
 */
export const LIST_KINDS = ["any", "spec", "research", "contract"] as const;

/**
 * Formats accepted by frontmatter output.
 */
export const VALID_FORMATS = [
  "json",
  "jsonl",
  "yaml",
  "value",
  "file"
] as const;

/**
 * Maps each document kind to its file name.
 */
export const DOC_FILE_NAMES: Record<string, string> = {
  contract: "CONTRACT.md",
  research: "RESEARCH.md",
  spec: "SPEC.md"
};

/**
 * Status values allowed in SPEC documents.
 */
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

/**
 * Matches a YAML frontmatter delimiter line.
 */
export const FRONTMATTER_DELIMITER_RE = /^---[ \t]*$/u;

/**
 * Matches an ISO date in YYYY-MM-DD form.
 */
export const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/u;

/**
 * Matches values that start with a URI scheme.
 */
export const URL_SCHEME_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/u;

/**
 * Matches relative SPEC.md links, optionally with a fragment.
 */
export const RELATIVE_SPEC_LINK_RE =
  /^(?![a-zA-Z][a-zA-Z0-9+.-]*:)(?!\/)(?![A-Za-z]:[\\/])(?:\.\/|\.\.\/|[^/][^/]*\/)*SPEC\.md(?:#[^\s]+)?$/u;

/**
 * Matches dated changelog headings and captures the date.
 */
export const CHANGELOG_ENTRY_RE =
  /^[ \t]{0,3}##[ \t]+(?<date>\d{4}-\d{2}-\d{2})[ \t]+-[ \t]+\S.*$/gmu;
