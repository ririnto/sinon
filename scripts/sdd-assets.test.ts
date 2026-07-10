// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

interface FrontmatterSchema {
  properties?: Readonly<
    Record<string, { type?: "array" | "object" | "string" }>
  >;
  required?: readonly string[];
}

const REPOSITORY_ROOT = path.resolve(import.meta.dirname, "..");
const SKILL_ROOT = path.join(
  REPOSITORY_ROOT,
  "plugins/spec-driven-development/skills/spec-driven-development"
);
const TEMPLATE_CASES = [
  ["SPEC.md", "spec-frontmatter.schema.json"],
  ["RESEARCH.md", "research-frontmatter.schema.json"],
  ["CONTRACT.md", "contract-frontmatter.schema.json"]
] as const;

const parseFrontmatter = (
  source: string
): Readonly<Record<string, unknown>> => {
  const lines = source.split(/\r?\n/u);
  if (lines[0] !== "---") {
    throw new Error("template must start with YAML frontmatter");
  }
  const closing = lines.indexOf("---", 1);
  if (closing === -1) {
    throw new Error("template is missing its closing frontmatter delimiter");
  }
  const parsed: unknown = Bun.YAML.parse(lines.slice(1, closing).join("\n"));
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("template frontmatter must be a mapping");
  }
  return parsed as Readonly<Record<string, unknown>>;
};

test("SDD templates mirror their frontmatter schemas", async () => {
  await Promise.all(
    TEMPLATE_CASES.map(async ([templateName, schemaName]) => {
      const template = await Bun.file(
        path.join(SKILL_ROOT, "assets/templates", templateName)
      ).text();
      const schema = (await Bun.file(
        path.join(SKILL_ROOT, "assets/schemas", schemaName)
      ).json()) as FrontmatterSchema;
      const frontmatter = parseFrontmatter(template);
      for (const field of schema.required ?? []) {
        expect(Object.hasOwn(frontmatter, field)).toBe(true);
      }
      for (const [field, property] of Object.entries(schema.properties ?? {})) {
        const value = frontmatter[field];
        if (value === undefined) {
          continue;
        }
        if (property.type === "array") {
          expect(Array.isArray(value)).toBe(true);
        } else if (property.type === "object") {
          expect(
            typeof value === "object" && value !== null && !Array.isArray(value)
          ).toBe(true);
        } else if (property.type !== undefined) {
          expect(typeof value).toBe(property.type);
        }
      }
    })
  );
});

test("the shipped SDD example tree passes the packaged validator", async () => {
  const validator = path.join(SKILL_ROOT, "scripts/sdd.ts");
  const fixture = path.join(
    SKILL_ROOT,
    "references/examples/valid-spec-tree/spec"
  );
  const child = Bun.spawn([process.execPath, validator, "validate", fixture], {
    stderr: "pipe",
    stdout: "pipe"
  });
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text()
  ]);
  if (exitCode !== 0) {
    throw new Error(`SDD fixture validation failed:\n${stdout}${stderr}`);
  }
  expect(stdout).toContain("Failed: 0");
});
