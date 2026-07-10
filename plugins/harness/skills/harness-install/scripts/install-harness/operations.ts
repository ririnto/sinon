import path from "node:path";

import { workflowAssetNameForCiHost, workflowNameForMode } from "./commands.js";
import {
  ensureOneRootContract,
  ensureOneRuntimeSymlink,
  ensureRootContracts,
  ensureRuntimeSymlinks
} from "./contracts.js";
import {
  copyMode,
  listTrackedTreeFiles,
  matchCandidate,
  pathExists,
  readInstallAsset,
  readUtf8,
  replaceFile,
  temporaryDestination,
  writeUtf8
} from "./files.js";
import {
  ensureSafeFileDestination,
  isCommonSkipPath,
  isDirectTemplateEntry,
  isHostTemplatePath,
  requiredSrc,
  toPosixRelative
} from "./paths.js";
import { buildPlan } from "./planning.js";
import {
  buildInstallResults,
  canRefreshOwnedAsset,
  captureCandidateStates,
  digestContent,
  previousAssetsForConfig
} from "./record.js";
import { fail, templateDir } from "./types.js";
import type { InstallAssetRecord, InstallerConfig } from "./types.js";

const runSerial = async <T>(
  items: readonly T[],
  action: (item: T) => Promise<void>,
  index = 0
): Promise<void> => {
  if (index >= items.length) {
    return;
  }
  const item = items[index];
  if (item !== undefined) {
    await action(item);
  }
  await runSerial(items, action, index + 1);
};

const copyAssetFile = async (
  config: InstallerConfig,
  srcFile: string,
  dst: string,
  seed: boolean,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<void> => {
  await ensureSafeFileDestination(dst);
  if ((await pathExists(dst)) && !config.force) {
    const source = await readInstallAsset(srcFile);
    const current = await readUtf8(dst);
    if (
      !seed &&
      canRefreshOwnedAsset(
        previousAssets.get(dst),
        digestContent(current),
        digestContent(source)
      )
    ) {
      const tmp = await temporaryDestination(dst, "refresh_file");
      await writeUtf8(tmp, source);
      await copyMode(srcFile, tmp);
      await replaceFile(tmp, dst);
      console.log(`refresh owned: ${dst}`);
      return;
    }
    if (seed) {
      console.log(`skip seed (target exists): ${dst}`);
    } else if (current === source) {
      console.log(`keep existing (matches template): ${dst}`);
    } else {
      console.error(
        `conflict: ${dst} differs from template; preserving target`
      );
    }
    return;
  }
  const tmp = await temporaryDestination(dst, "copy_file");
  await writeUtf8(tmp, await readInstallAsset(srcFile));
  await copyMode(srcFile, tmp);
  const hadExisting = await pathExists(dst);
  await replaceFile(tmp, dst);
  if (hadExisting) {
    console.log(
      seed ? `overwrite seed (--force): ${dst}` : `overwrite (--force): ${dst}`
    );
    return;
  }
  console.log(seed ? `deliver seed: ${dst}` : `write: ${dst}`);
};

const installOneGitkeepPath = async (
  config: InstallerConfig,
  keep: string,
  createOnly: boolean
): Promise<void> => {
  await ensureSafeFileDestination(keep);
  if (await pathExists(keep)) {
    if (createOnly) {
      return;
    }
    if (config.force) {
      const tmp = await temporaryDestination(keep, "install_one_gitkeep_path");
      await writeUtf8(tmp, "");
      await replaceFile(tmp, keep);
      console.log(`overwrite (--force): ${keep}`);
      return;
    }
    console.log(`keep existing: ${keep}`);
    return;
  }
  await writeUtf8(keep, "");
  console.log(`write: ${keep}`);
};

const ensureGitkeepPaths = async (config: InstallerConfig): Promise<void> => {
  await runSerial(
    [
      "docs/exec-plans/active/.gitkeep",
      "docs/exec-plans/completed/.gitkeep",
      "docs/generated/.gitkeep"
    ],
    (keep) => installOneGitkeepPath(config, keep, true)
  );
};

const copyTree = async (
  config: InstallerConfig,
  srcDir: string,
  dstDir: string,
  common: boolean,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>,
  overriddenDestinations: ReadonlySet<string>
): Promise<void> => {
  await runSerial(await listTrackedTreeFiles(srcDir), async (src) => {
    const rel = toPosixRelative(srcDir, src);
    if (overriddenDestinations.has(rel)) {
      return;
    }
    if (
      common &&
      (isCommonSkipPath(rel) || isHostTemplatePath(rel, config.ciHost))
    ) {
      return;
    }
    const selectedSrc =
      common && rel === "WORKFLOW.md"
        ? path.join(srcDir, workflowAssetNameForCiHost(config.ciHost))
        : src;
    const dst = dstDir === "" || dstDir === "." ? rel : `${dstDir}/${rel}`;
    await copyAssetFile(
      config,
      selectedSrc,
      dst,
      common && isDirectTemplateEntry(rel),
      previousAssets
    );
  });
};

const copyStackTree = async (
  config: InstallerConfig,
  srcDir: string,
  dstDir: string,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<void> => {
  const workflowName = workflowNameForMode(config.mode);
  await runSerial(await listTrackedTreeFiles(srcDir), async (src) => {
    const rel = toPosixRelative(srcDir, src);
    if (rel === ".gitlab-ci.yml") {
      if (config.ciHost === "gitlab" || config.ciHost === "both") {
        const dst = dstDir === "" || dstDir === "." ? rel : `${dstDir}/${rel}`;
        await copyAssetFile(config, src, dst, false, previousAssets);
      }
      return;
    }
    if (rel.startsWith(".github/workflows/")) {
      if (config.ciHost === "github" || config.ciHost === "both") {
        const dst =
          dstDir === "" || dstDir === "."
            ? `.github/workflows/${workflowName}`
            : `${dstDir}/.github/workflows/${workflowName}`;
        await copyAssetFile(config, src, dst, false, previousAssets);
      }
      return;
    }
    const dst = dstDir === "" || dstDir === "." ? rel : `${dstDir}/${rel}`;
    await copyAssetFile(config, src, dst, false, previousAssets);
  });
};

/** Install exactly one selected target path from the resolved install plan. */
export const installOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<readonly InstallAssetRecord[]> => {
  const candidates = await buildPlan(config);
  const candidate = matchCandidate(candidates, requestedPath);
  const previousAssets = await previousAssetsForConfig(config, true);
  const before = await captureCandidateStates([candidate]);
  switch (candidate.kind) {
    case "file":
    case "stack-file":
    case "seed": {
      await copyAssetFile(
        config,
        requiredSrc(candidate),
        candidate.dst,
        candidate.seed === true,
        previousAssets
      );
      break;
    }
    case "gitkeep": {
      await installOneGitkeepPath(config, candidate.dst, false);
      break;
    }
    case "root-contract": {
      await ensureOneRootContract(config, candidate);
      break;
    }
    case "symlink": {
      await ensureOneRuntimeSymlink(config, candidate);
      break;
    }
    default: {
      return fail(`unsupported --only target selection: ${requestedPath}`);
    }
  }
  return buildInstallResults([candidate], before, previousAssets);
};

/** Install the full harness plan for the selected stack and CI host. */
export const installFullPlan = async (
  config: InstallerConfig
): Promise<readonly InstallAssetRecord[]> => {
  const candidates = await buildPlan(config);
  const previousAssets = await previousAssetsForConfig(config, false);
  const before = await captureCandidateStates(candidates);
  const stackDestinations = new Set(
    candidates
      .filter((candidate) => candidate.kind === "stack-file")
      .map((candidate) => candidate.dst)
  );
  await ensureRootContracts(config);
  await ensureRuntimeSymlinks(config);
  await copyTree(
    config,
    path.join(templateDir, "common"),
    ".",
    true,
    previousAssets,
    stackDestinations
  );
  await ensureRuntimeSymlinks(config);
  await ensureGitkeepPaths(config);
  await copyStackTree(
    config,
    path.join(templateDir, config.mode),
    ".",
    previousAssets
  );
  return buildInstallResults(candidates, before, previousAssets);
};
