import path from "node:path";

export const modes = ["gradle", "maven", "uv", "bun", "shell"] as const;
export const ciHosts = ["github", "gitlab", "both", "none"] as const;
export const actions = ["adopt", "install", "preview", "show", "only"] as const;

export type Mode = (typeof modes)[number];
export type CiHost = (typeof ciHosts)[number];
export type Action = (typeof actions)[number];
export type CandidateKind =
  | "file"
  | "stack-file"
  | "seed"
  | "root-contract"
  | "gitkeep"
  | "symlink";
export type InstallOutcome = "conflict" | "created" | "kept" | "updated";
export type AssetOwnership = "harness" | "shared" | "target";

export type InstallerConfig = Readonly<{
  activateHooks: boolean;
  action: Action;
  ciHost: CiHost;
  force: boolean;
  mode: Mode;
  selectedPath: null | string;
  targetRoot: string;
}>;

export type InstallCandidate = Readonly<{
  dst: string;
  kind: CandidateKind;
  realTarget?: string;
  seed?: boolean;
  src?: string;
  symlinkTarget?: string;
}>;

export type InstallAssetRecord = Readonly<{
  kind: CandidateKind;
  linkTarget?: string;
  managedDigest?: string;
  outcome: InstallOutcome;
  ownership: AssetOwnership;
  path: string;
  sourceDigest?: string;
  targetDigest?: string;
}>;

export type InstallOperationResult = Readonly<{
  outcome: InstallOutcome;
  ownership: AssetOwnership;
}>;

export const scriptDir = path.join(import.meta.dirname, "..");
export const skillDir = path.join(scriptDir, "..");
export const templateDir = path.join(skillDir, "assets");

export class HarnessError extends Error {
  readonly exitCode: number;

  constructor(message: string, exitCode = 1) {
    super(message);
    this.name = "HarnessError";
    this.exitCode = exitCode;
  }
}

export const fail = (message: string, exitCode = 1): never => {
  throw new HarnessError(`[error] ${message}`, exitCode);
};
