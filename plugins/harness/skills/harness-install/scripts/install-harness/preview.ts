import { readlink } from "node:fs/promises";

import { validationCommandForMode } from "./commands.js";
import {
  isSymlink,
  matchCandidate,
  pathExists,
  readInstallAsset,
  readUtf8
} from "./files.js";
import {
  applyManagedBlock,
  hasManagedBlock,
  renderManagedBlock
} from "./managed.js";
import { requiredRealTarget, requiredSrc } from "./paths.js";
import { buildPlan } from "./planning.js";
import {
  canRefreshOwnedAsset,
  digestContent,
  previousAssetsForConfig
} from "./record.js";
import { fail } from "./types.js";
import type {
  InstallAssetRecord,
  InstallCandidate,
  InstallerConfig
} from "./types.js";

const previewFileCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<void> => {
  if (
    candidate.seed === true &&
    (await pathExists(candidate.dst)) &&
    !config.force
  ) {
    console.log(`skip seed (target exists): ${candidate.dst}`);
    return;
  }
  if (!(await pathExists(candidate.dst))) {
    console.log(`write: ${candidate.dst}`);
    return;
  }
  const template = await readInstallAsset(requiredSrc(candidate));
  const current = await readUtf8(candidate.dst);
  if (current === template) {
    console.log(`keep existing (matches template): ${candidate.dst}`);
    return;
  }
  if (config.force) {
    console.log(`overwrite (--force): ${candidate.dst}`);
    return;
  }
  if (
    canRefreshOwnedAsset(
      previousAssets.get(candidate.dst),
      digestContent(current),
      digestContent(template)
    )
  ) {
    console.log(`refresh owned: ${candidate.dst}`);
    return;
  }
  console.error(
    `drift: ${candidate.dst} differs from template (manual integration unresolved); rerun with --force to overwrite`
  );
};

const previewRootContractCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  const exists = await pathExists(realTarget);
  const current = exists ? await readUtf8(realTarget) : "";
  if (exists && hasManagedBlock(current) && !config.force) {
    const template = await readInstallAsset(requiredSrc(candidate));
    if (applyManagedBlock(current, template) === current) {
      console.log(`skip root contract: ${candidate.dst}`);
      return;
    }
    console.error(
      `drift root contract: ${candidate.dst} managed block differs from template; rerun with --force to update it`
    );
    return;
  }
  if (exists && !config.force) {
    console.error(
      `conflict root contract: ${realTarget} has no managed block; rerun with --force to add one while preserving existing content`
    );
    return;
  }
  console.log(
    exists
      ? `update root contract (--force): ${candidate.dst}`
      : `create root contract: ${candidate.dst}`
  );
};

const previewSymlinkCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  if (await isSymlink(candidate.dst)) {
    const currentTarget = await readlink(candidate.dst);
    if (currentTarget === candidate.symlinkTarget) {
      console.log(
        `skip existing symlink: ${candidate.dst} -> ${candidate.symlinkTarget ?? ""}`
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
      `skip existing symlink: ${candidate.dst} -> ${currentTarget}`
    );
    return;
  }
  if (await pathExists(candidate.dst)) {
    console.log(`skip existing: ${candidate.dst}`);
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
  if (await pathExists(candidate.dst)) {
    console.log(
      config.force
        ? `overwrite (--force): ${candidate.dst}`
        : `keep existing: ${candidate.dst}`
    );
    return;
  }
  console.log(`write: ${candidate.dst}`);
};

const showRootContractCandidate = async (
  candidate: InstallCandidate
): Promise<void> => {
  const realTarget = requiredRealTarget(candidate);
  if (
    (await pathExists(realTarget)) &&
    hasManagedBlock(await readUtf8(realTarget))
  ) {
    return;
  }
  if (await pathExists(realTarget)) {
    console.error(
      `note: requested root contract managed block would be added to existing file: ${realTarget}`
    );
  }
  const template = await readInstallAsset(requiredSrc(candidate));
  process.stdout.write(renderManagedBlock(template));
};

const previewCandidateStatus = async (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<void> => {
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      await previewFileCandidate(config, candidate, previousAssets);
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
  previousAssets: ReadonlyMap<string, InstallAssetRecord>,
  index = 0
): Promise<void> => {
  if (index >= candidates.length) {
    return;
  }
  const candidate = candidates[index];
  if (candidate !== undefined) {
    await previewCandidateStatus(config, candidate, previousAssets);
  }
  await previewCandidates(config, candidates, previousAssets, index + 1);
};

/** Print the selected install set and each candidate status without writing files. */
export const previewInstallSet = async (
  config: InstallerConfig
): Promise<void> => {
  console.log(`target: ${process.cwd()}`);
  console.log(`mode: ${config.mode}`);
  console.log(`ci-host: ${config.ciHost}`);
  console.log(`validation command: ${validationCommandForMode(config.mode)}`);
  await previewCandidates(
    config,
    await buildPlan(config),
    await previousAssetsForConfig(config, false)
  );
};

/** Print rendered content for one target path without writing files. */
export const showOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<void> => {
  const candidate = matchCandidate(await buildPlan(config), requestedPath);
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      process.stdout.write(await readInstallAsset(requiredSrc(candidate)));
      return;
    }
    case "root-contract": {
      await showRootContractCandidate(candidate);
      break;
    }
    case "gitkeep": {
      break;
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
