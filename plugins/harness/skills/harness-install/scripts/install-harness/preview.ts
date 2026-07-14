import { readlink } from "node:fs/promises";

import { validationCommandForMode } from "./commands.js";
import { renderCandidateContent } from "./content.js";
import { decideFileInstall } from "./decisions.js";
import { isSymlink, matchCandidate, pathExists, readUtf8 } from "./files.js";
import {
  checkSafeFileDestination,
  checkSafeParentDir,
  requiredRealTarget
} from "./paths.js";
import { buildPlan } from "./planning.js";
import { fail } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const previewFileCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const decision = await decideFileInstall(config, candidate);
  if (decision.diagnostic === "stderr") {
    console.error(decision.message);
  } else {
    console.log(decision.message);
  }
};

const previewRootContractCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  await checkSafeFileDestination(realTarget);
  const exists = await pathExists(realTarget);
  if (!exists) {
    console.log(`create root contract: ${candidate.dst}`);
    return;
  }
  const current = await readUtf8(realTarget);
  const source = await renderCandidateContent(candidate);
  if (current === source) {
    console.log(`keep root contract: ${candidate.dst}`);
    return;
  }
  if (config.force) {
    console.log(`update root contract (--force): ${candidate.dst}`);
    return;
  }
  console.error(
    `preserve root contract: ${candidate.dst} differs from the packaged source; rerun with --force to replace it`
  );
};

const previewSymlinkCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  await checkSafeParentDir(candidate.dst);
  if (await isSymlink(candidate.dst)) {
    const currentTarget = await readlink(candidate.dst);
    if (currentTarget === candidate.symlinkTarget) {
      console.log(
        `keep existing symlink: ${candidate.dst} -> ${candidate.symlinkTarget ?? ""}`
      );
      return;
    }
    if (config.force) {
      console.log(
        `replace symlink (--force): ${candidate.dst} -> ${candidate.symlinkTarget ?? ""}`
      );
      return;
    }
    console.error(
      `conflict symlink: ${candidate.dst} points to ${currentTarget}; rerun with --force to replace it`
    );
    return;
  }
  if (await pathExists(candidate.dst)) {
    console.error(`conflict symlink: ${candidate.dst} already exists`);
    return;
  }
  console.log(
    `create symlink: ${candidate.dst} -> ${candidate.symlinkTarget ?? ""}`
  );
};

const previewGitkeepCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  await checkSafeFileDestination(candidate.dst);
  if (!(await pathExists(candidate.dst))) {
    console.log(`write: ${candidate.dst}`);
    return;
  }
  const current = await readUtf8(candidate.dst);
  if (current === "") {
    console.log(`keep existing: ${candidate.dst}`);
    return;
  }
  if (config.force) {
    console.log(`overwrite (--force): ${candidate.dst}`);
    return;
  }
  console.error(
    `conflict: ${candidate.dst} differs from the packaged empty file; preserving target; rerun with --force to overwrite`
  );
};

const showRootContractCandidate = async (
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  await checkSafeFileDestination(realTarget);
  process.stdout.write(await renderCandidateContent(candidate));
};

const previewCandidateStatus = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      await previewFileCandidate(config, candidate);
      return;
    }
    case "root-contract": {
      await previewRootContractCandidate(config, candidate);
      return;
    }
    case "symlink": {
      await previewSymlinkCandidate(config, candidate);
      return;
    }
    case "gitkeep": {
      await previewGitkeepCandidate(config, candidate);
      return;
    }
    default: {
      return fail(`skip unknown candidate: ${candidate.dst}`);
    }
  }
};

const previewCandidates = async (
  config: InstallerConfig,
  candidates: readonly InstallCandidate[],
  index = 0
): Promise<void> => {
  if (index >= candidates.length) {
    return;
  }
  const candidate = candidates[index];
  if (candidate !== undefined) {
    await previewCandidateStatus(config, candidate);
  }
  await previewCandidates(config, candidates, index + 1);
};

/** Print the selected install set and each candidate status without writing files. */
export const previewInstallSet = async (
  config: InstallerConfig
): Promise<void> => {
  console.log(`target: ${process.cwd()}`);
  console.log(`mode: ${config.mode}`);
  console.log(`ci-host: ${config.ciHost}`);
  console.log(`validation command: ${validationCommandForMode(config.mode)}`);
  await previewCandidates(config, await buildPlan(config));
};

/** Print rendered content for one target path without writing files. */
export const showOneTargetPath = async (
  _config: InstallerConfig,
  requestedPath: string
): Promise<void> => {
  const candidate = matchCandidate(await buildPlan(_config), requestedPath);
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      await checkSafeFileDestination(candidate.dst);
      process.stdout.write(await renderCandidateContent(candidate));
      return;
    }
    case "root-contract": {
      await showRootContractCandidate(candidate);
      return;
    }
    case "gitkeep": {
      await checkSafeFileDestination(candidate.dst);
      return;
    }
    case "symlink": {
      return fail(
        `--show on symlink entry is not supported; run full install to create ${requestedPath}`
      );
    }
    default: {
      return fail(`requested path is not renderable: ${requestedPath}`);
    }
  }
};
