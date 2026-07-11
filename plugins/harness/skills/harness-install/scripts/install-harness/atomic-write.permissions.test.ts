import { test } from "bun:test";
import {
  chmod,
  lstat,
  mkdtemp,
  readdir,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { prepareAtomicWrite } from "./atomic-write.js";

const permissionBits = (mode: number): number => mode % 0o1000;

const withFixture = async (
  check: (directory: string) => Promise<void>
): Promise<void> => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "harness-atomic-write-permissions-")
  );
  try {
    await check(directory);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

const assertCopiedSourceMode = async (sourceMode: number): Promise<void> => {
  await withFixture(async (directory) => {
    const suffix = sourceMode.toString(8);
    const source = path.join(directory, `source-${suffix}.txt`);
    const target = path.join(directory, `target-${suffix}.txt`);
    await writeFile(source, "source\n", "utf-8");
    await chmod(source, sourceMode);

    const write = await prepareAtomicWrite(target, `mode_${suffix}`);
    const directoryEntries = await readdir(directory);
    const temporary = directoryEntries.find((entry) =>
      entry.startsWith(".harness-tmp-")
    );
    if (temporary === undefined) {
      throw new Error("atomic writes must create a temporary file");
    }
    const temporaryStat = await lstat(path.join(directory, temporary));
    if (permissionBits(temporaryStat.mode) !== 0o600) {
      throw new Error("atomic temporary files must begin with mode 0600");
    }

    await write.write("installed\n");
    await write.copyMode(source);
    await write.commit();

    const targetStat = await lstat(target);
    if (permissionBits(targetStat.mode) !== sourceMode) {
      throw new Error(
        `atomic writes must preserve source mode ${sourceMode.toString(8)}`
      );
    }
  });
};

test("commits packaged non-executable source permissions after beginning with a safe temporary mode", () =>
  assertCopiedSourceMode(0o644));

test("commits packaged executable source permissions after beginning with a safe temporary mode", () =>
  assertCopiedSourceMode(0o755));
