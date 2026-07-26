import { optionList } from "./args.js";
import { fail } from "./infrastructure.js";
import type { ParsedArgs } from "./shared.js";

const parseFilterKey = (rule: string, eq: number): string => {
  const rawKey = eq === -1 ? "" : rule.slice(0, eq);
  const trimmed = rawKey.trim();
  return trimmed === "tags" ? "tag" : trimmed;
};

const parseFilterValues = (rule: string, eq: number): readonly string[] =>
  eq === -1
    ? []
    : rule
        .slice(eq + 1)
        .split(",")
        .map((value) => value.trim())
        .filter((value) => value.length > 0);

const parseFilterRule = (
  rule: string
): readonly [string, readonly string[]] | undefined => {
  const eq = rule.indexOf("=");
  const key = parseFilterKey(rule, eq);
  const values = parseFilterValues(rule, eq);
  if (!key || values.length === 0) {
    fail(`FAIL: Invalid filter: ${rule}`);
    return undefined;
  }
  return [key, values];
};

export const buildFilters = (
  args: ParsedArgs
): readonly (readonly [string, readonly string[]])[] | number => {
  const rules = [
    ...optionList(args, "filter"),
    ...optionList(args, "tag").map((tag) => `tag=${tag}`)
  ];
  const filters: (readonly [string, readonly string[]])[] = [];
  for (const rule of rules) {
    const parsed = parseFilterRule(rule);
    if (!parsed) {
      return 1;
    }
    filters.push(parsed);
  }
  return filters;
};
