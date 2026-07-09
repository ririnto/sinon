import { lstat, mkdir, readlink } from "node:fs/promises";
import path from "node:path";

import {
  copyMode,
  createSymlink,
  isDirectory,
  isSymlink,
  pathExists,
  readInstallAsset,
  readUtf8,
  removePath,
  replaceFile,
  writeUtf8
} from "./files.js";
import { applyManagedBlock, hasManagedBlock } from "./managed.js";
import {
  ensureSafeFileDestination,
  ensureSafeParentDir,
  ensureSafeRelativePath,
  requiredRealTarget,
  requiredSrc
} from "./paths.js";
import { fail, templateDir } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const writeRootContractUpdate = async (
  realTarget: string,
  dst: string,
  templatePath: string,
  exists: boolean
): Promise<void> => {
  const tmp = `${realTarget}.harness.tmp.${process.pid}`;
  await ensureSafeFileDestination(tmp);
  const template = await readInstallAsset(templatePath);
  const existing = exists ? await readUtf8(realTarget) : "";
  const content = applyManagedBlock(existing, template);
  await writeUtf8(tmp, content);
  await copyMode(templatePath, tmp);
  await replaceFile(tmp, realTarget);
  console.log(
    exists
      ? `update root contract (--force): ${dst}`
      : `create root contract: ${dst}`
  );
};

const checkRootContractConflict = async (dst: string): Promise<boolean> => {
  if (await isSymlink(dst)) {
    console.error(
      `conflict root contract: ${dst} is a symlink; rerun with --force to replace it`
    );
    return true;
  }
  await ensureSafeFileDestination(dst);
  if ((await pathExists(dst)) && !hasManagedBlock(await readUtf8(dst))) {
    console.error(
      `conflict root contract: ${dst} has no managed block; rerun with --force to add one while preserving existing content`
    );
    return true;
  }
  return false;
};

const ensureRootContract = async (
  config: InstallerConfig,
  dst: string,
  templatePath: string
): Promise<void> => {
  if ((await isSymlink(dst)) && config.force) {
    await removePath(dst);
  }
  await ensureSafeFileDestination(dst);
  if (!(await pathExists(dst))) {
    await writeRootContractUpdate(dst, dst, templatePath, false);
    return;
  }
  const content = await readUtf8(dst);
  if (hasManagedBlock(content) && !config.force) {
    console.log(`skip root contract: ${dst}`);
    return;
  }
  if (!config.force) {
    console.error(
      `conflict root contract: ${dst} has no managed block; rerun with --force to add one while preserving existing content`
    );
    return;
  }
  await writeRootContractUpdate(dst, dst, templatePath, true);
};

const ensureTargetSymlink = async (
  config: InstallerConfig,
  linkPath: string,
  target: string
): Promise<void> => {
  if (await isSymlink(linkPath)) {
    const currentTarget = await readlink(linkPath);
    if (currentTarget === target) {
      return;
    }
    if (!config.force) {
      return fail(
        `conflict symlink: ${linkPath} points to ${currentTarget}; rerun with --force to replace it`
      );
    }
    await removePath(linkPath);
  } else if (await pathExists(linkPath)) {
    return fail(
      `conflict symlink: ${linkPath} already exists and is not a symlink`
    );
  }
  await ensureSafeFileDestination(linkPath);
  await createSymlink(target, linkPath);
  console.log(`create symlink: ${linkPath} -> ${target}`);
};

const ensureTargetDirectory = async (
  config: InstallerConfig,
  directoryPath: string
): Promise<void> => {
  if (await isSymlink(directoryPath)) {
    if (!config.force) {
      return fail(
        `conflict directory: ${directoryPath} is a symlink; rerun with --force to replace it`
      );
    }
    await removePath(directoryPath);
  }
  const current = await lstat(directoryPath).catch(() => null);
  if (current?.isDirectory() === true) {
    return;
  }
  if (current !== null) {
    return fail(
      `conflict directory: ${directoryPath} exists and is not a directory`
    );
  }
  ensureSafeRelativePath(directoryPath);
  const parent = path.dirname(directoryPath);
  if (parent !== "" && parent !== ".") {
    await ensureSafeParentDir(`${parent}/.keep`);
  }
  await mkdir(directoryPath);
  console.log(`create directory: ${directoryPath}`);
};

/** Ensure both root contract documents are present or safely updated. */
export const ensureRootContracts = async (
  config: InstallerConfig
): Promise<void> => {
  if (!config.force) {
    const hasConflict =
      (await checkRootContractConflict("AGENTS.md")) ||
      (await checkRootContractConflict("CLAUDE.md"));
    if (hasConflict) {
      return fail(
        "root contract conflicts must be resolved before installing assets"
      );
    }
  }
  await ensureRootContract(
    config,
    "AGENTS.md",
    path.join(templateDir, "common", "AGENTS.md")
  );
  await ensureRootContract(
    config,
    "CLAUDE.md",
    path.join(templateDir, "common", "CLAUDE.md")
  );
};

/** Install or update one root-contract candidate selected through `--only`. */
export const ensureOneRootContract = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  const templatePath = requiredSrc(candidate);
  if ((await isSymlink(realTarget)) && config.force) {
    await removePath(realTarget);
  }
  await ensureSafeFileDestination(realTarget);
  if (
    (await pathExists(realTarget)) &&
    hasManagedBlock(await readUtf8(realTarget)) &&
    !config.force
  ) {
    console.log(`skip root contract: ${candidate.dst}`);
    return;
  }
  if ((await pathExists(realTarget)) && !config.force) {
    return fail(
      `conflict root contract: ${realTarget} has no managed block; rerun with --force to add one while preserving existing content`
    );
  }
  await writeRootContractUpdate(
    realTarget,
    candidate.dst,
    templatePath,
    await pathExists(realTarget)
  );
};

/** Create runtime symlinks that expose Claude assets to other agent runtimes. */
export const ensureRuntimeSymlinks = async (
  config: InstallerConfig
): Promise<void> => {
  await ensureTargetDirectory(config, ".agents");
  await ensureTargetDirectory(config, ".codex");
  await ensureTargetDirectory(config, ".codex/agents");
  if (await isDirectory(".claude/skills")) {
    await ensureTargetSymlink(config, ".agents/skills", "../.claude/skills");
  }
};
