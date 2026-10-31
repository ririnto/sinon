import { spawnSync } from "node:child_process";
import {
  chmod,
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  stat,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";
import { parse } from "yaml";

import { materializeSample } from "../scripts/materialize-sample.js";

const samples = [
  ["cargo-workspace", "Cargo.toml"],
  ["go-workspace", "go.work"],
  ["gradle-multi-module", "settings.gradle.kts"],
  ["maven-reactor", "pom.xml"],
  ["pnpm-workspace", "pnpm-workspace.yaml"],
  ["uv-workspace", "pyproject.toml"]
] as const;

const runMaterializer = (script: string, target: string) =>
  spawnSync(process.execPath, [
    script,
    "--sample",
    "go-workspace",
    "--target",
    target
  ]);

test.each(samples)(
  "materializes %s into an independent empty directory",
  async (name, manifest) => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-"));
    onTestFinished(() => rm(directory, { force: true, recursive: true }));
    await materializeSample(name, directory);
    const copied = await stat(path.join(directory, manifest));
    expect(copied.isFile()).toBe(true);
    if (name === "gradle-multi-module") {
      const ruleset = await stat(
        path.join(directory, "tooling/kotlin/build.gradle.kts")
      );
      expect(ruleset.isFile()).toBe(true);
    }
    await expect(materializeSample(name, directory)).rejects.toThrow("empty");
  }
);

test("rejects a nonempty destination before writing", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-"));
  onTestFinished(() => rm(directory, { force: true, recursive: true }));
  await writeFile(path.join(directory, "owned.txt"), "keep");
  await expect(materializeSample("go-workspace", directory)).rejects.toThrow(
    "empty"
  );
  expect(await readFile(path.join(directory, "owned.txt"), "utf-8")).toBe(
    "keep"
  );
});

test.each(samples)(
  "%s GitLab pipeline declares every job stage",
  async (name) => {
    const pipeline = parse(
      await readFile(
        path.resolve(import.meta.dirname, "../samples", name, ".gitlab-ci.yml"),
        "utf-8"
      )
    ) as Record<string, unknown>;
    expect(pipeline.stages).toEqual(expect.arrayContaining(["validate"]));
    for (const [key, job] of Object.entries(pipeline)) {
      if (key !== "stages") {
        expect(pipeline.stages).toContain((job as { stage: string }).stage);
      }
    }
  }
);

test.each([
  ["pnpm@12.16.1", 0, "install --global pnpm@12.16.1"],
  ["npm@1.0.0", 1, ""],
  ["pnpm@latest", 1, ""]
] as const)(
  "pnpm sample CI validates package manager %s",
  async (manager, status, output) => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-ci-fixture-"));
    onTestFinished(() => rm(directory, { force: true, recursive: true }));
    await mkdir(path.join(directory, "bin"));
    await writeFile(
      path.join(directory, "bin/npm"),
      '#!/usr/bin/env sh\nprintf "%s\\n" "$*"\n',
      { mode: 0o755 }
    );
    await writeFile(
      path.join(directory, "package.json"),
      JSON.stringify({ packageManager: manager })
    );
    const result = spawnSync(
      "sh",
      [
        "-c",
        String(
          parse(
            await readFile(
              path.resolve(
                import.meta.dirname,
                "../samples/pnpm-workspace/.gitlab-ci.yml"
              ),
              "utf-8"
            )
          ).pnpm.before_script[0]
        )
      ],
      {
        cwd: directory,
        encoding: "utf-8",
        env: {
          ...process.env,
          PATH: `${path.join(directory, "bin")}${path.delimiter}${process.env.PATH}`
        }
      }
    );
    expect(result.status).toBe(status);
    expect(result.stdout.trim()).toBe(output);
  }
);

test("rejects unsafe inputs before writing and excludes generated files", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-fixture-"));
  onTestFinished(() => rm(directory, { force: true, recursive: true }));
  const script = path.join(directory, "scripts/materialize-sample.ts");
  const source = path.join(directory, "samples/go-workspace");
  const target = path.join(directory, "target");
  await Promise.all([
    mkdir(path.dirname(script), { recursive: true }),
    mkdir(source, { recursive: true }),
    mkdir(target)
  ]);
  await copyFile(
    path.resolve(import.meta.dirname, "../scripts/materialize-sample.ts"),
    script
  );
  await writeFile(path.join(source, "go.work"), "go 1.27\n");
  await symlink("go.work", path.join(source, "unsafe"));
  expect(runMaterializer(script, target).status).not.toBe(0);
  expect(await readdir(target)).toEqual([]);
  await rm(path.join(source, "unsafe"));
  await mkdir(path.join(source, "node_modules"));
  await symlink("../go.work", path.join(source, "node_modules/link"));
  const inaccessible = path.join(source, "node_modules/blocked");
  await mkdir(inaccessible);
  await chmod(inaccessible, 0);
  await mkdir(path.join(source, ".husky/_"), { recursive: true });
  await writeFile(path.join(source, ".husky/_/generated"), "generated");
  try {
    expect(runMaterializer(script, target).status).toBe(0);
  } finally {
    await chmod(inaccessible, 0o700);
  }
  expect(await readdir(target)).toEqual([".husky", "go.work"]);
  expect(await readdir(path.join(target, ".husky"))).toEqual([]);
});
