import {
  ensureCodexAgentDirectory,
  ensureOneRootContract,
  ensureOneRuntimeSymlink,
  ensureRootContracts,
  ensureRuntimeDirectories
} from "./contracts.js";
import { decideFileInstall } from "./decisions.js";
import {
  copyMode,
  matchCandidate,
  pathExists,
  readInstallAsset,
  readUtf8,
  replaceFile,
  temporaryDestination,
  writeUtf8
} from "./files.js";
import {
  checkSafeFileDestination,
  ensureSafeFileDestination,
  requiredSrc
} from "./paths.js";
import { buildPlan } from "./planning.js";
import {
  buildInstallResults,
  captureCandidateStates,
  digestContent,
  previousAssetsForConfig,
  readInstallRecord,
  requireCompatibleRecord,
  sourceDigestForCandidate
} from "./record.js";
import { fail } from "./types.js";
import type {
  InstallAssetRecord,
  InstallCandidate,
  InstallerConfig,
  InstallOperationResult
} from "./types.js";

const writeCandidateSource = async (
  candidate: InstallCandidate,
  label: string
): Promise<void> => {
  const src = requiredSrc(candidate);
  const temporary = await temporaryDestination(candidate.dst, label);
  await writeUtf8(temporary, await readInstallAsset(src));
  await copyMode(src, temporary);
  await replaceFile(temporary, candidate.dst);
};

const keptOwnership = (
  candidate: InstallCandidate,
  previous: InstallAssetRecord | undefined,
  currentDigest: string
): InstallOperationResult["ownership"] => {
  if (candidate.kind === "seed") {
    return "target";
  }
  if (
    previous?.ownership === "harness" &&
    previous.targetDigest === currentDigest
  ) {
    return "harness";
  }
  return "target";
};

const copyAssetFile = async (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<InstallOperationResult> => {
  const decision = await decideFileInstall(config, candidate, previousAssets);
  if (decision.write) {
    await ensureSafeFileDestination(candidate.dst);
    await writeCandidateSource(candidate, "install_file");
  }
  if (decision.diagnostic === "stderr") {
    console.error(decision.message);
  } else {
    console.log(decision.message);
  }
  return decision.operation;
};

const installGitkeep = async (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<InstallOperationResult> => {
  await ensureSafeFileDestination(candidate.dst);
  if (!(await pathExists(candidate.dst))) {
    await writeUtf8(candidate.dst, "");
    console.log(`write: ${candidate.dst}`);
    return { outcome: "created", ownership: "harness" };
  }
  if (config.force) {
    const temporary = await temporaryDestination(
      candidate.dst,
      "force_gitkeep"
    );
    await writeUtf8(temporary, "");
    await replaceFile(temporary, candidate.dst);
    console.log(`overwrite (--force): ${candidate.dst}`);
    return { outcome: "updated", ownership: "harness" };
  }
  console.log(`keep existing: ${candidate.dst}`);
  const currentDigest = digestContent(await readUtf8(candidate.dst));
  return {
    outcome: "kept",
    ownership: keptOwnership(
      candidate,
      previousAssets.get(candidate.dst),
      currentDigest
    )
  };
};

const installCandidate = (
  config: InstallerConfig,
  candidate: InstallCandidate,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<InstallOperationResult> => {
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      return copyAssetFile(config, candidate, previousAssets);
    }
    case "gitkeep": {
      return installGitkeep(config, candidate, previousAssets);
    }
    case "root-contract": {
      return ensureOneRootContract(config, candidate);
    }
    case "symlink": {
      return ensureOneRuntimeSymlink(config, candidate);
    }
    default: {
      return fail(`unsupported install candidate: ${candidate.dst}`);
    }
  }
};

const installCandidates = async (
  config: InstallerConfig,
  candidates: readonly InstallCandidate[],
  previousAssets: ReadonlyMap<string, InstallAssetRecord>,
  results: Map<string, InstallOperationResult>,
  index = 0
): Promise<void> => {
  if (index >= candidates.length) {
    return;
  }
  const candidate = candidates[index];
  if (candidate !== undefined) {
    results.set(
      candidate.dst,
      await installCandidate(config, candidate, previousAssets)
    );
  }
  await installCandidates(
    config,
    candidates,
    previousAssets,
    results,
    index + 1
  );
};

/** Install exactly one selected target path from the resolved install plan. */
export const installOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<readonly InstallAssetRecord[]> => {
  const candidates = await buildPlan(config);
  const candidate = matchCandidate(candidates, requestedPath);
  if (candidate.dst.startsWith(".codex/agents/")) {
    await ensureCodexAgentDirectory(config);
  }
  const previousAssets = await previousAssetsForConfig(config, true);
  const before = await captureCandidateStates([candidate]);
  const operation = await installCandidate(config, candidate, previousAssets);
  const operations = new Map<string, InstallOperationResult>([
    [candidate.dst, operation]
  ]);
  return buildInstallResults([candidate], before, operations);
};

/** Adopt one existing target-owned file without changing its content. */
export const adoptOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<readonly InstallAssetRecord[]> => {
  const record = await readInstallRecord();
  if (record === null || !record.complete) {
    return fail("--adopt requires an existing complete install record");
  }
  requireCompatibleRecord(record, config);
  const candidate = matchCandidate(await buildPlan(config), requestedPath);
  if (candidate.kind === "root-contract" || candidate.kind === "symlink") {
    return fail(`--adopt does not support ${candidate.kind}: ${requestedPath}`);
  }
  if (!record.assets.some((asset) => asset.path === candidate.dst)) {
    return fail(
      `--adopt path is missing from the complete install record: ${requestedPath}`
    );
  }
  await checkSafeFileDestination(candidate.dst);
  if (!(await pathExists(candidate.dst))) {
    return fail(`--adopt requires an existing file: ${requestedPath}`);
  }
  const currentDigest = digestContent(await readUtf8(candidate.dst));
  if (currentDigest === (await sourceDigestForCandidate(candidate))) {
    return fail(
      `--adopt is unnecessary because the target matches the source: ${requestedPath}`
    );
  }
  const before = await captureCandidateStates([candidate]);
  return buildInstallResults(
    [candidate],
    before,
    new Map([[candidate.dst, { outcome: "kept", ownership: "target" }]])
  );
};

/** Install the full harness plan for the selected stack and CI host. */
export const installFullPlan = async (
  config: InstallerConfig
): Promise<readonly InstallAssetRecord[]> => {
  const candidates = await buildPlan(config);
  const previousAssets = await previousAssetsForConfig(config, false);
  await ensureRuntimeDirectories(config);
  const before = await captureCandidateStates(candidates);
  const operations = new Map(await ensureRootContracts(config));
  await installCandidates(
    config,
    candidates.filter((candidate) => candidate.kind !== "root-contract"),
    previousAssets,
    operations
  );
  return buildInstallResults(candidates, before, operations);
};
