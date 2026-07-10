#!/usr/bin/env bun
// -*- coding: utf-8 -*-
/* eslint-disable func-style, promise/avoid-new */

import { spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

type FixResult = Readonly<{
  changed: readonly string[];
  errors: number;
  name: string;
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
 * List tracked repository files matching one git pathspec.
 *
 * @param pathspec Git pathspec.
 * @returns Absolute file paths that still exist.
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
    printOutput(result);
    return [];
  }
  return result.stdout
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line !== "")
    .map((line) => path.join(root, line))
    .filter((filePath) => existsSync(filePath));
}

/**
 * Capture file contents for change detection.
 *
 * @param files Files to snapshot.
 * @returns File content snapshot.
 */
function snapshotFiles(files: readonly string[]): ReadonlyMap<string, string> {
  return new Map(
    files.map((filePath) => [filePath, readFileSync(filePath, "utf-8")])
  );
}

/**
 * Return files whose content changed since a snapshot.
 *
 * @param before File content snapshot.
 * @returns Changed file paths.
 */
function changedFiles(before: ReadonlyMap<string, string>): readonly string[] {
  return [...before.entries()]
    .filter(
      ([filePath, content]) =>
        existsSync(filePath) && readFileSync(filePath, "utf-8") !== content
    )
    .map(([filePath]) => filePath);
}

/**
 * Run shfmt over tracked shell scripts.
 *
 * @returns Fix result.
 */
async function fixShellFiles(): Promise<FixResult> {
  const files = await trackedFiles("*.sh");
  if (files.length === 0) {
    return { changed: [], errors: 0, name: "shfmt" };
  }
  if (!(await hasCommand("shfmt"))) {
    process.stderr.write("warning: shfmt not in PATH; skipping shfmt fixes\n");
    return { changed: [], errors: 0, name: "shfmt" };
  }
  const before = snapshotFiles(files);
  const result = await runCommand("shfmt", ["-w", ...files]);
  if (result.code !== 0) {
    printOutput(result);
    process.stderr.write("error: shfmt failed\n");
    return { changed: [], errors: 1, name: "shfmt" };
  }
  return { changed: changedFiles(before), errors: 0, name: "shfmt" };
}

/**
 * Run markdownlint-cli2 fixes over tracked Markdown files.
 *
 * @returns Fix result.
 */
async function fixMarkdownFiles(): Promise<FixResult> {
  const files = await trackedFiles("*.md");
  if (files.length === 0) {
    return { changed: [], errors: 0, name: "markdownlint" };
  }
  if (!(await hasCommand("markdownlint-cli2"))) {
    process.stderr.write(
      "warning: markdownlint-cli2 not in PATH; skipping markdown fixes\n"
    );
    return { changed: [], errors: 0, name: "markdownlint" };
  }
  const before = snapshotFiles(files);
  const result = await runCommand("markdownlint-cli2", ["--fix", ...files], {
    cwd: root
  });
  if (result.code !== 0) {
    printOutput(result);
    process.stderr.write("error: markdownlint-cli2 --fix failed\n");
    return { changed: [], errors: 1, name: "markdownlint" };
  }
  return { changed: changedFiles(before), errors: 0, name: "markdownlint" };
}

/**
 * Run ruff lint and format fixes through uv.
 *
 * @returns Fix result.
 */
async function fixPythonFiles(): Promise<FixResult> {
  if (!(await hasCommand("uv"))) {
    process.stderr.write("warning: uv not in PATH; skipping ruff fixes\n");
    return { changed: [], errors: 0, name: "ruff" };
  }
  const files = await trackedFiles("*.py");
  const before = snapshotFiles(files);
  const lint = await runCommand(
    "uv",
    ["run", "--with", "ruff>=0.15.21,<0.16.0", "ruff", "check", "--fix", "."],
    { cwd: root }
  );
  if (lint.code !== 0) {
    printOutput(lint);
    process.stderr.write("error: ruff check --fix failed\n");
    return { changed: [], errors: 1, name: "ruff" };
  }
  const format = await runCommand(
    "uv",
    ["run", "--with", "ruff>=0.15.21,<0.16.0", "ruff", "format", "."],
    { cwd: root }
  );
  if (format.code !== 0) {
    printOutput(format);
    process.stderr.write("error: ruff format failed\n");
    return { changed: [], errors: 1, name: "ruff" };
  }
  return { changed: changedFiles(before), errors: 0, name: "ruff" };
}

/**
 * Run repository fixes.
 *
 * @returns Process exit code.
 */
async function main(): Promise<number> {
  const results = await Promise.all([
    fixShellFiles(),
    fixPythonFiles(),
    fixMarkdownFiles()
  ]);
  const errors = results.reduce((total, result) => total + result.errors, 0);
  if (errors > 0) {
    return 1;
  }
  const changed = [...new Set(results.flatMap((result) => result.changed))]
    .toSorted()
    .map((filePath) => path.relative(root, filePath));
  if (changed.length > 0) {
    console.log("fixed files:");
    for (const filePath of changed) {
      console.log(`  ${filePath}`);
    }
  } else {
    console.log("no files fixed");
  }
  console.log("remaining findings after fixes:");
  const check = await runCommand("bun", [
    path.join(root, "plugins", "harness", "scripts", "check.ts")
  ]);
  printOutput(check);
  return check.code;
}

process.exit(await main());
