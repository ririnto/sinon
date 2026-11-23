import { spawnSync } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";
import { parse } from "yaml";

import { arrayValue, stringValue, valueAt } from "./structured-value.ts";

const harness = path.resolve(import.meta.dirname, "..");
const valid = '#!/usr/bin/env sh\nif true; then\n  printf "%s\\n" "$1"\nfi\n';

test.skipIf(process.env.HARNESS_SHELL_PROFILE_TEST !== "1")(
  "checks tracked scripts with the installed shell profile and the GitLab job",
  async () => {
    const cwd = await mkdtemp(path.join(tmpdir(), "sinon-shell-profile-"));
    onTestFinished(async () => {
      await rm(cwd, { force: true, recursive: true });
    });
    const run = (command: string, args: readonly string[]) => {
      const result = spawnSync(command, args, {
        cwd,
        encoding: "utf-8",
        timeout: 90_000
      });
      expect(result.error).toBeUndefined();
      return { output: result.stdout + result.stderr, status: result.status };
    };
    const configure = () =>
      run(process.execPath, [
        path.join(harness, "scripts/configure.ts"),
        "--target",
        cwd,
        "--profile",
        "shell",
        "--eligibility",
        "eligible"
      ]);
    expect(run("git", ["init"]).status).toBe(0);
    const script = path.join(cwd, "tracked script.sh");
    await writeFile(script, valid);
    await writeFile(
      path.join(cwd, "untracked.sh"),
      "not valid shell syntax (\n"
    );
    expect(run("git", ["add", "--", "tracked script.sh"]).status).toBe(0);
    expect(configure().status).toBe(0);
    const install = run("pnpm", ["install", "--no-frozen-lockfile"]);
    expect(install.status, install.output).toBe(0);
    const module = run("go", ["mod", "init", "example.invalid/shell-profile"]);
    expect(module.status, module.output).toBe(0);
    const tools = run("pnpm", ["run", "shell:tools"]);
    expect(tools.status, tools.output).toBe(0);
    const job: unknown = parse(
      await readFile(
        path.join(harness, "ci/gitlab/shell.gitlab-ci.yaml"),
        "utf-8"
      )
    );
    const commands = arrayValue(valueAt(job, "shell-format", "script")).map(
      stringValue
    );
    const gates = [
      () => run("pnpm", ["run", "check:shell:format"]),
      () => run("git", ["hook", "run", "pre-commit"]),
      () => run("git", ["hook", "run", "pre-push"]),
      () => run("sh", ["-e", "-c", commands.join("\n")])
    ];
    for (const gate of gates) {
      const result = gate();
      expect(result.status, result.output).toBe(0);
    }
    await writeFile(script, valid.replace("\nfi\n", "\n"));
    for (const gate of gates) {
      const result = gate();
      expect(result.status).toBeGreaterThan(0);
      expect(result.output).toContain("must end with `fi`");
    }
    await writeFile(script, valid.replace("  printf", "\tprintf"));
    for (const gate of gates) {
      const result = gate();
      expect(result.status).toBeGreaterThan(0);
      expect(result.output).toContain("+  printf");
    }
    expect(
      run("env", [
        "GOWORK=off",
        "go",
        "tool",
        "-modfile=go.mod",
        "mvdan.cc/sh/v3/cmd/shfmt",
        "-w",
        "--",
        "tracked script.sh"
      ]).status
    ).toBe(0);
    expect(await readFile(script, "utf-8")).toBe(valid);
    for (const gate of gates) {
      const result = gate();
      expect(result.status, result.output).toBe(0);
    }
    const editorConfig = path.join(cwd, ".editorconfig");
    const targetSettings = "root = true\n\n[*.sh]\nindent_style = tab\n";
    await writeFile(editorConfig, targetSettings);
    expect(configure().status).toBe(0);
    expect(await readFile(editorConfig, "utf-8")).toBe(targetSettings);
    expect(await readFile(script, "utf-8")).toBe(valid);
  },
  120_000
);
