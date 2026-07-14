import { lstat, mkdir, readlink } from "node:fs/promises";
import path from "node:path";

import { prepareAtomicWrite } from "./atomic-write.js";
import { renderCandidateContent } from "./content.js";
import {
  createSymlink,
  isDirectory,
  isSymlink,
  pathExists,
  readUtf8,
  removePath
} from "./files.js";
import {
  ensureSafeFileDestination,
  ensureSafeParentDir,
  ensureSafeRelativePath,
  requiredRealTarget,
  requiredSrc
} from "./paths.js";
import { fail, templateDir } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const writeRootContract = async (
  candidate: InstallCandidate,
  exists: boolean
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  const write = await prepareAtomicWrite(realTarget, "write_root_contract");
  try {
    await write.write(await renderCandidateContent(candidate));
    await write.copyMode(requiredSrc(candidate));
    await write.commit();
  } finally {
    await write.discard();
  }
  console.log(
    exists
      ? `update root contract (--force): ${candidate.dst}`
      : `create root contract: ${candidate.dst}`
  );
};

const ensureRootContract = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  await ensureSafeFileDestination(realTarget);
  if (await isSymlink(realTarget)) {
    return fail(
      `conflict root contract: ${candidate.dst} is a symlink; replace it with a regular file before installing`
    );
  }
  if (!(await pathExists(realTarget))) {
    await writeRootContract(candidate, false);
    return;
  }
  const current = await readUtf8(realTarget);
  const source = await renderCandidateContent(candidate);
  if (current === source) {
    console.log(`keep root contract: ${candidate.dst}`);
    return;
  }
  if (!config.force) {
    console.error(
      `preserve root contract: ${candidate.dst} differs from the packaged source; rerun with --force to replace it`
    );
    return;
  }
  await writeRootContract(candidate, true);
};

const ensureTargetSymlink = async (
  config: InstallerConfig,
  linkPath: string,
  target: string
): Promise<void> => {
  if (await isSymlink(linkPath)) {
    const currentTarget = await readlink(linkPath);
    if (currentTarget === target) {
      console.log(`keep existing symlink: ${linkPath} -> ${target}`);
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

/** Ensure the portable Agent Skill link parent before installing assets. */
export const ensureAgentSkillDirectory = async (
  config: InstallerConfig
): Promise<void> => {
  await ensureTargetDirectory(config, ".agents");
};

/** Ensure both root contract documents are present or safely preserved. */
export const ensureRootContracts = async (
  config: InstallerConfig
): Promise<void> => {
  await ensureRootContract(config, {
    dst: "AGENTS.md",
    kind: "root-contract",
    realTarget: "AGENTS.md",
    src: path.join(templateDir, "common", "AGENTS.md")
  });
  await ensureRootContract(config, {
    dst: "CLAUDE.md",
    kind: "root-contract",
    realTarget: "CLAUDE.md",
    src: path.join(templateDir, "common", "CLAUDE.md")
  });
};

/** Install one selected root contract using the stateless byte decision. */
export const ensureOneRootContract = (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => ensureRootContract(config, candidate);

/** Install one selected runtime symlink after checking its real target. */
export const ensureOneRuntimeSymlink = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  if (candidate.symlinkTarget === undefined) {
    return fail(`candidate has no symlink target: ${candidate.dst}`);
  }
  if (!(await isDirectory(".claude/skills"))) {
    return fail(
      `cannot create ${candidate.dst}; install .claude/skills before selecting this symlink`
    );
  }
  await ensureTargetDirectory(config, ".agents");
  await ensureTargetSymlink(config, candidate.dst, candidate.symlinkTarget);
};
