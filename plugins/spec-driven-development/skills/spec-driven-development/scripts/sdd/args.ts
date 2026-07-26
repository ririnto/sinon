import { parseArgs as parseNodeArgs } from "node:util";

import { cliName, fail, resolveDefaultSpecPath } from "./infrastructure.js";
import type { ParsedArgs } from "./shared.js";

const SDD_OPTIONS = {
  "best-effort": { type: "boolean" },
  count: { type: "boolean" },
  fields: { type: "string" },
  filter: { multiple: true, type: "string" },
  format: { type: "string" },
  help: { short: "h", type: "boolean" },
  "inbound-of": { type: "string" },
  "include-yaml": { type: "boolean" },
  jsonl: { type: "boolean" },
  kind: { type: "string" },
  tag: { multiple: true, type: "string" }
} as const;

export const parseArgs = (argv: readonly string[]): ParsedArgs | undefined => {
  const [command, ...rest] = argv;
  if (!command) {
    return undefined;
  }
  if (command === "--help" || command === "-h") {
    return { command, options: { help: true }, positionals: [] };
  }
  try {
    const { positionals, values } = parseNodeArgs({
      allowPositionals: true,
      args: rest,
      options: SDD_OPTIONS,
      strict: true
    });
    return { command, options: values, positionals };
  } catch (error) {
    if (error instanceof TypeError) {
      return undefined;
    }
    throw error;
  }
};

export const optionString = (
  args: ParsedArgs,
  name: string
): string | undefined => {
  const value = args.options[name];
  return typeof value === "string" ? value : undefined;
};

export const optionList = (
  args: ParsedArgs,
  name: string
): readonly string[] => {
  const value = args.options[name];
  return Array.isArray(value) ? value.map(String) : [];
};

export const optionBool = (args: ParsedArgs, name: string): boolean =>
  args.options[name] === true;

export const requireChoice = (
  value: string,
  choices: readonly string[],
  label: string
): boolean => {
  if (choices.includes(value)) {
    return true;
  }
  fail(`FAIL: invalid ${label}: ${value}`);
  return false;
};

export const commandSpecPath = (
  args: ParsedArgs,
  index: number,
  label: string
): string | undefined => {
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
};

export const printHelp = (): void => {
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
};
