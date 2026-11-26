import { spawnSync } from "node:child_process";
import {
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  realpath,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import { configureBaseline } from "../scripts/configure.js";
import { waitForFixtureTasks } from "./fixture-command.ts";

test.skipIf(
  process.env.HARNESS_GO_WORKSPACE_TEST === undefined ||
    process.env.HARNESS_GO_WORKSPACE_TEST === ""
)(
  "runs root-owned tools across a workspace without changing its member modules",
  async () => {
    const root = await realpath(
      await mkdtemp(path.join(tmpdir(), "go workspace "))
    );
    onTestFinished(async () => {
      await rm(root, { force: true, recursive: true });
    });
    const first = path.join(root, "first module");
    const second = path.join(root, "second module");
    const workFile = path.join(root, "go.work");
    const workspace =
      'go 1.27.0\n\nuse (\n  "./first module"\n  "./second module"\n)\n';
    const firstModule = "module example.com/first\n\ngo 1.27.0\n";
    const secondModule =
      "module example.com/second\n\ngo 1.27.0\n\nrequire example.com/first v0.0.0\n";
    await waitForFixtureTasks([mkdir(first), mkdir(second)]);
    await waitForFixtureTasks([
      writeFile(workFile, workspace),
      writeFile(path.join(first, "go.mod"), firstModule),
      writeFile(path.join(second, "go.mod"), secondModule),
      writeFile(path.join(first, "first.go"), "package first\n"),
      writeFile(
        path.join(first, "main_test.go"),
        'package first\nimport "testing"\nfunc TestFirst(t *testing.T) {}\n'
      ),
      writeFile(
        path.join(second, "main_test.go"),
        'package second\nimport ("testing"; _ "example.com/first")\nfunc TestSecond(t *testing.T) {}\n'
      )
    ]);
    await configureBaseline(root, "go", "eligible");
    const { created } = await configureBaseline(root, "go", "eligible");
    expect(created).toEqual([]);
    const run = (script: string) =>
      spawnSync("pnpm", ["run", script], {
        cwd: root,
        encoding: "utf-8",
        env: { ...process.env, GOWORK: workFile },
        timeout: 120_000
      });
    const missingOwner = run("go:tools");
    expect(missingOwner.status).not.toBe(0);
    expect(missingOwner.stderr).toContain("cannot find main module");
    const go = (args: string[], cwd = root) =>
      spawnSync("go", args, {
        cwd,
        encoding: "utf-8",
        env: { ...process.env, GOWORK: "off" },
        timeout: 120_000
      });
    expect(go(["mod", "init", "example.com/workspace"]).status).toBe(0);
    const setup = run("go:tools");
    expect(setup.status, setup.stderr).toBe(0);
    const moduleFile = path.join(root, "go.mod");
    const sumFile = path.join(root, "go.sum");
    const manifest = await readFile(moduleFile, "utf-8");
    const checksums = await readFile(sumFile, "utf-8");
    expect(manifest).toContain("module example.com/workspace");
    expect(run("go:tools").status).toBe(0);
    expect(go(["mod", "verify"]).stdout).toContain("all modules verified");
    expect(await readdir(root)).not.toContain("tools");
    const passing = run("check:test:go");
    expect(passing.status, passing.stderr).toBe(0);
    expect(passing.stdout).toContain("example.com/first");
    expect(passing.stdout).toContain("example.com/second");
    await writeFile(
      path.join(second, "main_test.go"),
      'package second\nimport ("testing"; _ "example.com/first")\nfunc TestSecond(t *testing.T) { t.Fatal("broken second module") }\n'
    );
    const failing = run("check:test:go");
    expect(failing.status).not.toBe(0);
    expect(failing.stdout).toContain("broken second module");
    const standalone = go(["test", "-race", "./..."], first);
    expect(standalone.status, standalone.stderr).toBe(0);
    expect(standalone.stdout).toContain("example.com/first");
    expect(standalone.stdout).not.toContain("example.com/second");
    expect(await readFile(moduleFile, "utf-8")).toBe(manifest);
    expect(await readFile(sumFile, "utf-8")).toBe(checksums);
    expect(await readFile(workFile, "utf-8")).toBe(workspace);
    expect(await readFile(path.join(first, "go.mod"), "utf-8")).toBe(
      firstModule
    );
    expect(await readFile(path.join(second, "go.mod"), "utf-8")).toBe(
      secondModule
    );
  },
  360_000
);
