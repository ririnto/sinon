#!/usr/bin/env bun
// -*- coding: utf-8 -*-
/* eslint-disable func-style, promise/avoid-new */

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import path from "node:path";

type CheckResult = Readonly<{
  errors: number;
  name: string;
  warnings: number;
}>;

type CommandResult = Readonly<{
  code: number;
  stderr: string;
  stdout: string;
}>;

const root =
  process.env["CLAUDE_PLUGIN_ROOT"] ??
  path.resolve(import.meta.dirname, "..", "..", "..");

/**
 * Run a command and capture output.
 *
 * @param command Command name.
 * @param args Command arguments.
 * @param options Command options.
 * @returns Captured command result.
 */
async function runCommand(
  command: string,
  args: readonly string[],
  options: Readonly<{ cwd?: string; shell?: boolean }> = {}
): Promise<CommandResult> {
  return await new Promise((resolve) => {
    const child = spawn(command, [...args], {
      cwd: options.cwd,
      shell: options.shell ?? false,
      stdio: ["ignore", "pipe", "pipe"]
    });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf-8");
    child.stderr.setEncoding("utf-8");
    child.stdout.on("data", (chunk: string) => {
      stdout += chunk;
    });
    child.stderr.on("data", (chunk: string) => {
      stderr += chunk;
    });
    child.on("error", (error) => {
      resolve({ code: 127, stderr: `${error.message}\n`, stdout: "" });
    });
    child.on("close", (code) => {
      resolve({ code: code ?? 1, stderr, stdout });
    });
  });
}

/**
 * Return true when a command is available on PATH.
 *
 * @param command Command name.
 * @returns Whether the command can be executed.
 */
async function hasCommand(command: string): Promise<boolean> {
  const result = await runCommand("command", ["-v", command], { shell: true });
  return result.code === 0;
}

/**
 * List tracked repository files matching one git pathspec.
 *
 * @param pathspec Git pathspec.
 * @returns Matching repository-relative paths.
 */
async function trackedFiles(pathspec: string): Promise<readonly string[]> {
  const result = await runCommand("git", [
    "-C",
    root,
    "ls-files",
    "--full-name",
    "--",
    pathspec
  ]);
  if (result.code !== 0) {
    process.stderr.write(result.stderr || result.stdout);
    return [];
  }
  return result.stdout
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line !== "")
    .filter((line) => existsSync(path.join(root, line)));
}

/**
 * Print command output when present.
 *
 * @param result Command result.
 */
function printOutput(result: CommandResult): void {
  if (result.stdout !== "") {
    process.stderr.write(result.stdout);
  }
  if (result.stderr !== "") {
    process.stderr.write(result.stderr);
  }
}

/**
 * Run shellcheck over tracked shell scripts.
 *
 * @returns Check result.
 */
async function checkShellcheckFiles(): Promise<CheckResult> {
  const files = await trackedFiles("*.sh");
  if (files.length === 0) {
    return { errors: 0, name: "shellcheck", warnings: 0 };
  }
  if (!(await hasCommand("shellcheck"))) {
    process.stderr.write(
      "warning: shellcheck not in PATH; skipping shellcheck\n"
    );
    return { errors: 0, name: "shellcheck", warnings: 1 };
  }
  const result = await runCommand("shellcheck", [
    "-x",
    ...files.map((file) => path.join(root, file))
  ]);
  if (result.code !== 0) {
    printOutput(result);
    return { errors: 1, name: "shellcheck", warnings: 0 };
  }
  return { errors: 0, name: "shellcheck", warnings: 0 };
}

/**
 * Run shfmt diff checks over tracked shell scripts.
 *
 * @returns Check result.
 */
async function checkShfmtFiles(): Promise<CheckResult> {
  const files = await trackedFiles("*.sh");
  if (files.length === 0) {
    return { errors: 0, name: "shfmt", warnings: 0 };
  }
  if (!(await hasCommand("shfmt"))) {
    process.stderr.write("warning: shfmt not in PATH; skipping shfmt\n");
    return { errors: 0, name: "shfmt", warnings: 1 };
  }
  const result = await runCommand("shfmt", [
    "-d",
    ...files.map((file) => path.join(root, file))
  ]);
  if (result.code !== 0 || result.stdout !== "") {
    printOutput(result);
    return { errors: 1, name: "shfmt", warnings: 0 };
  }
  return { errors: 0, name: "shfmt", warnings: 0 };
}

/**
 * Run markdownlint-cli2 over tracked Markdown files.
 *
 * @returns Check result.
 */
async function checkMarkdownFiles(): Promise<CheckResult> {
  const files = await trackedFiles("*.md");
  if (files.length === 0) {
    return { errors: 0, name: "markdownlint", warnings: 0 };
  }
  if (!(await hasCommand("markdownlint-cli2"))) {
    process.stderr.write(
      "warning: markdownlint-cli2 not in PATH; skipping markdown linting\n"
    );
    return { errors: 0, name: "markdownlint", warnings: 1 };
  }
  const result = await runCommand("markdownlint-cli2", files, { cwd: root });
  if (result.code !== 0) {
    printOutput(result);
    return { errors: 1, name: "markdownlint", warnings: 0 };
  }
  return { errors: 0, name: "markdownlint", warnings: 0 };
}

/**
 * Run ruff lint and format checks through uv.
 *
 * @returns Check result.
 */
async function checkPythonFiles(): Promise<CheckResult> {
  if (!(await hasCommand("uv"))) {
    process.stderr.write("warning: uv not in PATH; skipping ruff checks\n");
    return { errors: 0, name: "ruff", warnings: 1 };
  }
  const lint = await runCommand(
    "uv",
    ["run", "--with", "ruff>=0.15.18,<0.16.0", "ruff", "check", "."],
    {
      cwd: root
    }
  );
  const format = await runCommand(
    "uv",
    [
      "run",
      "--with",
      "ruff>=0.15.18,<0.16.0",
      "ruff",
      "format",
      "--check",
      "."
    ],
    { cwd: root }
  );
  if (lint.code !== 0 || format.code !== 0) {
    printOutput(lint);
    printOutput(format);
    return { errors: 1, name: "ruff", warnings: 0 };
  }
  return { errors: 0, name: "ruff", warnings: 0 };
}

/**
 * Run plugin package validation.
 *
 * @returns Check result.
 */
async function checkPluginPackages(): Promise<CheckResult> {
  if (!(await hasCommand("bun"))) {
    process.stderr.write(
      "warning: bun not in PATH; skipping plugin package validation\n"
    );
    return { errors: 0, name: "plugin-packages", warnings: 1 };
  }
  const validator = path.join(
    root,
    "plugins",
    "harness",
    "scripts",
    "plugin-self-check.ts"
  );
  if (!existsSync(validator)) {
    process.stderr.write(
      `error: missing plugin package validator: ${validator}\n`
    );
    return { errors: 1, name: "plugin-packages", warnings: 0 };
  }
  const result = await runCommand("bun", [
    validator,
    path.join(root, "plugins", "harness")
  ]);
  printOutput(result);
  return {
    errors: result.code === 0 ? 0 : 1,
    name: "plugin-packages",
    warnings: 0
  };
}

/**
 * Run repository validation.
 *
 * @returns Process exit code.
 */
async function main(): Promise<number> {
  const results = await Promise.all([
    checkShellcheckFiles(),
    checkShfmtFiles(),
    checkMarkdownFiles(),
    checkPythonFiles(),
    checkPluginPackages()
  ]);
  const errors = results.reduce((total, result) => total + result.errors, 0);
  const warnings = results.reduce(
    (total, result) => total + result.warnings,
    0
  );
  if (errors === 0 && warnings === 0) {
    console.log("Repository validation passed.");
  } else {
    console.log("Repository validation reported diagnostics.");
  }
  return errors > 0 ? 1 : 0;
}

process.exit(await main());
