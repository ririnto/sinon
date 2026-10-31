import { spawnSync } from "node:child_process";
import {
  copyFile,
  mkdir,
  mkdtemp,
  realpath,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

const tooling = path.resolve(import.meta.dirname, "../tooling/go");

test.skipIf(!process.env.HARNESS_GO_WORKSPACE_TEST)(
  "tests each active workspace module and propagates failures",
  async () => {
    const root = await realpath(
      await mkdtemp(path.join(tmpdir(), "go workspace "))
    );
    onTestFinished(() => rm(root, { force: true, recursive: true }));
    const first = path.join(root, "first module");
    const second = path.join(root, "second module");
    await Promise.all([mkdir(first), mkdir(second)]);
    await Promise.all([
      copyFile(
        path.join(tooling, "Taskfile.yaml"),
        path.join(root, "Taskfile.yaml")
      ),
      copyFile(
        path.join(tooling, "Taskfile.yaml"),
        path.join(first, "Taskfile.yaml")
      ),
      writeFile(
        path.join(root, "go.work"),
        'go 1.27.0\n\nuse (\n  "./first module"\n  "./second module"\n)\n'
      ),
      writeFile(
        path.join(first, "go.mod"),
        "module example.com/first\n\ngo 1.27.0\n"
      ),
      writeFile(
        path.join(second, "go.mod"),
        "module example.com/second\n\ngo 1.27.0\n\nrequire example.com/first v0.0.0\n"
      ),
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
    const run = (dir: string, workspace: string) =>
      spawnSync(
        "go",
        [
          "-C",
          path.join(tooling, "tools"),
          "tool",
          "task",
          "--dir",
          dir,
          "test"
        ],
        {
          encoding: "utf-8",
          env: { ...process.env, GOWORK: "off", HARNESS_GOWORK: workspace },
          timeout: 120_000
        }
      );
    const passing = run(root, path.join(root, "go.work"));
    expect(passing.status, passing.stderr).toBe(0);
    expect(passing.stdout).toContain("example.com/first");
    expect(passing.stdout).toContain("example.com/second");
    await writeFile(
      path.join(second, "main_test.go"),
      'package second\nimport ("testing"; _ "example.com/first")\nfunc TestSecond(t *testing.T) { t.Fatal("broken second module") }\n'
    );
    const failing = run(root, path.join(root, "go.work"));
    expect(failing.status).not.toBe(0);
    expect(failing.stdout).toContain("broken second module");
    const standalone = run(first, "off");
    expect(standalone.status, standalone.stderr).toBe(0);
    expect(standalone.stdout).toContain("example.com/first");
    expect(standalone.stdout).not.toContain("example.com/second");
  },
  360_000
);
