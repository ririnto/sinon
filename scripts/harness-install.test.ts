import { test } from "bun:test";
import { cp, mkdtemp, readFile, rm } from "node:fs/promises";
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

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const manifestPaths = (manifest: unknown): string[] => {
  if (!isRecord(manifest)) {
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
    await run(
      [
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
      ],
      fixture
    );

    const record: unknown = JSON.parse(
      await readFile(path.join(target, ".harness/install-record.json"), "utf-8")
    );
    if (
      !isRecord(record) ||
      record.schemaVersion !== 2 ||
      record.complete !== true ||
      !Array.isArray(record.assets)
    ) {
      throw new Error("installer must write a complete schema-v2 record");
    }
    if (
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
    const hooks = await Bun.spawn(
      ["git", "config", "--local", "--get", "core.hooksPath"],
      { cwd: target }
    ).exited;
    if (hooks === 0) {
      throw new Error("installer must leave hooks inactive by default");
    }
    await run(
      [
        "bun",
        path.join(
          cachedPlugin,
          "skills/harness-validate/scripts/validate-install-record.ts"
        ),
        target
      ],
      fixture
    );
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
