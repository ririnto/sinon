import { test } from "bun:test";
import { cp, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { computeHarnessAssetManifest } from "./generate-harness-asset-manifest.js";

const rootDir = path.join(import.meta.dirname, "..");
const pluginDir = path.join(rootDir, "plugins/harness");
const manifestPath = path.join(
  pluginDir,
  "skills/harness-install/asset-manifest.json"
);

const run = async (command: string[], cwd: string): Promise<void> => {
  const process = Bun.spawn(command, {
    cwd,
    stderr: "pipe",
    stdout: "pipe"
  });
  if ((await process.exited) !== 0) {
    throw new Error(
      `${command.join(" ")} failed:\n${await new Response(process.stderr).text()}`
    );
  }
};

const exists = (target: string): Promise<boolean> => Bun.file(target).exists();

const manifestPaths = (manifest: unknown): string[] => {
  if (
    typeof manifest !== "object" ||
    manifest === null ||
    Array.isArray(manifest)
  ) {
    throw new Error("asset manifest must be an object");
  }
  return Object.entries(manifest)
    .flatMap(([subdir, entries]) => {
      if (
        !Array.isArray(entries) ||
        !entries.every((entry) => typeof entry === "string")
      ) {
        throw new Error(
          `asset manifest entry must be a string array: ${subdir}`
        );
      }
      return entries.map((entry) => `${subdir}/${entry}`);
    })
    .toSorted();
};

test("installs from a non-Git plugin cache with only target runtime assets", async () => {
  const fixture = await mkdtemp(path.join(tmpdir(), "harness-install-"));
  const cachedPlugin = path.join(fixture, "cache", "harness");
  const target = path.join(fixture, "target");
  try {
    await cp(pluginDir, cachedPlugin, { recursive: true });
    await run(["git", "init", target], fixture);
    const installerCommand = [
      "bun",
      path.join(
        cachedPlugin,
        "skills/harness-install/scripts/install-harness.ts"
      ),
      "--target",
      target,
      "--mode",
      "bun",
      "--ci-host",
      "github"
    ];
    await run(installerCommand, fixture);

    if (
      (await exists(path.join(target, ".harness"))) ||
      !(await exists(path.join(target, "WORKFLOW.md"))) ||
      (await exists(path.join(target, "WORKFLOW.github.md"))) ||
      (await exists(path.join(target, "WORKFLOW.gitlab.md"))) ||
      (await exists(path.join(target, ".claude/agents"))) ||
      (await exists(path.join(target, ".codex/agents")))
    ) {
      throw new Error(
        "installer must copy only selected target runtime assets"
      );
    }
    const contractFiles = await Promise.all(
      ["AGENTS.md", "CLAUDE.md"].map(async (relativePath) => ({
        content: await readFile(path.join(target, relativePath), "utf-8"),
        relativePath
      }))
    );
    const managedFiles = contractFiles
      .filter(({ content }) => content.includes("harness:managed"))
      .map(({ relativePath }) => relativePath);
    if (managedFiles.length > 0) {
      throw new Error(
        `installer must not emit managed markers: ${managedFiles.join(",")}`
      );
    }
    const hooks = await Bun.spawn(
      ["git", "config", "--local", "--get", "core.hooksPath"],
      { cwd: target }
    ).exited;
    if (hooks === 0) {
      throw new Error("installer must leave hooks inactive by default");
    }
    const localWorkflow = "# Local workflow\n";
    await writeFile(path.join(target, "WORKFLOW.md"), localWorkflow, "utf-8");
    const conflict = Bun.spawn(installerCommand, {
      cwd: fixture,
      stderr: "pipe",
      stdout: "pipe"
    });
    const conflictExit = await conflict.exited;
    const conflictStderr = await new Response(conflict.stderr).text();
    if (
      conflictExit === 0 ||
      !conflictStderr.includes("conflict: WORKFLOW.md")
    ) {
      throw new Error("installer must fail when a packaged asset conflicts");
    }
    if (
      (await readFile(path.join(target, "WORKFLOW.md"), "utf-8")) !==
      localWorkflow
    ) {
      throw new Error("installer must preserve a conflicting target asset");
    }
  } finally {
    await rm(fixture, { force: true, recursive: true });
  }
});

test("checked-in manifest matches the complete tracked Harness asset set", async () => {
  const checkedIn: unknown = JSON.parse(await readFile(manifestPath, "utf-8"));
  const checkedInPaths = manifestPaths(checkedIn);
  const computedPaths = manifestPaths(computeHarnessAssetManifest());
  const missing = computedPaths.filter(
    (entry) => !checkedInPaths.includes(entry)
  );
  const undeclared = checkedInPaths.filter(
    (entry) => !computedPaths.includes(entry)
  );
  if (missing.length > 0 || undeclared.length > 0) {
    throw new Error(
      `asset manifest mismatch: missing=${missing.join(",")} undeclared=${undeclared.join(",")}`
    );
  }
});
