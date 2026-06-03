#!/usr/bin/env bun
// -*- coding: utf-8 -*-
import { expect, test } from "bun:test";
import { OXLINT_CODE_TO_CATEGORY, OXLINT_FIX_SAFETY, OXLINT_CATEGORIES } from "./oxlint-code-map";

test("OXLINT_CODE_TO_CATEGORY has all 8 codes", () => {
  const codes = [
    "harness(greaterThanComparison)",
    "harness(multilineDocStyle)",
    "harness(publicDeclarationDocComment)",
    "eslint(no-console)",
    "eslint(no-empty)",
    "eslint(curly)",
    "eslint(no-underscore-dangle)",
    "import(no-namespace)",
    "typescript(ban-ts-comment)",
  ];
  codes.forEach((code) => {
    expect(OXLINT_CODE_TO_CATEGORY[code]).toBeDefined();
  });
  expect(Object.keys(OXLINT_CODE_TO_CATEGORY).length).toBe(9);
});

test("OXLINT_FIX_SAFETY has entries for all categories", () => {
  const expectedCategories = Object.values(OXLINT_CODE_TO_CATEGORY);
  expectedCategories.forEach((category) => {
    expect(OXLINT_FIX_SAFETY[category]).toBeDefined();
    expect(["safe", "unsafe", "manual"]).toContain(OXLINT_FIX_SAFETY[category]);
  });
});

test("OXLINT_CATEGORIES matches OXLINT_FIX_SAFETY", () => {
  OXLINT_CATEGORIES.forEach((category) => {
    expect(OXLINT_FIX_SAFETY[category]).toBeDefined();
  });
  expect(OXLINT_CATEGORIES.length).toBe(12);
});

test("code to category mappings are consistent", () => {
  const codeToCategory = OXLINT_CODE_TO_CATEGORY;
  const categories = new Set(Object.values(codeToCategory));
  expect(categories.size).toBe(12);
  categories.forEach((category) => {
    expect(OXLINT_CATEGORIES).toContain(category);
  });
});
