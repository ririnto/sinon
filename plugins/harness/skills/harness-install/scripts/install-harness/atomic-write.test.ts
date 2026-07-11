import { mock, test } from "bun:test";
import {
  lstat,
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

const TEMPORARY_UUID = "11111111-1111-4111-8111-111111111111";

mock.module("node:crypto", () => ({
  randomUUID: () => TEMPORARY_UUID
}));

const { prepareAtomicWrite } = await import("./atomic-write.js");

const temporaryNameFor = (target: string): string =>
  path.join(
    path.dirname(target),
    `.harness-tmp-${TEMPORARY_UUID}-${path.basename(target)}`
  );

const withFixture = async (
  check: (directory: string) => Promise<void>
): Promise<void> => {
  const directory = await mkdtemp(path.join(tmpdir(), "harness-atomic-write-"));
  try {
    await check(directory);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

const exists = async (filePath: string): Promise<boolean> => {
  try {
    await lstat(filePath);
    return true;
  } catch {
    return false;
  }
};

const assertRejected = async (
  operation: () => Promise<unknown>,
  expectedMessage: string
): Promise<void> => {
  try {
    await operation();
  } catch (error) {
    if (error instanceof Error && error.message.includes(expectedMessage)) {
      return;
    }
    throw error;
  }
  throw new Error(`expected failure containing: ${expectedMessage}`);
};

test("writes and commits a regular file through an exclusive same-directory temporary file", async () => {
  await withFixture(async (directory) => {
    const target = path.join(directory, "target.txt");

    const write = await prepareAtomicWrite(target, "regular");
    await write.write("installed\n");
    await write.commit();

    if ((await readFile(target, "utf-8")) !== "installed\n") {
      throw new Error(
        "regular atomic write must install the requested content"
      );
    }
  });
});

test("rejects an existing destination symlink without following it", async () => {
  await withFixture(async (directory) => {
    const outside = path.join(directory, "outside.txt");
    const target = path.join(directory, "target.txt");
    await writeFile(outside, "outside\n", "utf-8");
    await symlink(outside, target);

    const write = await prepareAtomicWrite(
      target,
      "existing_destination_symlink"
    );
    await write.write("installed\n");
    await assertRejected(() => write.commit(), "destination symlink");
    await write.discard();

    if ((await readFile(outside, "utf-8")) !== "outside\n") {
      throw new Error(
        "atomic write must not follow an existing destination symlink"
      );
    }
  });
});

test("rejects a dangling destination symlink", async () => {
  await withFixture(async (directory) => {
    const target = path.join(directory, "target.txt");
    await symlink(path.join(directory, "missing.txt"), target);

    const write = await prepareAtomicWrite(
      target,
      "dangling_destination_symlink"
    );
    await write.write("installed\n");
    await assertRejected(() => write.commit(), "destination symlink");
    await write.discard();

    const destination = await lstat(target);
    if (!destination.isSymbolicLink()) {
      throw new Error(
        "atomic write must leave a dangling destination symlink intact"
      );
    }
  });
});

test("refuses an attacker-created temporary symlink through exclusive no-follow creation", async () => {
  await withFixture(async (directory) => {
    const target = path.join(directory, "target.txt");
    const temporary = temporaryNameFor(target);
    const outside = path.join(directory, "outside.txt");
    await writeFile(outside, "outside\n", "utf-8");
    await symlink(outside, temporary);

    await assertRejected(
      () => prepareAtomicWrite(target, "temporary_symlink"),
      "temporary"
    );

    if ((await readFile(outside, "utf-8")) !== "outside\n") {
      throw new Error("exclusive temporary creation must not follow a symlink");
    }
  });
});

test("rejects a replaced parent directory before committing", async () => {
  await withFixture(async (directory) => {
    const parent = path.join(directory, "parent");
    const movedParent = path.join(directory, "moved-parent");
    const target = path.join(parent, "target.txt");
    await mkdir(parent);

    const write = await prepareAtomicWrite(target, "replaced_parent");
    await write.write("installed\n");
    await rename(parent, movedParent);
    await mkdir(parent);
    await assertRejected(() => write.commit(), "parent directory changed");
    await write.discard();

    if (await exists(path.join(parent, "target.txt"))) {
      throw new Error("parent replacement must prevent the final rename");
    }
  });
});

test("rejects a parent directory replaced with a symlink without writing through it", async () => {
  await withFixture(async (directory) => {
    const parent = path.join(directory, "parent");
    const movedParent = path.join(directory, "moved-parent");
    const outside = path.join(directory, "outside");
    const target = path.join(parent, "target.txt");
    await mkdir(parent);
    await mkdir(outside);

    const write = await prepareAtomicWrite(target, "parent_symlink");
    await write.write("installed\n");
    await rename(parent, movedParent);
    await symlink(outside, parent, "dir");
    await assertRejected(() => write.commit(), "parent directory changed");
    await write.discard();

    if (await exists(path.join(outside, "target.txt"))) {
      throw new Error(
        "parent symlink replacement must not receive the final rename"
      );
    }
  });
});

test("discards its owned temporary file without removing an unrelated destination", async () => {
  await withFixture(async (directory) => {
    const target = path.join(directory, "target.txt");
    const temporary = temporaryNameFor(target);

    const write = await prepareAtomicWrite(target, "discard");
    await write.write("installed\n");
    await write.discard();

    if (await exists(temporary)) {
      throw new Error("discard must remove its temporary file");
    }
    if (await exists(target)) {
      throw new Error("discard must not create the destination");
    }
  });
});
