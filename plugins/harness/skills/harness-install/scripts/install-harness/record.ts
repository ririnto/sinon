// -*- coding: utf-8 -*-

import { createHash } from "node:crypto";
import { lstat, readlink } from "node:fs/promises";

import {
  fixCommandForMode,
  prePushCommandForMode,
  validationCommandForMode
} from "./commands.js";
import {
  isSymlink,
  pathExists,
  readInstallAsset,
  readUtf8,
  replaceFile,
  temporaryDestination,
  writeUtf8
} from "./files.js";
import {
  managedBeginMarker,
  managedEndMarker,
  renderManagedBlock
} from "./managed.js";
import {
  ensureSafeFileDestination,
  requiredRealTarget,
  requiredSrc
} from "./paths.js";
import { ciHosts, fail, modes } from "./types.js";
import type {
  CiHost,
  InstallAssetRecord,
  InstallCandidate,
  InstallerConfig,
  Mode
} from "./types.js";

/** Relative path of the managed install record inside the target repository. */
export const installRecordPath = ".harness/install-record.json";

/** Managed install record persisted from actual installer outcomes. */
export type InstallRecord = Readonly<{
  assets: readonly InstallAssetRecord[];
  canonicalCheckCommand: string;
  ciHost: CiHost;
  complete: boolean;
  fixCommand: string;
  mode: Mode;
  prePushCommand: string;
  schemaVersion: 2;
}>;

type CandidateState = Readonly<{
  exists: boolean;
  linkTarget?: string;
  managedDigest?: string;
  targetDigest?: string;
}>;

type LegacyInstallRecord = Readonly<{
  canonicalCheckCommand: string;
  ciHost: CiHost;
  fixCommand: string;
  installedAssets: readonly string[];
  mode: Mode;
  prePushCommand: string;
  schemaVersion: 1;
}>;

/** Check whether an unknown value is an object record. */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Return a stable digest for installed content. */
export const digestContent = (content: string): string =>
  createHash("sha256").update(content).digest("hex");

/** Extract the normalized managed block from one root contract. */
const managedBlock = (content: string): null | string => {
  const begin = content.indexOf(managedBeginMarker);
  const end = content.indexOf(managedEndMarker, begin);
  if (begin === -1 || end === -1) {
    return null;
  }
  return content.slice(begin, end + managedEndMarker.length);
};

/** Return the source digest expected for one candidate. */
export const sourceDigestForCandidate = async (
  candidate: InstallCandidate
): Promise<string> => {
  if (candidate.kind === "symlink") {
    return digestContent(candidate.symlinkTarget ?? "");
  }
  if (candidate.kind === "gitkeep") {
    return digestContent("");
  }
  const source = await readInstallAsset(requiredSrc(candidate));
  if (candidate.kind !== "root-contract") {
    return digestContent(source);
  }
  const block = managedBlock(renderManagedBlock(source));
  if (block === null) {
    return fail(`cannot render managed block for ${candidate.dst}`);
  }
  return digestContent(block);
};

/** Return the on-disk state for one install candidate. */
const readCandidateState = async (
  candidate: InstallCandidate
): Promise<CandidateState> => {
  const target =
    candidate.kind === "root-contract"
      ? requiredRealTarget(candidate)
      : candidate.dst;
  if (await isSymlink(target)) {
    const linkTarget = await readlink(target);
    return {
      exists: true,
      linkTarget,
      targetDigest: digestContent(linkTarget)
    };
  }
  if (!(await pathExists(target))) {
    return { exists: false };
  }
  const stat = await lstat(target);
  if (!stat.isFile()) {
    return { exists: true };
  }
  const content = await readUtf8(target);
  const block = managedBlock(content);
  return {
    exists: true,
    ...(block === null ? {} : { managedDigest: digestContent(block) }),
    targetDigest: digestContent(content)
  };
};

/** Capture candidate state before an install operation. */
export const captureCandidateStates = async (
  candidates: readonly InstallCandidate[]
): Promise<ReadonlyMap<string, CandidateState>> =>
  new Map(
    await Promise.all(
      candidates.map(
        async (candidate) =>
          [candidate.dst, await readCandidateState(candidate)] as const
      )
    )
  );

/** Check whether two captured candidate states differ. */
const stateChanged = (before: CandidateState, after: CandidateState): boolean =>
  before.exists !== after.exists ||
  before.linkTarget !== after.linkTarget ||
  before.managedDigest !== after.managedDigest ||
  before.targetDigest !== after.targetDigest;

/** Check whether one candidate matches its current plugin source. */
const stateMatchesSource = (
  candidate: InstallCandidate,
  state: CandidateState,
  sourceDigest: string
): boolean => {
  if (candidate.kind === "root-contract") {
    return state.managedDigest === sourceDigest;
  }
  return state.targetDigest === sourceDigest;
};

/** Determine durable ownership from one actual install outcome. */
const ownershipForResult = (
  candidate: InstallCandidate,
  outcome: InstallAssetRecord["outcome"],
  before: CandidateState,
  after: CandidateState,
  previous: InstallAssetRecord | undefined
): InstallAssetRecord["ownership"] => {
  if (candidate.kind === "root-contract") {
    return "shared";
  }
  if (candidate.kind === "seed") {
    return "target";
  }
  if (outcome === "conflict") {
    return "target";
  }
  if (outcome === "created" || outcome === "updated") {
    return "harness";
  }
  if (
    previous?.ownership === "harness" &&
    previous.targetDigest !== undefined &&
    previous.targetDigest === before.targetDigest &&
    before.targetDigest === after.targetDigest
  ) {
    return "harness";
  }
  return "target";
};

/** Build records from actual before-and-after installer states. */
export const buildInstallResults = (
  candidates: readonly InstallCandidate[],
  beforeStates: ReadonlyMap<string, CandidateState>,
  previousAssets: ReadonlyMap<string, InstallAssetRecord>
): Promise<readonly InstallAssetRecord[]> =>
  Promise.all(
    candidates.map(async (candidate): Promise<InstallAssetRecord> => {
      const before = beforeStates.get(candidate.dst) ?? { exists: false };
      const after = await readCandidateState(candidate);
      const sourceDigest = await sourceDigestForCandidate(candidate);
      const matchesSource = stateMatchesSource(candidate, after, sourceDigest);
      let outcome: InstallAssetRecord["outcome"];
      if (!before.exists && after.exists && matchesSource) {
        outcome = "created";
      } else if (stateChanged(before, after) && matchesSource) {
        outcome = "updated";
      } else if (matchesSource) {
        outcome = "kept";
      } else {
        outcome = "conflict";
      }
      const previous = previousAssets.get(candidate.dst);
      return {
        kind: candidate.kind,
        ...(after.linkTarget === undefined
          ? {}
          : { linkTarget: after.linkTarget }),
        ...(after.managedDigest === undefined
          ? {}
          : { managedDigest: after.managedDigest }),
        outcome,
        ownership: ownershipForResult(
          candidate,
          outcome,
          before,
          after,
          previous
        ),
        path: candidate.dst,
        sourceDigest,
        ...(after.targetDigest === undefined
          ? {}
          : { targetDigest: after.targetDigest })
      };
    })
  );

/** Parse one persisted asset outcome. */
const parseAsset = (value: unknown): InstallAssetRecord => {
  if (!isRecord(value)) {
    return fail(`${installRecordPath}: each asset must be an object`);
  }
  const {
    kind,
    linkTarget,
    managedDigest,
    outcome,
    ownership,
    path,
    sourceDigest,
    targetDigest
  } = value;
  const kinds = [
    "file",
    "gitkeep",
    "root-contract",
    "seed",
    "stack-file",
    "symlink"
  ];
  if (
    typeof path !== "string" ||
    !kinds.includes(String(kind)) ||
    !["conflict", "created", "kept", "updated"].includes(String(outcome)) ||
    !["harness", "shared", "target"].includes(String(ownership)) ||
    (linkTarget !== undefined && typeof linkTarget !== "string") ||
    (managedDigest !== undefined && typeof managedDigest !== "string") ||
    (sourceDigest !== undefined && typeof sourceDigest !== "string") ||
    (targetDigest !== undefined && typeof targetDigest !== "string")
  ) {
    return fail(`${installRecordPath}: invalid asset outcome`);
  }
  return value as InstallAssetRecord;
};

/** Parse one legacy schema-v1 record conservatively as target-owned assets. */
const parseLegacyRecord = (value: Record<string, unknown>): InstallRecord => {
  const legacy = value as Partial<LegacyInstallRecord>;
  if (
    !modes.includes(legacy.mode as Mode) ||
    !ciHosts.includes(legacy.ciHost as CiHost) ||
    !Array.isArray(legacy.installedAssets) ||
    !legacy.installedAssets.every((asset) => typeof asset === "string") ||
    typeof legacy.canonicalCheckCommand !== "string" ||
    typeof legacy.fixCommand !== "string" ||
    typeof legacy.prePushCommand !== "string"
  ) {
    return fail(`${installRecordPath}: invalid schema-v1 record`);
  }
  const ciHost = legacy.ciHost as CiHost;
  const mode = legacy.mode as Mode;
  return {
    assets: legacy.installedAssets.map((asset) => ({
      kind: "file",
      outcome: "kept",
      ownership: "target",
      path: asset
    })),
    canonicalCheckCommand: legacy.canonicalCheckCommand,
    ciHost,
    complete: true,
    fixCommand: legacy.fixCommand,
    mode,
    prePushCommand: legacy.prePushCommand,
    schemaVersion: 2
  };
};

/** Parse and validate one schema-v2 install record. */
const parseInstallRecord = (value: unknown): InstallRecord => {
  if (!isRecord(value)) {
    return fail(`${installRecordPath}: top-level value must be an object`);
  }
  if (value["schemaVersion"] === 1) {
    return parseLegacyRecord(value);
  }
  const {
    assets,
    canonicalCheckCommand,
    ciHost,
    complete,
    fixCommand,
    mode,
    prePushCommand,
    schemaVersion
  } = value;
  if (
    schemaVersion !== 2 ||
    !modes.includes(mode as Mode) ||
    !ciHosts.includes(ciHost as CiHost) ||
    typeof complete !== "boolean" ||
    !Array.isArray(assets) ||
    typeof canonicalCheckCommand !== "string" ||
    typeof fixCommand !== "string" ||
    typeof prePushCommand !== "string"
  ) {
    return fail(`${installRecordPath}: invalid schema-v2 record`);
  }
  const parsedAssets = assets.map(parseAsset);
  if (
    new Set(parsedAssets.map((asset) => asset.path)).size !==
    parsedAssets.length
  ) {
    return fail(`${installRecordPath}: duplicate asset path`);
  }
  return {
    assets: parsedAssets,
    canonicalCheckCommand,
    ciHost: ciHost as CiHost,
    complete,
    fixCommand,
    mode: mode as Mode,
    prePushCommand,
    schemaVersion: 2
  };
};

/** Read and validate the existing install record when present. */
export const readInstallRecord = async (): Promise<InstallRecord | null> => {
  if (!(await pathExists(installRecordPath))) {
    return null;
  }
  if (await isSymlink(installRecordPath)) {
    return fail(`${installRecordPath}: refusing symlink install record`);
  }
  await ensureSafeFileDestination(installRecordPath);
  let value: unknown;
  try {
    value = JSON.parse(await readUtf8(installRecordPath));
  } catch (error) {
    return fail(
      `${installRecordPath}: invalid JSON: ${
        error instanceof Error ? error.message : String(error)
      }`
    );
  }
  return parseInstallRecord(value);
};

/** Require a targeted refresh to match the existing record selection. */
export const requireCompatibleRecord = (
  record: InstallRecord,
  config: InstallerConfig
): void => {
  if (record.mode !== config.mode || record.ciHost !== config.ciHost) {
    fail(
      `${installRecordPath}: recorded mode/CI host is ${record.mode}/${record.ciHost}; --only requires the same selection`
    );
  }
};

/** Return prior assets that are safe to consume for the selected refresh. */
export const previousAssetsForConfig = async (
  config: InstallerConfig,
  strict: boolean
): Promise<ReadonlyMap<string, InstallAssetRecord>> => {
  const record = await readInstallRecord();
  if (record === null) {
    return new Map();
  }
  if (record.mode !== config.mode || record.ciHost !== config.ciHost) {
    if (strict) {
      requireCompatibleRecord(record, config);
    }
    return new Map();
  }
  return new Map(record.assets.map((asset) => [asset.path, asset]));
};

/** Check whether prior ownership permits an automatic source refresh. */
export const canRefreshOwnedAsset = (
  previous: InstallAssetRecord | undefined,
  currentDigest: string,
  sourceDigest: string
): boolean =>
  previous?.ownership === "harness" &&
  previous.outcome !== "conflict" &&
  previous.targetDigest === currentDigest &&
  currentDigest !== sourceDigest;

/** Build the durable record for current actual outcomes. */
const recordForResults = (
  config: InstallerConfig,
  assets: readonly InstallAssetRecord[],
  complete: boolean
): InstallRecord => ({
  assets: [...assets].toSorted((left, right) =>
    left.path.localeCompare(right.path)
  ),
  canonicalCheckCommand: validationCommandForMode(config.mode),
  ciHost: config.ciHost,
  complete,
  fixCommand: fixCommandForMode(config.mode),
  mode: config.mode,
  prePushCommand: prePushCommandForMode(config.mode),
  schemaVersion: 2
});

/** Write actual installer results atomically and merge targeted outcomes. */
export const writeInstallRecord = async (
  config: InstallerConfig,
  results: readonly InstallAssetRecord[],
  fullInstall: boolean
): Promise<void> => {
  const existing = fullInstall ? null : await readInstallRecord();
  if (existing !== null) {
    requireCompatibleRecord(existing, config);
  }
  const merged = new Map(
    (existing?.assets ?? []).map((asset) => [asset.path, asset])
  );
  for (const result of results) {
    merged.set(result.path, result);
  }
  const record = recordForResults(
    config,
    fullInstall ? results : [...merged.values()],
    fullInstall || existing?.complete === true
  );
  await ensureSafeFileDestination(installRecordPath);
  const temporary = await temporaryDestination(
    installRecordPath,
    "write_record"
  );
  await writeUtf8(temporary, `${JSON.stringify(record, null, 2)}\n`);
  await replaceFile(temporary, installRecordPath);
  console.log(`write record: ${installRecordPath}`);
};
