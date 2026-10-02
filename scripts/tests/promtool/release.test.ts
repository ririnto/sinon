import { createHash } from "node:crypto";
import { mkdir, mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { create } from "tar";
import { expect, test } from "vitest";

import { verifiedExecutable } from "./run.js";

const archiveBytes = async (
  files: Readonly<Record<string, string | Uint8Array>>,
  members: readonly string[] = Object.keys(files)
): Promise<Buffer> => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-release-test-"));
  try {
    await Promise.all(
      Object.entries(files).map(async ([member, content]) => {
        await mkdir(path.dirname(path.join(directory, member)), {
          recursive: true
        });
        await writeFile(path.join(directory, member), content);
      })
    );
    return Buffer.concat(
      await create({ cwd: directory, gzip: true }, [...members]).collect()
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

const checksumIndex = (bytes: Uint8Array): string =>
  `${createHash("sha256").update(bytes).digest("hex")}  release.tar.gz`;

test("rejects a release archive whose checksum does not match", async () => {
  await expect(
    verifiedExecutable(
      new Uint8Array([1]),
      `${"0".repeat(64)}  release.tar.gz`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("rejects a checksum for another archive", async () => {
  await expect(
    verifiedExecutable(
      new Uint8Array([1]),
      `${"0".repeat(64)}  other.tar.gz`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("rejects ambiguous checksum entries for the selected archive", async () => {
  const bytes = new Uint8Array([1]);
  const checksum = checksumIndex(bytes);
  await expect(
    verifiedExecutable(
      bytes,
      `${checksum}\n${checksum}`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("selects only the requested regular executable from a verified archive", async () => {
  const payload = Buffer.from([0, 255, 128, 1]);
  const bytes = await archiveBytes({
    "release/promtool": payload,
    "release/unrelated": "other content"
  });
  const executable = await verifiedExecutable(
    bytes,
    checksumIndex(bytes),
    "release.tar.gz",
    "release/promtool"
  );
  expect(executable).toEqual(payload);
});

test("rejects a verified archive that omits its executable", async () => {
  const bytes = await archiveBytes({ "release/other": "content" });
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Missing release executable");
});

test("rejects duplicate executable entries in a verified archive", async () => {
  const bytes = await archiveBytes({ "release/promtool": "executable" }, [
    "release/promtool",
    "release/promtool"
  ]);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Ambiguous release executable");
});

test("rejects a symbolic link selected as the release executable", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-release-test-"));
  try {
    await mkdir(path.join(directory, "release"));
    await writeFile(path.join(directory, "release/other"), "executable");
    await symlink("other", path.join(directory, "release/promtool"));
    const bytes = Buffer.concat(
      await create({ cwd: directory, gzip: true }, [
        "release/other",
        "release/promtool"
      ]).collect()
    );
    await expect(
      verifiedExecutable(
        bytes,
        checksumIndex(bytes),
        "release.tar.gz",
        "release/promtool"
      )
    ).rejects.toThrow("Nonregular release executable");
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});

test("rejects a verified archive with an invalid format", async () => {
  const bytes = new Uint8Array([1]);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Unrecognized archive format");
});

test("rejects a verified archive with truncated gzip data", async () => {
  const archive = await archiveBytes({ "release/promtool": "executable" });
  const bytes = archive.subarray(0, -8);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow();
});
