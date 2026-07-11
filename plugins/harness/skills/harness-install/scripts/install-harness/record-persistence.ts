import { prepareAtomicWrite } from "./atomic-write.js";
import {
  fixCommandForMode,
  prePushCommandForMode,
  validationCommandForMode
} from "./commands.js";
import { ensureSafeFileDestination } from "./paths.js";
import { buildPlan } from "./planning.js";
import { requireCompatibleRecord } from "./record-compatibility.js";
import { digestContent } from "./record-content.js";
import { installRecordPath, readInstallRecord } from "./record-schema.js";
import type { InstallRecord } from "./record-schema.js";
import { fail } from "./types.js";
import type { InstallAssetRecord, InstallerConfig } from "./types.js";

export { installRecordPath } from "./record-schema.js";

type PrepareAtomicWrite = (
  target: string,
  label: string
) => ReturnType<typeof prepareAtomicWrite>;

/** Build the durable record for current actual outcomes. */
const recordForResults = (
  config: InstallerConfig,
  assets: readonly InstallAssetRecord[],
  complete: boolean,
  expectedAssets: readonly string[]
): InstallRecord => ({
  assets: [...assets].toSorted((left, right) =>
    left.path.localeCompare(right.path)
  ),
  canonicalCheckCommand: validationCommandForMode(config.mode),
  ciHost: config.ciHost,
  complete,
  expectedAssets,
  expectedPlanDigest: digestContent(JSON.stringify(expectedAssets)),
  fixCommand: fixCommandForMode(config.mode),
  mode: config.mode,
  prePushCommand: prePushCommandForMode(config.mode),
  schemaVersion: 2
});

/** Reject a targeted write before mutation when its recorded plan is stale. */
export const requireCompatibleInstallPlan = async (
  config: InstallerConfig
): Promise<void> => {
  const existing = await readInstallRecord();
  if (existing === null) {
    return;
  }
  requireCompatibleRecord(existing, config);
  const plan = await buildPlan(config);
  const expectedAssets = plan.map((candidate) => candidate.dst).toSorted();
  if (
    JSON.stringify(existing.expectedAssets) !== JSON.stringify(expectedAssets)
  ) {
    fail(
      `${installRecordPath}: selected install plan changed; run a full install before a targeted update`
    );
  }
};

/** Persist one fully constructed record through the atomic-write boundary. */
export const persistInstallRecord = async (
  record: InstallRecord,
  prepareWrite: PrepareAtomicWrite = prepareAtomicWrite
): Promise<void> => {
  await ensureSafeFileDestination(installRecordPath);
  const write = await prepareWrite(installRecordPath, "write_record");
  const operation = (async (): Promise<void> => {
    await write.write(`${JSON.stringify(record, null, 2)}\n`);
    await write.commit();
  })();
  let operationFailed = false;
  try {
    await operation;
  } catch {
    operationFailed = true;
  }
  if (operationFailed) {
    try {
      await write.discard();
    } catch {
      return operation;
    }
    return operation;
  }
  await write.discard();
  console.log(`write record: ${installRecordPath}`);
};

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
  const plan = await buildPlan(config);
  const expectedAssets = plan.map((candidate) => candidate.dst).toSorted();
  const resultAssets = results.map((asset) => asset.path).toSorted();
  if (
    fullInstall &&
    JSON.stringify(resultAssets) !== JSON.stringify(expectedAssets)
  ) {
    return fail(
      `${installRecordPath}: full install results do not match the expected plan`
    );
  }
  if (
    existing !== null &&
    JSON.stringify(existing.expectedAssets) !== JSON.stringify(expectedAssets)
  ) {
    return fail(
      `${installRecordPath}: selected install plan changed; run a full install before a targeted update`
    );
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
    fullInstall || existing?.complete === true,
    expectedAssets
  );
  await persistInstallRecord(record);
};
