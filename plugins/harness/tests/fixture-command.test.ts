import { readFile, stat, watch } from "node:fs/promises";
import path from "node:path";
import { setImmediate } from "node:timers/promises";

import { expect, test, vi } from "vitest";

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
    }).rejects.toMatchObject({ name: "AssertionError" });
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

test("a configurator write failure leaves the fixture alive through a late write", async () => {
  const fixture = await createCommandFixture("sinon-configure-write-");
  const failedTarget = path.join(fixture.directory, "package.json");
  const lateTarget = path.join(fixture.directory, "pnpm-workspace.yaml");
  const failure = Object.assign(new Error("Cannot write the manifest"), {
    code: "EACCES"
  });
  const lateStarted = Promise.withResolvers<null>();
  const failureStarted = Promise.withResolvers<null>();
  const releaseLate = Promise.withResolvers<null>();
  const lateFinished = Promise.withResolvers<null>();
  let workspace: string | undefined;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      writeFile: vi.fn<typeof fs.writeFile>(async (file, data, options) => {
        if (file === failedTarget) {
          await lateStarted.promise;
          failureStarted.resolve(null);
          throw failure;
        }
        if (file === lateTarget) {
          lateStarted.resolve(null);
          try {
            await releaseLate.promise;
            await fs.writeFile(file, data, options);
            workspace = await readFile(file, "utf-8");
          } finally {
            lateFinished.resolve(null);
          }
        } else {
          await fs.writeFile(file, data, options);
        }
      })
    };
  });
  const { configureBaseline } = await import("../scripts/configure.js");
  let reported = false;
  const configuration = (async () => {
    await using ownedFixture = fixture;
    try {
      await configureBaseline(ownedFixture.directory, "node", "eligible");
    } finally {
      reported = true;
    }
  })();
  const rejection = expect(configuration).rejects.toBe(failure);
  try {
    await failureStarted.promise;
    await setImmediate();
    expect(reported).toBe(false);
  } finally {
    releaseLate.resolve(null);
    await lateFinished.promise;
    await rejection;
  }
  expect(workspace).toContain("catalog:");
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});
