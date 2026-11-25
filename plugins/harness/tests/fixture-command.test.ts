import { readFile, stat, watch } from "node:fs/promises";
import path from "node:path";
import { setImmediate, setTimeout as delay } from "node:timers/promises";

import { expect, test, vi } from "vitest";

import {
  createCommandFixture,
  settleWithin,
  waitForFixtureTasks
} from "./fixture-command.ts";

const failureResultBoundMs = 1000;
const overlapWindowMs = 400;

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

test("a bounded wait settles with its operation and rejects when the operation outlives the bound", async () => {
  const failure = new Error("operation failed");
  const never = Promise.withResolvers<never>().promise;
  await expect(
    settleWithin(failureResultBoundMs, Promise.resolve("settled"))
  ).resolves.toBe("settled");
  await expect(
    settleWithin(failureResultBoundMs, Promise.reject(failure))
  ).rejects.toBe(failure);
  await expect(settleWithin(10, never)).rejects.toBeInstanceOf(Error);
});

test("a configurator write failure leaves the fixture alive through a late write", async () => {
  const fixture = await createCommandFixture("sinon-configure-write-");
  const failedTarget = path.join(fixture.directory, "package.json");
  const lateTarget = path.join(fixture.directory, "pnpm-workspace.yaml");
  const failure = Object.assign(new Error("Cannot write the manifest"), {
    code: "EACCES"
  });
  const failureInvoked = Promise.withResolvers<null>();
  const siblingInvoked = Promise.withResolvers<null>();
  const releaseSibling = Promise.withResolvers<null>();
  let workspace: string | undefined;
  let failed = false;
  let failedAfterWrite = false;
  let siblingStarted = false;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      writeFile: vi.fn<typeof fs.writeFile>(async (file, data, options) => {
        if (file === failedTarget) {
          failed = true;
          failedAfterWrite = workspace !== undefined;
          failureInvoked.resolve(null);
          throw failure;
        }
        if (file === lateTarget) {
          siblingStarted = true;
          siblingInvoked.resolve(null);
          await releaseSibling.promise;
          await fs.writeFile(file, data, options);
          workspace = await readFile(file, "utf-8");
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
  const failureResult = (async () => {
    try {
      await settleWithin(failureResultBoundMs, configuration);
      return null;
    } catch (error) {
      return error;
    }
  })();
  const invoked = [failureInvoked.promise, siblingInvoked.promise];
  const overlapping = Promise.all(invoked);
  const windowClosed = (async () => {
    await Promise.race(invoked);
    await delay(overlapWindowMs, false, { ref: false });
  })();
  await Promise.race([overlapping, windowClosed, failureResult]);
  await setImmediate();
  const failedWhileHeld = failed;
  const siblingHeld = siblingStarted;
  const reportedWhileHeld = reported;
  releaseSibling.resolve(null);
  expect(await failureResult).toBe(failure);
  if (siblingHeld) {
    expect(reportedWhileHeld).toBe(false);
    expect(failedWhileHeld || failedAfterWrite).toBe(true);
  } else {
    expect(
      failedWhileHeld && reportedWhileHeld,
      "the failure stayed pending and no sibling write started within the overlap window"
    ).toBe(true);
  }
  expect(workspace?.includes("catalog:") ?? false).toBe(siblingStarted);
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});

test("a configurator write failure reports its failure within the bound when writes are serialized", async () => {
  const fixture = await createCommandFixture("sinon-configure-serial-");
  const { directory } = fixture;
  const failedTarget = path.join(fixture.directory, "package.json");
  const failure = Object.assign(new Error("Cannot write the manifest"), {
    code: "EACCES"
  });
  let turn: Promise<unknown> = Promise.resolve();
  let writesAfterRemoval = 0;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      writeFile: vi.fn<typeof fs.writeFile>(async (file, data, options) => {
        const writing = (async () => {
          await Promise.allSettled([turn]);
          const present = await stat(directory)
            .then(() => true)
            .catch(() => false);
          if (!present) {
            writesAfterRemoval += 1;
          }
          if (file === failedTarget) {
            throw failure;
          }
          await fs.writeFile(file, data, options);
        })();
        turn = writing;
        await writing;
      })
    };
  });
  vi.resetModules();
  const { configureBaseline } = await import("../scripts/configure.js");
  const configuration = (async () => {
    await using ownedFixture = fixture;
    await configureBaseline(ownedFixture.directory, "node", "eligible");
  })();
  await expect(settleWithin(failureResultBoundMs, configuration)).rejects.toBe(
    failure
  );
  expect(writesAfterRemoval).toBe(0);
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});
