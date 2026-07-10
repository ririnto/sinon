// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import { cp, mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { validateVersionPolicy } from "./version-policy.js";

const repositoryRoot = path.resolve(import.meta.dirname, "..");
const assetPackagePath =
  "plugins/harness/skills/harness-install/assets/bun/package.json";
const schemaPaths = [
  ".markdownlint-cli2.jsonc",
  "plugins/harness/skills/harness-install/assets/common/.markdownlint-cli2.jsonc"
] as const;

const rootCatalogVersion = (name: string): string => {
  const packageJson = JSON.parse(readFileSync("package.json", "utf-8")) as {
    workspaces: { catalog: Record<string, string> };
  };
  return packageJson.workspaces.catalog[name] ?? "";
};

const differentCaretVersion = (version: string): string => {
  const match = /^\^(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)/u.exec(version);
  if (match === null) {
    throw new Error(`expected caret semver: ${version}`);
  }
  return `^${match.groups?.major ?? ""}.${match.groups?.minor ?? ""}.${Number(match.groups?.patch) + 1}`;
};

const createFixture = async (): Promise<string> => {
  const root = await mkdtemp(path.join(tmpdir(), "sinon-version-policy-"));
  await cp(
    path.join(repositoryRoot, "package.json"),
    path.join(root, "package.json")
  );
  await cp(
    path.join(repositoryRoot, assetPackagePath),
    path.join(root, assetPackagePath),
    { recursive: true }
  );
  await cp(
    path.join(repositoryRoot, ".gitignore"),
    path.join(root, ".gitignore")
  );
  await Promise.all(
    schemaPaths.map((relativePath) =>
      cp(
        path.join(repositoryRoot, relativePath),
        path.join(root, relativePath),
        {
          recursive: true
        }
      )
    )
  );
  return root;
};

const mutateJson = async (
  root: string,
  relativePath: string,
  mutate: (value: Record<string, unknown>) => void
): Promise<void> => {
  const filePath = path.join(root, relativePath);
  const value = JSON.parse(await Bun.file(filePath).text()) as Record<
    string,
    unknown
  >;
  mutate(value);
  await writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf-8");
};

test("the repository version policy is valid", () => {
  expect(validateVersionPolicy(repositoryRoot).errors).toEqual([]);
});

test("catalog entries require caret semver", async () => {
  const root = await createFixture();
  try {
    await mutateJson(root, "package.json", (value) => {
      const workspaces = value.workspaces as Record<string, unknown>;
      const catalog = workspaces.catalog as Record<string, string>;
      catalog.oxlint = "1.0.0";
    });
    expect(validateVersionPolicy(root).errors).toContain(
      "root catalog oxlint must use ^x.y.z, found 1.0.0"
    );
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});

test("Harness Bun dependencies must match the root catalog", async () => {
  const root = await createFixture();
  const rootVersion = rootCatalogVersion("oxlint");
  const mismatchedVersion = differentCaretVersion(rootVersion);
  try {
    await mutateJson(root, assetPackagePath, (value) => {
      const dependencies = value.devDependencies as Record<string, string>;
      dependencies.oxlint = mismatchedVersion;
    });
    expect(validateVersionPolicy(root).errors).toContain(
      `Harness Bun devDependencies oxlint must match root catalog ${rootVersion}, found ${mismatchedVersion}`
    );
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});

test("inline npm imports must match the root catalog", async () => {
  const root = await createFixture();
  const importPath = "example.ts";
  const rootVersion = rootCatalogVersion("oxlint");
  const mismatchedVersion = differentCaretVersion(rootVersion);
  try {
    await writeFile(
      path.join(root, importPath),
      `import "npm:oxlint@${mismatchedVersion}";\n`,
      "utf-8"
    );
    expect(validateVersionPolicy(root, [importPath]).errors).toContain(
      `example.ts: npm:oxlint must match root catalog ${rootVersion}, found ${mismatchedVersion}`
    );
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});

test("Markdownlint schemas follow the catalog baseline", async () => {
  const root = await createFixture();
  try {
    await mutateJson(root, schemaPaths[0], (value) => {
      value.$schema = "https://example.invalid/schema.json";
    });
    expect(validateVersionPolicy(root).errors).toContain(
      `.markdownlint-cli2.jsonc must use the markdownlint-cli2 schema for ${rootCatalogVersion("markdownlint-cli2")}`
    );
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});

test("locks, GitHub Actions, and Docker image tags are excluded", async () => {
  const root = await createFixture();
  const workflowPath = ".github/workflows/check.yaml";
  try {
    await mkdir(path.join(root, ".github", "workflows"), { recursive: true });
    await writeFile(
      path.join(root, workflowPath),
      "uses: actions/checkout@v7\nimage: oven/bun:1\n",
      "utf-8"
    );
    await writeFile(
      path.join(root, "bun.lock"),
      "lockfileVersion: 1\n",
      "utf-8"
    );
    expect(validateVersionPolicy(root, [workflowPath]).errors).toEqual([]);
  } finally {
    await rm(root, { force: true, recursive: true });
  }
});
