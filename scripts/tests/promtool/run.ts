import { chmod, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Verifies a release archive before selecting its regular executable file.
 * Rejects missing, ambiguous, and mismatched release checksums.
 */
export const verifiedExecutable = async (
  bytes: Uint8Array,
  checksumIndex: string,
  archiveName: string,
  executableMember: string
): Promise<File> => {
  const checksums = checksumIndex
    .split(/\r?\n/u)
    .map((line) => line.trim().split(/\s+/u))
    .filter((fields) => fields[1]?.replace(/^\*/u, "") === archiveName);
  if (
    checksums.length !== 1 ||
    !/^[a-f0-9]{64}$/u.test(checksums[0]?.[0] ?? "") ||
    new Bun.CryptoHasher("sha256").update(bytes).digest("hex") !==
      checksums[0]?.[0]
  ) {
    throw new Error(`Invalid official release checksum for ${archiveName}`);
  }
  const files = await new Bun.Archive(bytes).files(executableMember);
  const executable = files.get(executableMember);
  if (executable === undefined) {
    throw new Error(`Missing release executable ${executableMember}`);
  }
  return executable;
};

const releaseAsset = async (url: string): Promise<Response> => {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Release download failed: ${response.status} ${url}`);
  }
  return response;
};

const installPromtool = async (directory: string): Promise<string> => {
  const manifest = await Bun.file(
    new URL("../../../package.json", import.meta.url)
  ).json();
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
  await Bun.write(
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
): Promise<number> =>
  await Bun.spawn([binary, ...args], {
    stderr: "inherit",
    stdin: "inherit",
    stdout: "inherit"
  }).exited;

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

if (import.meta.main) {
  process.exitCode = await main(Bun.argv.slice(2));
}
