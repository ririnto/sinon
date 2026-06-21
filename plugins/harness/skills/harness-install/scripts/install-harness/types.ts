import path from "node:path";

export const modes = ["gradle", "maven", "uv", "bun", "shell"] as const;
export const ciHosts = ["github", "gitlab", "both", "none"] as const;
export const actions = ["install", "preview", "show", "only"] as const;

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

export type InstallerConfig = Readonly<{
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
  marker?: string;
  realTarget?: string;
  seed?: boolean;
  src?: string;
  symlinkTarget?: string;
}>;

export const agentsMarker = "# Repository Contract";
export const claudeMarker = "@AGENTS.md";
export const claudePointerContent = "# CLAUDE.md\n\n@AGENTS.md";

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

export const hasRootContractMarker = (
  dst: string,
  marker: string,
  content: string
): boolean => {
  if (dst === "CLAUDE.md") {
    return content.trim() === claudePointerContent;
  }
  return content.includes(marker);
};
