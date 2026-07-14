import { prepareAtomicWrite } from "./atomic-write.js";
import { renderCandidateContent } from "./content.js";
import {
  ensureAgentSkillDirectory,
  ensureOneRootContract,
  ensureOneRuntimeSymlink,
  ensureRootContracts
} from "./contracts.js";
import { decideFileInstall } from "./decisions.js";
import { matchCandidate, pathExists, readUtf8 } from "./files.js";
import { ensureSafeFileDestination, requiredSrc } from "./paths.js";
import { buildPlan } from "./planning.js";
import { fail } from "./types.js";
import type { InstallCandidate, InstallerConfig } from "./types.js";

const writeCandidateSource = async (
  candidate: InstallCandidate,
  label: string
): Promise<void> => {
  const src = requiredSrc(candidate);
  const write = await prepareAtomicWrite(candidate.dst, label);
  try {
    await write.write(await renderCandidateContent(candidate));
    await write.copyMode(src);
    await write.commit();
  } finally {
    await write.discard();
  }
};

const copyAssetFile = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  const decision = await decideFileInstall(config, candidate);
  if (decision.diagnostic === "stderr") {
    return fail(decision.message);
  }
  if (decision.write) {
    await ensureSafeFileDestination(candidate.dst);
    await writeCandidateSource(candidate, "install_file");
  }
  console.log(decision.message);
};

const installGitkeep = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  await ensureSafeFileDestination(candidate.dst);
  if (!(await pathExists(candidate.dst))) {
    const write = await prepareAtomicWrite(candidate.dst, "create_gitkeep");
    try {
      await write.write("");
      await write.commit();
    } finally {
      await write.discard();
    }
    console.log(`write: ${candidate.dst}`);
    return;
  }
  const current = await readUtf8(candidate.dst);
  if (current === "") {
    console.log(`keep existing: ${candidate.dst}`);
    return;
  }
  if (!config.force) {
    return fail(
      `conflict: ${candidate.dst} differs from the packaged empty file; preserving target; rerun with --force to overwrite`
    );
  }
  const write = await prepareAtomicWrite(candidate.dst, "force_gitkeep");
  try {
    await write.write("");
    await write.commit();
  } finally {
    await write.discard();
  }
  console.log(`overwrite (--force): ${candidate.dst}`);
};

const installCandidate = async (
  config: InstallerConfig,
  candidate: InstallCandidate
): Promise<void> => {
  switch (candidate.kind) {
    case "file":
    case "seed":
    case "stack-file": {
      await copyAssetFile(config, candidate);
      return;
    }
    case "gitkeep": {
      await installGitkeep(config, candidate);
      return;
    }
    case "root-contract": {
      await ensureOneRootContract(config, candidate);
      return;
    }
    case "symlink": {
      await ensureOneRuntimeSymlink(config, candidate);
      return;
    }
    default: {
      return fail(`unsupported install candidate: ${candidate.dst}`);
    }
  }
};

const installCandidates = async (
  config: InstallerConfig,
  candidates: readonly InstallCandidate[],
  index = 0
): Promise<void> => {
  if (index >= candidates.length) {
    return;
  }
  const candidate = candidates[index];
  if (candidate !== undefined) {
    await installCandidate(config, candidate);
  }
  await installCandidates(config, candidates, index + 1);
};

/** Install exactly one selected target path from the resolved install plan. */
export const installOneTargetPath = async (
  config: InstallerConfig,
  requestedPath: string
): Promise<void> => {
  const candidate = matchCandidate(await buildPlan(config), requestedPath);
  await installCandidate(config, candidate);
};

/** Install the full Harness plan for the selected stack and CI host. */
export const installFullPlan = async (
  config: InstallerConfig
): Promise<void> => {
  const candidates = await buildPlan(config);
  await ensureAgentSkillDirectory(config);
  await ensureRootContracts(config);
  await installCandidates(
    config,
    candidates.filter((candidate) => candidate.kind !== "root-contract")
  );
};
