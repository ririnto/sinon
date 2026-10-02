import { expect, test } from "bun:test";

import { verifiedExecutable } from "./run.js";

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
  const checksum = `${new Bun.CryptoHasher("sha256").update(bytes).digest("hex")}  release.tar.gz`;
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
  const bytes = await new Bun.Archive({
    "release/promtool": "executable",
    "release/unrelated": "other content"
  }).bytes();
  const executable = await verifiedExecutable(
    bytes,
    `${new Bun.CryptoHasher("sha256").update(bytes).digest("hex")}  release.tar.gz`,
    "release.tar.gz",
    "release/promtool"
  );
  expect(await executable.text()).toBe("executable");
});

test("rejects a verified archive that omits its executable", async () => {
  const bytes = await new Bun.Archive({ "release/other": "content" }).bytes();
  await expect(
    verifiedExecutable(
      bytes,
      `${new Bun.CryptoHasher("sha256").update(bytes).digest("hex")}  release.tar.gz`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Missing release executable");
});
