import { optionList } from "./args.js";
import { fail } from "./infrastructure.js";
import type { FilterBuildResult, FilterRule, ParsedArgs } from "./shared.js";

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

const parseFilterRule = (rule: string): FilterRule | undefined => {
  const eq = rule.indexOf("=");
  const key = parseFilterKey(rule, eq);
  const values = parseFilterValues(rule, eq);
  return key && values.length > 0 ? [key, values] : undefined;
};

/**
 * Converts command-line filter and tag options into filter rules; the
 * discriminated result carries the first rule that fails to parse.
 */
export const buildFilters = (args: ParsedArgs): FilterBuildResult => {
  const rules = [
    ...optionList(args, "filter"),
    ...optionList(args, "tag").map((tag) => `tag=${tag}`)
  ];
  const filters: FilterRule[] = [];
  for (const rule of rules) {
    const parsed = parseFilterRule(rule);
    if (!parsed) {
      const message = `Invalid filter: ${rule}`;
      fail(`FAIL: ${message}`);
      return { kind: "error", message };
    }
    filters.push(parsed);
  }
  return { filters, kind: "filters" };
};
