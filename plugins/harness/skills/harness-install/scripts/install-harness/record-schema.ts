import { isSymlink, pathExists, readUtf8 } from "./files.js";
import { ensureSafeFileDestination } from "./paths.js";
import { digestContent } from "./record-content.js";
import { ciHosts, fail, modes } from "./types.js";
import type {
  AssetOwnership,
  CandidateKind,
  CiHost,
  InstallAssetRecord,
  InstallOutcome,
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
  expectedAssets: readonly string[];
  expectedPlanDigest: string;
  fixCommand: string;
  mode: Mode;
  prePushCommand: string;
  schemaVersion: 2;
}>;

/** Check whether an unknown value is an object record. */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const assetKinds = [
  "file",
  "gitkeep",
  "root-contract",
  "seed",
  "stack-file",
  "symlink"
] as const;

const assetOutcomes = ["conflict", "created", "kept", "updated"] as const;
const assetOwnerships = ["harness", "shared", "target"] as const;

const isStringArray = (value: unknown): value is readonly string[] =>
  Array.isArray(value) && value.every((entry) => typeof entry === "string");

const isMode = (value: unknown): value is Mode =>
  modes.some((mode) => mode === value);

const isCiHost = (value: unknown): value is CiHost =>
  ciHosts.some((ciHost) => ciHost === value);

const isAssetKind = (value: unknown): value is CandidateKind =>
  assetKinds.some((kind) => kind === value);

const isAssetOutcome = (value: unknown): value is InstallOutcome =>
  assetOutcomes.some((outcome) => outcome === value);

const isAssetOwnership = (value: unknown): value is AssetOwnership =>
  assetOwnerships.some((ownership) => ownership === value);

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
  if (
    typeof path !== "string" ||
    !isAssetKind(kind) ||
    !isAssetOutcome(outcome) ||
    !isAssetOwnership(ownership) ||
    (linkTarget !== undefined && typeof linkTarget !== "string") ||
    (managedDigest !== undefined && typeof managedDigest !== "string") ||
    (sourceDigest !== undefined && typeof sourceDigest !== "string") ||
    (targetDigest !== undefined && typeof targetDigest !== "string")
  ) {
    return fail(`${installRecordPath}: invalid asset outcome`);
  }
  return {
    kind,
    ...(linkTarget === undefined ? {} : { linkTarget }),
    ...(managedDigest === undefined ? {} : { managedDigest }),
    outcome,
    ownership,
    path,
    ...(sourceDigest === undefined ? {} : { sourceDigest }),
    ...(targetDigest === undefined ? {} : { targetDigest })
  };
};

/** Parse and validate one schema-v2 install record. */
const parseInstallRecord = (value: unknown): InstallRecord => {
  if (!isRecord(value)) {
    return fail(`${installRecordPath}: top-level value must be an object`);
  }
  const {
    assets,
    canonicalCheckCommand,
    ciHost,
    complete,
    expectedAssets,
    expectedPlanDigest,
    fixCommand,
    mode,
    prePushCommand,
    schemaVersion
  } = value;
  if (
    schemaVersion !== 2 ||
    !isMode(mode) ||
    !isCiHost(ciHost) ||
    typeof complete !== "boolean" ||
    !Array.isArray(assets) ||
    !isStringArray(expectedAssets) ||
    typeof expectedPlanDigest !== "string" ||
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
  const sortedExpectedAssets = [...expectedAssets].toSorted();
  if (
    new Set(sortedExpectedAssets).size !== sortedExpectedAssets.length ||
    expectedPlanDigest !== digestContent(JSON.stringify(sortedExpectedAssets))
  ) {
    return fail(`${installRecordPath}: invalid expected plan inventory`);
  }
  return {
    assets: parsedAssets,
    canonicalCheckCommand,
    ciHost,
    complete,
    expectedAssets: sortedExpectedAssets,
    expectedPlanDigest,
    fixCommand,
    mode,
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
