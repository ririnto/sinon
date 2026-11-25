import { stat, watch } from "node:fs/promises";

import { expect, test } from "vitest";

import {
  createCommandFixture,
  waitForFixtureTasks
} from "./fixture-command.ts";

test.skipIf(process.platform === "win32")(
  "fixture teardown after a failed assertion drains an aborted command",
  async ({ signal }) => {
    const fixture = await createCommandFixture("sinon-command-abort-");
    const controller = new AbortController();
    const ready = (async () => {
      for await (const { filename } of watch(fixture.directory, { signal })) {
        if (filename === "ready") {
          break;
        }
      }
    })();
    const command = fixture.run(
      process.execPath,
      [
        "-e",
        `
        const { readFileSync, renameSync, writeFileSync, writeSync } = require("node:fs");
        process.on("SIGTERM", () => {
          writeSync(1, readFileSync("ready"));
          writeSync(2, "stderr drained");
          process.exit(0);
        });
        writeFileSync("pending", "stdout drained");
        renameSync("pending", "ready");
        setInterval(() => {}, 1000);
        `
      ],
      { signal: controller.signal }
    );
    await expect(async () => {
      await using ownedFixture = fixture;
      try {
        await ready;
        const directory = await stat(ownedFixture.directory);
        expect(directory.isFile()).toBe(true);
      } finally {
        controller.abort();
      }
    }).rejects.toThrow("expected false to be true");
    const result = await command;
    expect(result.error?.name).toBe("AbortError");
    expect(result.stdout.toString()).toBe("stdout drained");
    expect(result.stderr.toString()).toBe("stderr drained");
    await expect(stat(fixture.directory)).rejects.toMatchObject({
      code: "ENOENT"
    });
  }
);

test("a fixture setup failure waits for sibling work and removes the directory", async () => {
  const fixture = await createCommandFixture("sinon-command-setup-");
  const failure = new Error("fixture setup failed");
  let completed = false;
  await expect(async () => {
    await using ownedFixture = fixture;
    const finishSetup = async (): Promise<void> => {
      const write = await ownedFixture.run(process.execPath, [
        "-e",
        'require("node:fs").writeFileSync("prepared", "sibling ready")'
      ]);
      expect(write.status).toBe(0);
      const read = await ownedFixture.run(process.execPath, [
        "-e",
        'process.stdout.write(require("node:fs").readFileSync("prepared"))'
      ]);
      expect(read.stdout.toString()).toBe("sibling ready");
      completed = true;
    };
    await waitForFixtureTasks([Promise.reject(failure), finishSetup()]);
  }).rejects.toBe(failure);
  expect(completed).toBe(true);
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});
