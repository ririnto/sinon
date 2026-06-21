import path from "node:path";

import { workflowAssetNameForCiHost, workflowNameForMode } from "./commands.js";
import {
  ensureOneRootContract,
  ensureRootContracts,
  ensureRuntimeSymlinks
} from "./contracts.js";
import {
  copyMode,
  listTrackedTreeFiles,
  matchCandidate,
  pathExists,
  readInstallAsset,
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
import { fail, templateDir } from "./types.js";
import type { InstallerConfig } from "./types.js";

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
  seed: boolean
): Promise<void> => {
  await ensureSafeFileDestination(dst);
  if ((await pathExists(dst)) && !config.force) {
    console.log(
      seed ? `skip seed (target exists): ${dst}` : `keep existing: ${dst}`
    );
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
  common: boolean
): Promise<void> => {
  await runSerial(await listTrackedTreeFiles(srcDir), async (src) => {
    const rel = toPosixRelative(srcDir, src);
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
      common && isDirectTemplateEntry(rel)
    );
  });
};

const copyStackTree = async (
  config: InstallerConfig,
  srcDir: string,
  dstDir: string
): Promise<void> => {
  const workflowName = workflowNameForMode(config.mode);
  await runSerial(await listTrackedTreeFiles(srcDir), async (src) => {
    const rel = toPosixRelative(srcDir, src);
    if (rel === ".gitlab-ci.yml") {
      if (config.ciHost === "gitlab" || config.ciHost === "both") {
        const dst = dstDir === "" || dstDir === "." ? rel : `${dstDir}/${rel}`;
        await copyAssetFile(config, src, dst, false);
      }
      return;
    }
    if (rel.startsWith(".github/workflows/")) {
      if (config.ciHost === "github" || config.ciHost === "both") {
        const dst =
          dstDir === "" || dstDir === "."
            ? `.github/workflows/${workflowName}`
            : `${dstDir}/.github/workflows/${workflowName}`;
        await copyAssetFile(config, src, dst, false);
      }
      return;
    }
    const dst = dstDir === "" || dstDir === "." ? rel : `${dstDir}/${rel}`;
    await copyAssetFile(config, src, dst, false);
  });
};

/** Install exactly one selected target path from the resolved install plan. */
export const installOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<void> => {
  const candidate = matchCandidate(await buildPlan(config), requestedPath);
  switch (candidate.kind) {
    case "file":
    case "stack-file":
    case "seed": {
      await copyAssetFile(
        config,
        requiredSrc(candidate),
        candidate.dst,
        candidate.seed === true
      );
      return;
    }
    case "gitkeep": {
      await installOneGitkeepPath(config, candidate.dst, false);
      return;
    }
    case "root-contract": {
      await ensureOneRootContract(config, candidate);
      return;
    }
    case "symlink": {
      return fail(
        `cannot place symlink entry with --only; run full install for ${requestedPath}`
      );
    }
    default: {
      return fail(`unsupported --only target selection: ${requestedPath}`);
    }
  }
};

/** Install the full harness plan for the selected stack and CI host. */
export const installFullPlan = async (
  config: InstallerConfig
): Promise<void> => {
  await ensureRootContracts(config);
  await copyTree(config, path.join(templateDir, "common"), ".", true);
  await ensureRuntimeSymlinks(config);
  await ensureGitkeepPaths(config);
  await copyStackTree(config, path.join(templateDir, config.mode), ".");
};
