import { execFileSync, spawnSync } from "node:child_process";
import type { SpawnSyncOptionsWithStringEncoding } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

test("YAML sequence lint requires block style for nonempty sequences", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-yaml-sequences-"));
  onTestFinished(async () => {
    await rm(directory, { recursive: true, force: true });
  });
  const gitVariables = new Set(
    execFileSync("git", ["rev-parse", "--local-env-vars"], {
      encoding: "utf-8"
    })
      .trim()
      .split("\n")
  );
  const options = {
    cwd: directory,
    encoding: "utf-8",
    env: Object.fromEntries(
      Object.entries(process.env).filter(
        ([name]) => !gitVariables.has(name) && !name.startsWith("GIT_TRACE")
      )
    )
  } satisfies SpawnSyncOptionsWithStringEncoding;
  execFileSync("git", ["init", "--quiet"], options);
  const file = path.join(directory, "sequences.yml");
  const valid = 'block:\n  - one\n  - two\nempty: []\nquoted: "[one, two]"\n';
  await Promise.all([
    writeFile(file, valid),
    writeFile(path.join(directory, "pnpm-lock.yaml"), "cpu: [arm64]\n")
  ]);
  execFileSync(
    "git",
    ["add", "--", "sequences.yml", "pnpm-lock.yaml"],
    options
  );
  const check = path.join(import.meta.dirname, "yaml-sequence-style.ts");
  const accepted = spawnSync(process.execPath, [check], options);
  expect(accepted.status, accepted.stderr).toBe(0);
  expect(accepted.stderr).toBe("");
  await writeFile(
    file,
    `${valid}---\ninline: [one, two]\nmultiline: [\n  one,\n  two,\n]\n`
  );
  const rejected = spawnSync(process.execPath, [check], options);
  expect(rejected.status, rejected.stderr).toBe(1);
  expect(
    rejected.stderr
      .trim()
      .split("\n")
      .map((diagnostic) => diagnostic.split(" ", 1)[0])
  ).toEqual(["sequences.yml:7:9", "sequences.yml:8:12"]);
});
