import { test } from "bun:test";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { installRecordPath, writeInstallRecord } from "./record-persistence.js";
import { readInstallRecord } from "./record-schema.js";
import type { InstallAssetRecord, InstallerConfig } from "./types.js";

const configFor = (targetRoot: string): InstallerConfig => ({
  action: "only",
  activateHooks: false,
  ciHost: "none",
  force: false,
  mode: "bun",
  selectedPath: "ARCHITECTURE.md",
  targetRoot
});

const result: InstallAssetRecord = {
  kind: "file",
  outcome: "updated",
  ownership: "harness",
  path: "ARCHITECTURE.md",
  sourceDigest: "source",
  targetDigest: "target"
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

test("writes a partial install record with the established durable shape", async () => {
  const targetRoot = await mkdtemp(path.join(tmpdir(), "harness-record-"));
  const previousDirectory = process.cwd();
  try {
    await mkdir(path.join(targetRoot, ".harness"));
    process.chdir(targetRoot);

    await writeInstallRecord(configFor(targetRoot), [result], false);

    const value: unknown = JSON.parse(
      await readFile(path.join(targetRoot, installRecordPath), "utf-8")
    );
    if (
      !isRecord(value) ||
      value["schemaVersion"] !== 2 ||
      value["complete"] !== false ||
      !Array.isArray(value["assets"])
    ) {
      throw new Error(
        "targeted write must preserve the schema-v2 partial record"
      );
    }
    const asset = value["assets"].find(
      (entry) => isRecord(entry) && entry["path"] === result.path
    );
    if (asset === undefined) {
      throw new Error("targeted write must persist its updated asset outcome");
    }
  } finally {
    process.chdir(previousDirectory);
    await rm(targetRoot, { force: true, recursive: true });
  }
});

test("rejects schema-v1 install records without migration", async () => {
  const targetRoot = await mkdtemp(path.join(tmpdir(), "harness-record-"));
  const previousDirectory = process.cwd();
  try {
    await mkdir(path.join(targetRoot, ".harness"));
    await writeFile(
      path.join(targetRoot, installRecordPath),
      `${JSON.stringify({
        canonicalCheckCommand: "bun run check",
        ciHost: "none",
        fixCommand: "bun run fix",
        installedAssets: ["ARCHITECTURE.md"],
        mode: "bun",
        prePushCommand: "bun test",
        schemaVersion: 1
      })}\n`,
      "utf-8"
    );
    process.chdir(targetRoot);

    let message = "";
    try {
      await readInstallRecord();
    } catch (error) {
      message = error instanceof Error ? error.message : String(error);
    }
    if (!message.includes("invalid schema-v2 record")) {
      throw new Error("schema-v1 records must be rejected without migration");
    }
  } finally {
    process.chdir(previousDirectory);
    await rm(targetRoot, { force: true, recursive: true });
  }
});
