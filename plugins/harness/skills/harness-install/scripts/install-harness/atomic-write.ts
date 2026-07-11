import { randomUUID } from "node:crypto";
import { constants } from "node:fs";
import { lstat, open, rename, stat, unlink } from "node:fs/promises";
import path from "node:path";

import { fail } from "./types.js";

type FileIdentity = Readonly<{
  dev: number;
  ino: number;
}>;

export type AtomicWrite = Readonly<{
  commit: () => Promise<void>;
  copyMode: (source: string) => Promise<void>;
  discard: () => Promise<void>;
  write: (content: string) => Promise<void>;
}>;

const temporaryName = (target: string): string =>
  path.join(
    path.dirname(target),
    `.harness-tmp-${randomUUID()}-${path.basename(target)}`
  );

const identityFor = (entry: { dev: number; ino: number }): FileIdentity => ({
  dev: entry.dev,
  ino: entry.ino
});

const matchesIdentity = (
  entry: { dev: number; ino: number },
  identity: FileIdentity
): boolean => entry.dev === identity.dev && entry.ino === identity.ino;

const lstatOrNull = async (filePath: string) => {
  try {
    return await lstat(filePath);
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "ENOENT") {
      return null;
    }
    throw error;
  }
};

const requireParentDirectory = async (
  parent: string,
  label: string
): Promise<FileIdentity> => {
  const entry = await lstat(parent);
  if (!entry.isDirectory()) {
    return fail(`[${label}] atomic write parent is not a directory: ${parent}`);
  }
  return identityFor(entry);
};

class PreparedAtomicWrite {
  readonly #label: string;
  readonly #parent: string;
  readonly #parentIdentity: FileIdentity;
  readonly #target: string;
  readonly #temporary: string;
  readonly #temporaryIdentity: FileIdentity;
  #closed = false;
  #committed = false;
  #file: Awaited<ReturnType<typeof open>>;

  constructor(
    target: string,
    label: string,
    parent: string,
    parentIdentity: FileIdentity,
    temporary: string,
    file: Awaited<ReturnType<typeof open>>,
    temporaryIdentity: FileIdentity
  ) {
    this.#label = label;
    this.#parent = parent;
    this.#parentIdentity = parentIdentity;
    this.#target = target;
    this.#temporary = temporary;
    this.#file = file;
    this.#temporaryIdentity = temporaryIdentity;
  }

  async write(content: string): Promise<void> {
    await this.#file.writeFile(content, "utf-8");
  }

  async copyMode(source: string): Promise<void> {
    const sourceStat = await stat(source);
    await this.#file.chmod(sourceStat.mode % 0o1000);
  }

  async commit(): Promise<void> {
    await this.#file.sync();
    await this.#assertParentIdentity();
    await this.#assertTemporaryIdentity();
    await this.#assertDestinationIsNotSymlink();
    await this.#close();
    await rename(this.#temporary, this.#target);
    this.#committed = true;
  }

  async discard(): Promise<void> {
    await this.#close();
    if (this.#committed) {
      return;
    }
    const current = await lstatOrNull(this.#temporary);
    if (
      current === null ||
      !current.isFile() ||
      !matchesIdentity(current, this.#temporaryIdentity)
    ) {
      return;
    }
    await unlink(this.#temporary);
  }

  async #assertParentIdentity(): Promise<void> {
    const current = await lstat(this.#parent);
    if (
      !current.isDirectory() ||
      !matchesIdentity(current, this.#parentIdentity)
    ) {
      fail(
        `[${this.#label}] atomic write parent directory changed: ${this.#parent}`
      );
    }
  }

  async #assertTemporaryIdentity(): Promise<void> {
    const current = await lstatOrNull(this.#temporary);
    if (
      current === null ||
      !current.isFile() ||
      !matchesIdentity(current, this.#temporaryIdentity)
    ) {
      fail(
        `[${this.#label}] atomic write temporary path changed: ${this.#temporary}`
      );
    }
  }

  async #assertDestinationIsNotSymlink(): Promise<void> {
    const destination = await lstatOrNull(this.#target);
    if (destination?.isSymbolicLink() === true) {
      fail(
        `[${this.#label}] atomic write destination symlink: ${this.#target}`
      );
    }
  }

  async #close(): Promise<void> {
    if (this.#closed) {
      return;
    }
    await this.#file.close();
    this.#closed = true;
  }
}

// Best-effort path validation assumes trusted, non-hostile target ancestry.
export const prepareAtomicWrite = async (
  target: string,
  label: string
): Promise<AtomicWrite> => {
  const parent = path.dirname(target);
  const parentIdentity = await requireParentDirectory(parent, label);
  const temporary = temporaryName(target);
  let file: Awaited<ReturnType<typeof open>>;
  try {
    file = await open(
      temporary,
      constants.O_WRONLY +
        constants.O_CREAT +
        constants.O_EXCL +
        constants.O_NOFOLLOW,
      0o600
    );
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "EEXIST") {
      return fail(
        `[${label}] atomic write temporary already exists: ${temporary}`
      );
    }
    throw error;
  }
  const temporaryStat = await file.stat();
  if (!temporaryStat.isFile()) {
    await file.close();
    return fail(
      `[${label}] atomic write temporary is not a regular file: ${temporary}`
    );
  }
  const temporaryIdentity = identityFor(temporaryStat);
  if (temporaryIdentity.dev !== parentIdentity.dev) {
    await file.close();
    return fail(
      `[${label}] atomic write temporary is not on the parent filesystem: ${temporary}`
    );
  }
  const prepared = new PreparedAtomicWrite(
    target,
    label,
    parent,
    parentIdentity,
    temporary,
    file,
    temporaryIdentity
  );
  return {
    commit: () => prepared.commit(),
    copyMode: (source) => prepared.copyMode(source),
    discard: () => prepared.discard(),
    write: (content) => prepared.write(content)
  };
};
