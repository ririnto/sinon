import { installRecordPath, readInstallRecord } from "./record-schema.js";
import type { InstallRecord } from "./record-schema.js";
import type { InstallAssetRecord, InstallerConfig } from "./types.js";
import { fail } from "./types.js";

/** Require a targeted refresh to match the existing record selection. */
export const requireCompatibleRecord = (
  record: InstallRecord,
  config: InstallerConfig
): void => {
  if (record.mode !== config.mode || record.ciHost !== config.ciHost) {
    fail(
      `${installRecordPath}: recorded mode/CI host is ${record.mode}/${record.ciHost}; targeted actions require the same selection`
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
