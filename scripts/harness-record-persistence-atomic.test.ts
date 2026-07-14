import { test } from "bun:test";
import { lstat, mkdir, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import type { AtomicWrite } from "../plugins/harness/skills/harness-install/scripts/install-harness/atomic-write.js";
import {
  installRecordPath,
  persistInstallRecord
} from "../plugins/harness/skills/harness-install/scripts/install-harness/record-persistence.js";
import type { InstallRecord } from "../plugins/harness/skills/harness-install/scripts/install-harness/record-schema.js";
import type { InstallAssetRecord } from "../plugins/harness/skills/harness-install/scripts/install-harness/types.js";

const result: InstallAssetRecord = {
  kind: "file",
  outcome: "updated",
  ownership: "harness",
  path: "ARCHITECTURE.md",
  sourceDigest: "source",
  targetDigest: "target"
};

const record: InstallRecord = {
  assets: [result],
  canonicalCheckCommand: "bun run check",
  ciHost: "none",
  complete: false,
  expectedAssets: [result.path],
  expectedPlanDigest: "plan",
  fixCommand: "bun run fix",
  mode: "bun",
  prePushCommand: "bun test",
  schemaVersion: 2
};

const withTarget = async (
  check: (targetRoot: string) => Promise<void>
): Promise<void> => {
  const targetRoot = await mkdtemp(path.join(tmpdir(), "harness-record-"));
  const previousDirectory = process.cwd();
  try {
    await mkdir(path.join(targetRoot, ".harness"));
    process.chdir(targetRoot);
    await check(targetRoot);
  } finally {
    process.chdir(previousDirectory);
    await rm(targetRoot, { force: true, recursive: true });
  }
};

test("discards an uncommitted record write without creating the target", async () => {
  await withTarget(async (targetRoot) => {
    let discarded = false;
    let written = "";

    try {
      await persistInstallRecord(
        record,
        (): Promise<AtomicWrite> =>
          Promise.resolve({
            commit: (): Promise<void> =>
              Promise.reject(new Error("atomic commit failed")),
            copyMode: (): Promise<void> => Promise.resolve(),
            discard: (): Promise<void> => {
              discarded = true;
              return Promise.resolve();
            },
            write: (content: string): Promise<void> => {
              written = content;
              return Promise.resolve();
            }
          })
      );
      throw new Error("record persistence must surface atomic commit failures");
    } catch (error) {
      if (
        !(error instanceof Error) ||
        error.message !== "atomic commit failed"
      ) {
        throw error;
      }
    }

    if (!discarded || written.length === 0) {
      throw new Error(
        "record persistence must discard the prepared atomic write"
      );
    }
    try {
      await lstat(path.join(targetRoot, installRecordPath));
      throw new Error(
        "failed atomic persistence must not commit a record target"
      );
    } catch (error) {
      if (
        error instanceof Error &&
        "code" in error &&
        error.code === "ENOENT"
      ) {
        return;
      }
      throw error;
    }
  });
});

test("preserves the primary commit error when atomic cleanup also fails", async () => {
  await withTarget(async () => {
    const primaryError = new Error("primary commit failure");
    let discardCalls = 0;
    let caught: unknown;

    try {
      await persistInstallRecord(
        record,
        (): Promise<AtomicWrite> =>
          Promise.resolve({
            commit: (): Promise<void> => Promise.reject(primaryError),
            copyMode: (): Promise<void> => Promise.resolve(),
            discard: (): Promise<void> => {
              discardCalls += 1;
              return Promise.reject(new Error("cleanup failure"));
            },
            write: (): Promise<void> => Promise.resolve()
          })
      );
    } catch (error) {
      caught = error;
    }

    if (caught !== primaryError || discardCalls !== 1) {
      throw new Error(
        "record persistence must preserve the primary error after one cleanup attempt"
      );
    }
  });
});

test("surfaces a cleanup error after a successful atomic commit", async () => {
  await withTarget(async () => {
    const cleanupError = new Error("cleanup failure");
    let discardCalls = 0;
    let caught: unknown;

    try {
      await persistInstallRecord(
        record,
        (): Promise<AtomicWrite> =>
          Promise.resolve({
            commit: (): Promise<void> => Promise.resolve(),
            copyMode: (): Promise<void> => Promise.resolve(),
            discard: (): Promise<void> => {
              discardCalls += 1;
              return Promise.reject(cleanupError);
            },
            write: (): Promise<void> => Promise.resolve()
          })
      );
    } catch (error) {
      caught = error;
    }

    if (caught !== cleanupError || discardCalls !== 1) {
      throw new Error(
        "record persistence must surface its only cleanup failure after one attempt"
      );
    }
  });
});
