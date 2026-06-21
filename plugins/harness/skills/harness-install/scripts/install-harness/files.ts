import { constants } from "node:fs";
import {
  access,
  chmod,
  lstat,
  readFile,
  rename,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import path from "node:path";

import type { InstallCandidate } from "./types.js";
import { fail, skillDir } from "./types.js";

export const pathExists = (filePath: string): Promise<boolean> =>
  Bun.file(filePath).exists();

export const isDirectory = async (filePath: string): Promise<boolean> => {
  const stat = await Bun.file(filePath)
    .stat()
    .catch(() => null);
  return stat?.isDirectory() === true;
};

export const isFile = async (filePath: string): Promise<boolean> => {
  const stat = await Bun.file(filePath)
    .stat()
    .catch(() => null);
  return stat?.isFile() === true;
};

export const isSymlink = async (filePath: string): Promise<boolean> => {
  const stat = await lstat(filePath).catch(() => null);
  return stat?.isSymbolicLink() === true;
};

export const isExecutable = async (filePath: string): Promise<boolean> => {
  try {
    await access(filePath, constants.X_OK);
    return true;
  } catch {
    return false;
  }
};

export const readUtf8 = (filePath: string): Promise<string> =>
  readFile(filePath, "utf-8");

export const writeUtf8 = async (
  filePath: string,
  content: string
): Promise<void> => {
  await writeFile(filePath, content, "utf-8");
};

export const replaceFile = async (
  tmp: string,
  target: string
): Promise<void> => {
  await rename(tmp, target);
};

export const removePath = async (filePath: string): Promise<void> => {
  await rm(filePath, { force: true, recursive: false });
};

export const createSymlink = async (
  target: string,
  linkPath: string
): Promise<void> => {
  await symlink(target, linkPath, "dir");
};

export const temporaryDestination = async (
  dst: string,
  label: string
): Promise<string> => {
  const parent = path.dirname(dst) === "." ? "." : path.dirname(dst);
  const tmp = path.join(
    parent,
    `.harness-tmp-${process.pid}-${path.basename(dst)}`
  );
  if (await pathExists(tmp)) {
    fail(
      `[${label}] temporary destination already exists: ${tmp} (cleanup or retry)`
    );
  }
  return tmp;
};

export const readInstallAsset = async (assetFile: string): Promise<string> => {
  if (!(await isFile(assetFile))) {
    fail(`[read_install_asset] missing asset: ${assetFile}`);
  }
  return readUtf8(assetFile);
};

export const listTrackedTreeFiles = async (
  srcDir: string
): Promise<readonly string[]> => {
  if (!(await isDirectory(srcDir))) {
    return [];
  }
  const srcRel = path.relative(skillDir, srcDir).split("\\").join("/");
  const proc = Bun.spawnSync(
    ["git", "-C", skillDir, "ls-files", "--", srcRel],
    {
      stderr: "pipe",
      stdout: "pipe"
    }
  );
  if (!proc.success) {
    fail(`git ls-files failed for ${srcRel}: ${proc.stderr.toString().trim()}`);
  }
  return proc.stdout
    .toString()
    .split(/\r?\n/u)
    .filter(Boolean)
    .map((line) => path.join(skillDir, line));
};

export const copyMode = async (srcFile: string, tmp: string): Promise<void> => {
  if (await isExecutable(srcFile)) {
    await chmod(tmp, 0o755);
  }
};

export const matchCandidate = (
  candidates: readonly InstallCandidate[],
  targetPath: string
): InstallCandidate => {
  const candidate = candidates.find((entry) => entry.dst === targetPath);
  if (candidate !== undefined) {
    return candidate;
  }
  return fail(
    `requested path is not in the selected install set: ${targetPath}`
  );
};
