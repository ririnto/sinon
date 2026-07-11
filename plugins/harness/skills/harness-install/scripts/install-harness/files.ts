import { constants } from "node:fs";
import { access, lstat, readFile, rm, symlink } from "node:fs/promises";
import path from "node:path";

import { manifestFilesForSubdir } from "../asset-manifest.js";
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

export const removePath = async (filePath: string): Promise<void> => {
  await rm(filePath, { force: true, recursive: false });
};

export const createSymlink = async (
  target: string,
  linkPath: string
): Promise<void> => {
  await symlink(target, linkPath, "dir");
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
  const subdir = path.basename(srcDir);
  return manifestFilesForSubdir(skillDir, subdir);
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
