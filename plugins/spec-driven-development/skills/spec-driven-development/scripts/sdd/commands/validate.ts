import { existsSync, statSync } from "node:fs";
import path from "node:path";

import { commandSpecPath } from "../args.js";
import { fail, listByBasename, skillRoot } from "../infrastructure.js";
import { resolveValidationRoots } from "../links.js";
import type { ParsedArgs, ValidationResult } from "../shared.js";
import { validateChangelogFile, validateDocument } from "../validation.js";

export const cmdValidate = (args: ParsedArgs): number => {
  const specPathArg = commandSpecPath(args, 0, "spec_path");
  if (!specPathArg) {
    return 1;
  }
  if (!existsSync(specPathArg) || !statSync(specPathArg).isDirectory()) {
    fail(`FAIL: Path is not a directory: ${specPathArg}`);
    return 1;
  }
  const roots = resolveValidationRoots(specPathArg);
  if (!roots) {
    fail(
      `FAIL: Path must be under spec/ or contain a spec/ directory: ${specPathArg}`
    );
    return 1;
  }
  const [specRoot, scanRoot] = roots;
  for (const schemaName of [
    "spec-frontmatter.schema.json",
    "research-frontmatter.schema.json",
    "contract-frontmatter.schema.json"
  ]) {
    const schemaPath = path.join(skillRoot(), "assets", "schemas", schemaName);
    if (!existsSync(schemaPath)) {
      fail(`FAIL: Schema not found: ${schemaPath}`);
      return 1;
    }
  }
  const specFiles = listByBasename(scanRoot, "SPEC.md");
  const researchFiles = listByBasename(scanRoot, "RESEARCH.md");
  const contractFiles = listByBasename(scanRoot, "CONTRACT.md");
  if (
    specFiles.length === 0 &&
    researchFiles.length === 0 &&
    contractFiles.length === 0
  ) {
    fail(
      `FAIL: No SPEC.md, RESEARCH.md, or CONTRACT.md files found under ${scanRoot}`
    );
    return 1;
  }
  let total = 0;
  let passed = 0;
  let failed = 0;
  let changelogLayoutFailures = 0;
  const apply = (result: ValidationResult): void => {
    total += 1;
    for (const error of result.errors) {
      fail(error);
    }
    if (result.passed) {
      passed += 1;
    } else {
      failed += 1;
    }
  };
  for (const filePath of specFiles) {
    apply(validateDocument(filePath, "spec"));
  }
  for (const filePath of researchFiles) {
    apply(validateDocument(filePath, "research"));
  }
  for (const filePath of contractFiles) {
    apply(validateDocument(filePath, "contract"));
  }
  const changelogPath = path.join(specRoot, "CHANGELOG.md");
  if (existsSync(changelogPath)) {
    const result = validateChangelogFile(changelogPath);
    if (!result.passed) {
      changelogLayoutFailures += 1;
    }
    apply(result);
  }
  console.log("Validation Summary");
  console.log(`- Total checks: ${total}`);
  console.log(`- Passed: ${passed}`);
  console.log(`- Failed: ${failed}`);
  console.log(`- Changelog layout failures: ${changelogLayoutFailures}`);
  if (failed > 0) {
    return 1;
  }
  console.log(`OK: Validation complete for ${scanRoot}`);
  return 0;
};
