import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { once } from "node:events";
import { chmod, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { Parser, ReadEntry } from "tar";

/**
 * Verifies a release archive before selecting its regular executable file.
 * Rejects missing, ambiguous, and mismatched release checksums.
 * Returns binary bytes without extracting archive paths to disk.
 */
export const verifiedExecutable = async (
  bytes: Uint8Array,
  checksumIndex: string,
  archiveName: string,
  executableMember: string
): Promise<Buffer> => {
  const checksums = checksumIndex
    .split(/\r?\n/u)
    .map((line) => line.trim().split(/\s+/u))
    .filter((fields) => fields[1]?.replace(/^\*/u, "") === archiveName);
  if (
    checksums.length !== 1 ||
    !/^[a-f0-9]{64}$/u.test(checksums[0]?.[0] ?? "") ||
    createHash("sha256").update(bytes).digest("hex") !== checksums[0]?.[0]
  ) {
    throw new Error(`Invalid official release checksum for ${archiveName}`);
  }
  const selectedEntries = new Set<ReadEntry>();
  const chunks: Buffer[] = [];
  const parser = new Parser({
    filter: (member, entry) => {
      if (entry instanceof ReadEntry && member === executableMember) {
        selectedEntries.add(entry);
        return (
          selectedEntries.size === 1 &&
          (entry.type === "File" || entry.type === "OldFile")
        );
      }
      return false;
    },
    onReadEntry: (entry) => {
      entry.on("data", (chunk: Buffer) => {
        chunks.push(chunk);
      });
    },
    strict: true
  });
  parser.on("ignoredEntry", (entry: ReadEntry) => {
    if (entry.path === executableMember) {
      selectedEntries.add(entry);
    }
  });
  const parsed = once(parser, "end");
  parser.end(Buffer.from(bytes));
  await parsed;
  if (selectedEntries.size === 0) {
    throw new Error(`Missing release executable ${executableMember}`);
  }
  if (selectedEntries.size !== 1) {
    throw new Error(`Ambiguous release executable ${executableMember}`);
  }
  const [executable] = selectedEntries;
  if (executable?.type !== "File" && executable?.type !== "OldFile") {
    throw new Error(`Nonregular release executable ${executableMember}`);
  }
  return Buffer.concat(chunks);
};

const releaseAsset = async (url: string): Promise<Response> => {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Release download failed: ${response.status} ${url}`);
  }
  return response;
};

const installPromtool = async (directory: string): Promise<string> => {
  const manifest = JSON.parse(
    await readFile(new URL("../../../package.json", import.meta.url), "utf-8")
  );
  const version: unknown = manifest.config?.promtoolVersion;
  if (typeof version !== "string" || !/^\d+\.\d+\.\d+$/u.test(version)) {
    throw new Error("package.json must configure a stable promtoolVersion");
  }
  const platform = new Map<string, string>([
    ["darwin", "darwin"],
    ["linux", "linux"],
    ["win32", "windows"]
  ]).get(process.platform);
  const architecture = new Map<string, string>([
    ["arm64", "arm64"],
    ["x64", "amd64"]
  ]).get(process.arch);
  if (platform === undefined || architecture === undefined) {
    throw new Error(
      `Unsupported Promtool host: ${process.platform}/${process.arch}`
    );
  }
  const archiveRoot = `prometheus-${version}.${platform}-${architecture}`;
  const archiveName = `${archiveRoot}.tar.gz`;
  const executableName =
    process.platform === "win32" ? "promtool.exe" : "promtool";
  const releaseUrl = `https://github.com/prometheus/prometheus/releases/download/v${version}`;
  const [archive, checksums] = await Promise.all([
    releaseAsset(`${releaseUrl}/${archiveName}`),
    releaseAsset(`${releaseUrl}/sha256sums.txt`)
  ]);
  const executablePath = path.join(directory, executableName);
  await writeFile(
    executablePath,
    await verifiedExecutable(
      new Uint8Array(await archive.arrayBuffer()),
      await checksums.text(),
      archiveName,
      `${archiveRoot}/${executableName}`
    )
  );
  await chmod(executablePath, 0o755);
  return executablePath;
};

const execute = async (
  binary: string,
  args: readonly string[]
): Promise<number> => {
  const [code, signal] = await once(
    spawn(binary, [...args], { stdio: "inherit" }),
    "close"
  );
  if (typeof code !== "number") {
    throw new TypeError(`Promtool terminated by signal ${signal}`);
  }
  return code;
};

const main = async (args: readonly string[]): Promise<number> => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-promtool-"));
  try {
    const binary = await installPromtool(directory);
    if (args.length !== 0) {
      return await execute(binary, args);
    }
    const ruleExit = await execute(binary, [
      "check",
      "rules",
      fileURLToPath(new URL("alerts/api-errors.rules.yaml", import.meta.url))
    ]);
    return ruleExit === 0
      ? await execute(binary, [
          "test",
          "rules",
          fileURLToPath(new URL("alerts/api-errors.test.yaml", import.meta.url))
        ])
      : ruleExit;
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

if (
  process.argv[1] !== undefined &&
  pathToFileURL(path.resolve(process.argv[1])).href === import.meta.url
) {
  process.exitCode = await main(process.argv.slice(2));
}
