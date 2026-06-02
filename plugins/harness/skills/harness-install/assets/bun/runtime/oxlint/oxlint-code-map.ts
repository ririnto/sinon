#!/usr/bin/env bun
// -*- coding: utf-8 -*-

/**
 * Maps oxlint diagnostic `code` (paren form) to harness manifest category.
 * The `code` field in oxlint JSON output uses paren form: `eslint(no-console)`.
 */
export const OXLINT_CODE_TO_CATEGORY: Record<string, string> = {
  "harness(greaterThanComparison)": "greaterThanComparison",
  "harness(earlyReturn)": "earlyReturn",
  "harness(silentCatch)": "silentCatch",
  "harness(importOverFqn)": "importOverFqn",
  "harness(multilineDocStyle)": "multilineDocStyle",
  "harness(publicDeclarationDocComment)": "publicDeclarationDocComment",
  "eslint(no-console)": "unstructuredLogging",
  "eslint(no-empty)": "emptyCatchBlock",
  "eslint(curly)": "ifStatementBraces",
  "eslint(no-underscore-dangle)": "leadingUnderscore",
  "import(no-namespace)": "wildcardImport",
  "typescript(ban-ts-comment)": "uncheckedCastSuppression",
};

/**
 * Fix safety classification per category.
 * Mechanically safe single-token rewrites: "safe".
 * Human judgment required or unsafe transformation: "unsafe".
 * Manual verification needed: "manual".
 */
export const OXLINT_FIX_SAFETY: Record<string, "safe" | "unsafe" | "manual"> = {
  greaterThanComparison: "unsafe",
  earlyReturn: "unsafe",
  silentCatch: "unsafe",
  importOverFqn: "unsafe",
  multilineDocStyle: "safe",
  publicDeclarationDocComment: "manual",
  unstructuredLogging: "unsafe",
  emptyCatchBlock: "unsafe",
  ifStatementBraces: "safe",
  leadingUnderscore: "unsafe",
  wildcardImport: "unsafe",
  uncheckedCastSuppression: "manual",
};

/**
 * Exhaustive list of oxlint-owned categories (both built-in and custom plugin).
 * Used to extend knownCategories in harness-check so oxlint manifest keys
 * don't trigger "unknown manifest key" warnings after the 14 AST rule modules are deleted.
 */
export const OXLINT_CATEGORIES = [
  "greaterThanComparison",
  "earlyReturn",
  "silentCatch",
  "importOverFqn",
  "multilineDocStyle",
  "publicDeclarationDocComment",
  "unstructuredLogging",
  "emptyCatchBlock",
  "ifStatementBraces",
  "leadingUnderscore",
  "wildcardImport",
  "uncheckedCastSuppression",
] as const;
