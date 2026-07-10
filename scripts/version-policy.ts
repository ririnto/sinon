// -*- coding: utf-8 -*-

import { readFileSync } from "node:fs";
import path from "node:path";

type PackageJson = Readonly<{
  dependencies?: Readonly<Record<string, string>>;
  devDependencies?: Readonly<Record<string, string>>;
  engines?: Readonly<Record<string, string>>;
  optionalDependencies?: Readonly<Record<string, string>>;
  peerDependencies?: Readonly<Record<string, string>>;
  workspaces?: Readonly<{
    catalog?: Readonly<Record<string, string>>;
  }>;
}>;

export type VersionPolicyResult = Readonly<{
  errors: readonly string[];
}>;

const CARET_SEMVER = /^\^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/u;
const MARKDOWNLINT_SCHEMA =
  "https://raw.githubusercontent.com/DavidAnson/markdownlint-cli2/refs/tags/v";
const MARKDOWNLINT_SCHEMA_SUFFIX =
  "/schema/markdownlint-cli2-config-schema.json";
const EXPLICIT_SECTIONS = [
  "dependencies",
  "optionalDependencies",
  "peerDependencies"
] as const;
const SCHEMA_PATHS = [
  ".markdownlint-cli2.jsonc",
  "plugins/harness/skills/harness-install/assets/common/.markdownlint-cli2.jsonc"
] as const;

const readJson = <T>(filePath: string): T =>
  JSON.parse(readFileSync(filePath, "utf-8")) as T;

const expectedSchema = (version: string): string =>
  `${MARKDOWNLINT_SCHEMA}${version.slice(1)}${MARKDOWNLINT_SCHEMA_SUFFIX}`;

const collectInlineImports = (
  root: string,
  files: readonly string[]
): readonly Readonly<{ name: string; path: string; version: string }>[] => {
  const imports: { name: string; path: string; version: string }[] = [];
  const pattern =
    /npm:(?<name>@[^/\s"'`]+\/[^@\s"'`]+|[^@\s"'`/]+)@(?<version>\^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)/gu;
  for (const relativePath of files) {
    const text = readFileSync(path.join(root, relativePath), "utf-8");
    for (const match of text.matchAll(pattern)) {
      imports.push({
        name: match.groups?.name ?? "",
        path: relativePath,
        version: match.groups?.version ?? ""
      });
    }
  }
  return imports;
};

const validatePackages = (
  rootPackage: PackageJson,
  assetPackage: PackageJson
): readonly string[] => {
  const errors: string[] = [];
  const catalog = rootPackage.workspaces?.catalog ?? {};
  for (const [name, version] of Object.entries(catalog)) {
    if (!CARET_SEMVER.test(version)) {
      errors.push(`root catalog ${name} must use ^x.y.z, found ${version}`);
    }
  }
  for (const [name, version] of Object.entries(
    rootPackage.devDependencies ?? {}
  )) {
    if (version !== "catalog:") {
      errors.push(
        `root devDependencies ${name} must use catalog:, found ${version}`
      );
    }
  }
  for (const section of EXPLICIT_SECTIONS) {
    for (const [name, version] of Object.entries(rootPackage[section] ?? {})) {
      if (!CARET_SEMVER.test(version)) {
        errors.push(
          `root ${section} ${name} must use ^x.y.z, found ${version}`
        );
      }
    }
  }
  for (const section of ["dependencies", "devDependencies"] as const) {
    for (const [name, version] of Object.entries(assetPackage[section] ?? {})) {
      if (!CARET_SEMVER.test(version)) {
        errors.push(
          `Harness Bun ${section} ${name} must use ^x.y.z, found ${version}`
        );
      }
      if (catalog[name] !== undefined && catalog[name] !== version) {
        errors.push(
          `Harness Bun ${section} ${name} must match root catalog ${catalog[name]}, found ${version}`
        );
      }
    }
  }
  return errors;
};

const validateEngines = (
  rootPackage: PackageJson,
  assetPackage: PackageJson
): readonly string[] => {
  const errors: string[] = [];
  for (const [label, packageJson] of [
    ["root", rootPackage],
    ["Harness Bun", assetPackage]
  ] as const) {
    if (packageJson.engines?.node !== ">=22") {
      errors.push(`${label} package must declare engines.node >=22`);
    }
  }
  return errors;
};

const validateLockPolicy = (root: string): readonly string[] => {
  const errors: string[] = [];
  const ignoredLocks = new Set(
    readFileSync(path.join(root, ".gitignore"), "utf-8")
      .split(/\r?\n/u)
      .map((line) => line.trim())
  );
  if (ignoredLocks.has("bun.lock")) {
    errors.push("root .gitignore must not ignore bun.lock");
  }
  if (!ignoredLocks.has("bun.lockb")) {
    errors.push("root .gitignore must retain bun.lockb");
  }
  return errors;
};

const validateSchemas = (
  root: string,
  markdownlintVersion: string | undefined
): readonly string[] => {
  const errors: string[] = [];
  for (const relativePath of SCHEMA_PATHS) {
    const schema = readJson<Readonly<{ $schema?: string }>>(
      path.join(root, relativePath)
    ).$schema;
    if (schema !== expectedSchema(markdownlintVersion ?? "")) {
      errors.push(
        `${relativePath} must use the markdownlint-cli2 schema for ${markdownlintVersion ?? "the root catalog version"}`
      );
    }
  }
  return errors;
};

const validateInlineImports = (
  root: string,
  catalog: Readonly<Record<string, string>>,
  inlineImportPaths: readonly string[]
): readonly string[] => {
  const errors: string[] = [];
  for (const entry of collectInlineImports(root, inlineImportPaths)) {
    if (catalog[entry.name] !== entry.version) {
      errors.push(
        `${entry.path}: npm:${entry.name} must match root catalog ${catalog[entry.name] ?? "missing"}, found ${entry.version}`
      );
    }
  }
  return errors;
};

/** Validate the repository's explicit npm and Bun version policy. */
export const validateVersionPolicy = (
  root: string,
  inlineImportPaths: readonly string[] = []
): VersionPolicyResult => {
  const rootPackage = readJson<PackageJson>(path.join(root, "package.json"));
  const assetPackage = readJson<PackageJson>(
    path.join(
      root,
      "plugins/harness/skills/harness-install/assets/bun/package.json"
    )
  );
  const catalog = rootPackage.workspaces?.catalog ?? {};
  return {
    errors: [
      ...validatePackages(rootPackage, assetPackage),
      ...validateEngines(rootPackage, assetPackage),
      ...validateLockPolicy(root),
      ...validateSchemas(root, catalog["markdownlint-cli2"]),
      ...validateInlineImports(root, catalog, inlineImportPaths)
    ]
  };
};

const main = (): number => {
  const root = path.resolve(import.meta.dirname, "..");
  const files = Bun.spawnSync(["git", "ls-files"], { cwd: root })
    .stdout.toString()
    .split(/\r?\n/u)
    .filter((filePath) => /\.(?:[cm]?js|[jt]sx?|md)$/u.test(filePath));
  const result = validateVersionPolicy(root, files);
  for (const error of result.errors) {
    console.error(`error: ${error}`);
  }
  return result.errors.length === 0 ? 0 : 1;
};

if (import.meta.main) {
  process.exitCode = main();
}
