#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { createHash } from "node:crypto";
import { existsSync, lstatSync, readFileSync, readlinkSync } from "node:fs";
import path from "node:path";

type Mode = "bun" | "gradle" | "maven" | "shell" | "uv";

type AssetState = Readonly<{
  linkTarget?: string;
  managedDigest?: string;
  targetDigest?: string;
}>;

type ParsedAsset = Readonly<{
  kind: string;
  linkTarget?: string;
  managedDigest?: string;
  outcome: string;
  ownership: string;
  path: string;
  sourceDigest?: string;
  targetDigest?: string;
}>;

type ParsedRecord = Readonly<{
  assets: readonly unknown[];
  canonicalCheckCommand: string;
  complete: boolean;
  fixCommand: string;
  mode: Mode;
  prePushCommand: string;
}>;

const installRecordPath = ".harness/install-record.json";
const managedBeginMarker = "<!-- harness:managed begin -->";
const managedEndMarker = "<!-- harness:managed end -->";
const modes = new Set<unknown>(["bun", "gradle", "maven", "shell", "uv"]);
const ciHosts = new Set<unknown>(["both", "github", "gitlab", "none"]);
const kinds = new Set<unknown>([
  "file",
  "gitkeep",
  "root-contract",
  "seed",
  "stack-file",
  "symlink"
]);
const outcomes = new Set<unknown>(["conflict", "created", "kept", "updated"]);
const ownerships = new Set<unknown>(["harness", "shared", "target"]);

/** Check whether an unknown value is an object record. */
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Check whether an optional field is a string when present. */
const isOptionalString = (value: unknown): boolean =>
  value === undefined || typeof value === "string";

/** Return a stable SHA-256 digest. */
const digestContent = (content: string): string =>
  createHash("sha256").update(content).digest("hex");

/** Check whether a path stays inside the target root. */
const isInsidePath = (root: string, target: string): boolean => {
  const relative = path.relative(root, target);
  return (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  );
};

/** Resolve a record path without allowing parent symlink traversal. */
const safeTargetPath = (root: string, relativePath: string): string => {
  if (
    relativePath === "" ||
    relativePath.startsWith("/") ||
    relativePath.includes("\\") ||
    relativePath
      .split("/")
      .some((part) => part === "" || part === "." || part === "..")
  ) {
    throw new Error(`unsafe recorded path: ${relativePath}`);
  }
  const absolutePath = path.resolve(root, relativePath);
  if (!isInsidePath(root, absolutePath)) {
    throw new Error(`recorded path escapes target root: ${relativePath}`);
  }
  let current = root;
  const parts = relativePath.split("/");
  for (const part of parts.slice(0, -1)) {
    current = path.join(current, part);
    const stat = lstatSync(current, { throwIfNoEntry: false });
    if (stat?.isSymbolicLink()) {
      throw new Error(`recorded path crosses symlink parent: ${relativePath}`);
    }
  }
  return absolutePath;
};

/** Extract the managed block digest from one root contract. */
const managedDigest = (content: string): string | undefined => {
  const begin = content.indexOf(managedBeginMarker);
  const end = content.indexOf(managedEndMarker, begin);
  if (begin === -1 || end === -1) {
    return undefined;
  }
  return digestContent(content.slice(begin, end + managedEndMarker.length));
};

/** Read the target state for one recorded asset. */
const readAssetState = (root: string, relativePath: string): AssetState => {
  const absolutePath = safeTargetPath(root, relativePath);
  const stat = lstatSync(absolutePath, { throwIfNoEntry: false });
  if (stat?.isSymbolicLink()) {
    const linkTarget = readlinkSync(absolutePath);
    return { linkTarget, targetDigest: digestContent(linkTarget) };
  }
  if (stat?.isFile() !== true) {
    return {};
  }
  const content = readFileSync(absolutePath, "utf-8");
  return {
    ...(managedDigest(content) === undefined
      ? {}
      : { managedDigest: managedDigest(content) }),
    targetDigest: digestContent(content)
  };
};

/** Build Maven check and fix command prefixes. */
const mavenSpotlessFilesFragments = (): readonly string[] => [
  'root=$(pwd -P); if git ls-files -- "*.java" | grep -q \'^"\'; then',
  'unsafe_file=$(git ls-files -- "*.java" | grep \'^"\');',
  'echo "error: escaped Java path for spotlessFiles: $unsafe_file" >&2;',
  "exit 1; fi;",
  "if git ls-files -- \"*.java\" | grep -q ','; then",
  "comma_file=$(git ls-files -- \"*.java\" | grep ',');",
  'echo "error: comma Java path for spotlessFiles: $comma_file" >&2;',
  "exit 1; fi;",
  'files=$(git ls-files -- "*.java" | while IFS= read -r file; do',
  'printf \'%s/%s\\n\' "$root" "$file" |',
  "sed 's/[][\\\\.^$*+?{}()|]/\\\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -);"
];

/** Return the canonical check command for one mode. */
const checkCommand = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew ktlintCheck";
    }
    case "maven": {
      return [
        ...mavenSpotlessFilesFragments(),
        'if [ -z "$files" ]; then ./mvnw validate;',
        'echo "spotless: no tracked Java files to check";',
        'else ./mvnw validate -DspotlessFiles="$files"; fi'
      ].join(" ");
    }
    case "uv": {
      return "uv run scripts/check.py";
    }
    case "bun": {
      return "bun run check";
    }
    case "shell": {
      return "sh scripts/check.sh";
    }
    default: {
      throw new Error(`unsupported mode: ${mode}`);
    }
  }
};

/** Return the canonical fix command for one mode. */
const fixCommand = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew ktlintFormat";
    }
    case "maven": {
      return [
        ...mavenSpotlessFilesFragments(),
        'if [ -z "$files" ]; then ./mvnw exec:exec@format-markdown spotless:apply;',
        'else ./mvnw exec:exec@format-markdown spotless:apply -DspotlessFiles="$files"; fi'
      ].join(" ");
    }
    case "uv": {
      return "uv run scripts/fix.py";
    }
    case "bun": {
      return "bun run fix";
    }
    case "shell": {
      return "sh scripts/fix.sh";
    }
    default: {
      throw new Error(`unsupported mode: ${mode}`);
    }
  }
};

/** Return the canonical pre-push command for one mode. */
const prePushCommand = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew check";
    }
    case "maven": {
      return "./mvnw verify";
    }
    case "uv": {
      return "uv run scripts/check.py";
    }
    case "bun": {
      return "bun run check && bun test";
    }
    case "shell": {
      return "sh scripts/check.sh";
    }
    default: {
      throw new Error(`unsupported mode: ${mode}`);
    }
  }
};

/** Parse one asset record without reading its target path. */
const parseAsset = (value: unknown): ParsedAsset | string => {
  if (!isRecord(value)) {
    return `${installRecordPath}: each asset must be an object`;
  }
  const {
    kind,
    linkTarget,
    managedDigest: recordedManagedDigest,
    outcome,
    ownership,
    path: assetPath,
    sourceDigest,
    targetDigest
  } = value;
  const valid = [
    typeof assetPath === "string",
    kinds.has(kind),
    outcomes.has(outcome),
    ownerships.has(ownership),
    isOptionalString(linkTarget),
    isOptionalString(recordedManagedDigest),
    isOptionalString(sourceDigest),
    isOptionalString(targetDigest)
  ].every(Boolean);
  if (!valid) {
    return `${installRecordPath}: invalid asset outcome`;
  }
  return {
    kind: kind as string,
    ...(linkTarget === undefined ? {} : { linkTarget: linkTarget as string }),
    ...(recordedManagedDigest === undefined
      ? {}
      : { managedDigest: recordedManagedDigest as string }),
    outcome: outcome as string,
    ownership: ownership as string,
    path: assetPath as string,
    ...(sourceDigest === undefined
      ? {}
      : { sourceDigest: sourceDigest as string }),
    ...(targetDigest === undefined
      ? {}
      : { targetDigest: targetDigest as string })
  };
};

/** Validate parsed asset semantics against target state. */
const validateParsedAsset = (
  asset: ParsedAsset,
  state: AssetState
): readonly string[] => {
  const errors: string[] = [];
  if (asset.outcome === "conflict") {
    errors.push(`${asset.path}: unresolved install conflict`);
  }
  if (asset.kind === "root-contract" && asset.ownership !== "shared") {
    errors.push(`${asset.path}: root contract must use shared ownership`);
  }
  if (asset.kind === "seed" && asset.ownership !== "target") {
    errors.push(`${asset.path}: seed must use target ownership`);
  }
  if (
    asset.ownership === "harness" &&
    asset.targetDigest !== state.targetDigest
  ) {
    errors.push(`${asset.path}: installed target drift`);
  }
  if (
    asset.ownership === "shared" &&
    asset.managedDigest !== state.managedDigest
  ) {
    errors.push(`${asset.path}: managed block drift`);
  }
  if (asset.kind === "symlink" && asset.linkTarget !== state.linkTarget) {
    errors.push(`${asset.path}: symlink target drift`);
  }
  if (
    (asset.ownership === "harness" || asset.ownership === "shared") &&
    asset.sourceDigest === undefined
  ) {
    errors.push(
      `${asset.path}: source digest is required for ${asset.ownership} ownership`
    );
  }
  if (
    asset.ownership === "harness" &&
    asset.sourceDigest !== asset.targetDigest
  ) {
    errors.push(`${asset.path}: recorded source and target digests differ`);
  }
  if (
    asset.ownership === "shared" &&
    asset.sourceDigest !== asset.managedDigest
  ) {
    errors.push(`${asset.path}: recorded source and managed digests differ`);
  }
  return errors;
};

/** Validate one parsed asset entry against its target state. */
const validateAsset = (root: string, value: unknown): readonly string[] => {
  const asset = parseAsset(value);
  if (typeof asset === "string") {
    return [asset];
  }
  let state: AssetState;
  try {
    state = readAssetState(root, asset.path);
  } catch (error) {
    return [error instanceof Error ? error.message : String(error)];
  }
  return validateParsedAsset(asset, state);
};

/** Parse the top-level schema-v2 record. */
const parseRecord = (value: unknown): ParsedRecord | string => {
  if (!isRecord(value)) {
    return `${installRecordPath}: top-level value must be an object`;
  }
  const {
    assets,
    canonicalCheckCommand,
    ciHost,
    complete,
    fixCommand: recordedFixCommand,
    mode,
    prePushCommand: recordedPrePushCommand,
    schemaVersion
  } = value;
  const valid = [
    schemaVersion === 2,
    modes.has(mode),
    ciHosts.has(ciHost),
    typeof complete === "boolean",
    Array.isArray(assets),
    typeof canonicalCheckCommand === "string",
    typeof recordedFixCommand === "string",
    typeof recordedPrePushCommand === "string"
  ].every(Boolean);
  if (!valid) {
    return `${installRecordPath}: invalid schema-v2 record`;
  }
  return {
    assets: assets as readonly unknown[],
    canonicalCheckCommand: canonicalCheckCommand as string,
    complete: complete as boolean,
    fixCommand: recordedFixCommand as string,
    mode: mode as Mode,
    prePushCommand: recordedPrePushCommand as string
  };
};

/** Validate canonical command fields for one parsed record. */
const validateCommands = (record: ParsedRecord): readonly string[] => {
  const errors: string[] = [];
  if (record.canonicalCheckCommand !== checkCommand(record.mode)) {
    errors.push(`${installRecordPath}: canonical check command mismatch`);
  }
  if (record.fixCommand !== fixCommand(record.mode)) {
    errors.push(`${installRecordPath}: fix command mismatch`);
  }
  if (record.prePushCommand !== prePushCommand(record.mode)) {
    errors.push(`${installRecordPath}: pre-push command mismatch`);
  }
  return errors;
};

/** Validate record structure, commands, outcomes, ownership, and target drift. */
const validateRecord = (root: string): readonly string[] => {
  const recordPath = safeTargetPath(root, installRecordPath);
  if (!existsSync(recordPath)) {
    return [`${installRecordPath}: missing install record`];
  }
  if (lstatSync(recordPath).isSymbolicLink()) {
    return [`${installRecordPath}: refusing symlink install record`];
  }
  let value: unknown;
  try {
    value = JSON.parse(readFileSync(recordPath, "utf-8"));
  } catch (error) {
    return [
      `${installRecordPath}: invalid JSON: ${
        error instanceof Error ? error.message : String(error)
      }`
    ];
  }
  const record = parseRecord(value);
  if (typeof record === "string") {
    return [record];
  }
  const errors: string[] = [];
  if (!record.complete) {
    errors.push(
      `${installRecordPath}: partial --only record is not a complete install`
    );
  }
  if (record.assets.length === 0) {
    errors.push(`${installRecordPath}: asset inventory is empty`);
  }
  errors.push(...validateCommands(record));
  const paths = record.assets
    .filter(isRecord)
    .map((asset) => asset["path"])
    .filter((assetPath): assetPath is string => typeof assetPath === "string");
  if (new Set(paths).size !== paths.length) {
    errors.push(`${installRecordPath}: duplicate asset path`);
  }
  for (const asset of record.assets) {
    errors.push(...validateAsset(root, asset));
  }
  return errors;
};

/** Validate the target install record and return a process exit code. */
const main = (): number => {
  const targetRoot = path.resolve(Bun.argv[2] ?? ".");
  if (!existsSync(targetRoot) || !lstatSync(targetRoot).isDirectory()) {
    console.error(
      `[ERROR] install-record: target root is not a directory: ${targetRoot}`
    );
    return 1;
  }
  let errors: readonly string[];
  try {
    errors = validateRecord(targetRoot);
  } catch (error) {
    errors = [error instanceof Error ? error.message : String(error)];
  }
  if (errors.length > 0) {
    for (const error of errors) {
      console.error(`[ERROR] install-record: ${error}`);
    }
    return 1;
  }
  console.log("[install record] OK");
  return 0;
};

process.exit(main());
