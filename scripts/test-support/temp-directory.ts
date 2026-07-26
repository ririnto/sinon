import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

export const withTempDirectory = async <T>(
  prefix: string,
  use: (directory: string) => Promise<T>
): Promise<T> => {
  const directory = await mkdtemp(path.join(tmpdir(), prefix));
  try {
    return await use(directory);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};
