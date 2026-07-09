import {
  fixCommandForMode,
  prePushCommandForMode,
  validationCommandForMode
} from "./commands.js";
import { replaceFile, temporaryDestination, writeUtf8 } from "./files.js";
import { ensureSafeFileDestination } from "./paths.js";
import { buildPlan } from "./planning.js";
import type { CiHost, InstallerConfig, Mode } from "./types.js";

/** Relative path of the managed install record inside the target repository. */
export const installRecordPath = ".harness/install-record.json";

/** Managed install record persisted after a full harness install or refresh. */
export type InstallRecord = Readonly<{
  schemaVersion: 1;
  mode: Mode;
  ciHost: CiHost;
  canonicalCheckCommand: string;
  fixCommand: string;
  prePushCommand: string;
  installedAssets: readonly string[];
}>;

/**
 * Collect the deterministic set of installed asset destination paths.
 *
 * @param config Installer config.
 * @returns Sorted unique destination paths derived from the install plan.
 */
const collectInstalledAssets = async (
  config: InstallerConfig
): Promise<readonly string[]> => {
  const candidates = await buildPlan(config);
  const seen = new Set<string>();
  for (const candidate of candidates) {
    seen.add(candidate.dst);
  }
  return [...seen].toSorted();
};

/**
 * Build the managed install record for the selected stack and CI host.
 *
 * @param config Installer config.
 * @returns Install record value object.
 */
export const buildInstallRecord = async (
  config: InstallerConfig
): Promise<InstallRecord> => ({
  canonicalCheckCommand: validationCommandForMode(config.mode),
  ciHost: config.ciHost,
  fixCommand: fixCommandForMode(config.mode),
  installedAssets: await collectInstalledAssets(config),
  mode: config.mode,
  prePushCommand: prePushCommandForMode(config.mode),
  schemaVersion: 1
});

/**
 * Write the managed install record atomically into the target repository.
 *
 * The record is the durable source of truth for validation and refresh; it is
 * fully owned by the installer and overwritten on every full install.
 *
 * @param config Installer config.
 */
export const writeInstallRecord = async (
  config: InstallerConfig
): Promise<void> => {
  const record = await buildInstallRecord(config);
  await ensureSafeFileDestination(installRecordPath);
  const tmp = await temporaryDestination(installRecordPath, "write_record");
  await writeUtf8(tmp, `${JSON.stringify(record, null, 2)}\n`);
  await replaceFile(tmp, installRecordPath);
  console.log(`write record: ${installRecordPath}`);
};
