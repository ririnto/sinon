import { spawnSync } from "node:child_process";
import {
  chmod,
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  stat,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { setImmediate, setTimeout as delay } from "node:timers/promises";

import { expect, onTestFinished, test, vi } from "vitest";

import { materializeSample } from "../scripts/materialize-sample.js";
import {
  createCommandFixture,
  drainWithin,
  settleWithin,
  waitForFixtureTasks
} from "./fixture-command.ts";

const failureResultBoundMs = 1000;
const overlapWindowMs = 400;

const samples = [
  ["cargo-workspace", "Cargo.toml"],
  ["go-workspace", "go.work"],
  ["gradle-multi-module", "settings.gradle.kts"],
  ["maven-reactor", "pom.xml"],
  ["pnpm-workspace", "pnpm-workspace.yaml"],
  ["uv-workspace", "pyproject.toml"]
] as const;

const runMaterializer = (script: string, target: string) =>
  spawnSync(process.execPath, [
    script,
    "--sample",
    "go-workspace",
    "--target",
    target
  ]);

test.each(samples)(
  "materializes %s into an independent empty directory",
  async (name, manifest) => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-"));
    onTestFinished(async () => {
      await rm(directory, { force: true, recursive: true });
    });
    await materializeSample(name, directory);
    const copied = await stat(path.join(directory, manifest));
    expect(copied.isFile()).toBe(true);
    if (name === "gradle-multi-module") {
      const ruleset = await stat(
        path.join(
          directory,
          "buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
        )
      );
      expect(ruleset.isFile()).toBe(true);
      expect(
        await readFile(
          path.join(
            directory,
            "buildSrc/src/main/resources/META-INF/services/com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3"
          ),
          "utf-8"
        )
      ).toContain("com.ririnto.sinon.harness.ktlint.RuleSetProvider");
      const tests = await readdir(
        path.join(
          directory,
          "buildSrc/src/test/kotlin/com/ririnto/sinon/harness/ktlint"
        )
      );
      expect(tests.length).toBeGreaterThan(0);
      await expect(
        stat(path.join(directory, "buildSrc/settings.gradle.kts"))
      ).rejects.toThrow();
    }
  }
);

test("rejects a nonempty destination before writing", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-"));
  onTestFinished(async () => {
    await rm(directory, { force: true, recursive: true });
  });
  await writeFile(path.join(directory, "owned.txt"), "keep");
  await expect(materializeSample("go-workspace", directory)).rejects.toThrow(
    "empty"
  );
  expect(await readFile(path.join(directory, "owned.txt"), "utf-8")).toBe(
    "keep"
  );
});

test("rejects unsafe inputs before writing and excludes generated files", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-sample-fixture-"));
  onTestFinished(async () => {
    await rm(directory, { force: true, recursive: true });
  });
  const script = path.join(directory, "scripts/materialize-sample.ts");
  const source = path.join(directory, "samples/go-workspace");
  const target = path.join(directory, "target");
  await waitForFixtureTasks([
    mkdir(path.dirname(script), { recursive: true }),
    mkdir(source, { recursive: true }),
    mkdir(target)
  ]);
  await copyFile(
    path.resolve(import.meta.dirname, "../scripts/materialize-sample.ts"),
    script
  );
  await writeFile(path.join(source, "go.work"), "go 1.27\n");
  await symlink("go.work", path.join(source, "unsafe"));
  expect(runMaterializer(script, target).status).not.toBe(0);
  expect(await readdir(target)).toEqual([]);
  await rm(path.join(source, "unsafe"));
  await mkdir(path.join(source, "node_modules"));
  await symlink("../go.work", path.join(source, "node_modules/link"));
  const inaccessible = path.join(source, "node_modules/blocked");
  await mkdir(inaccessible);
  await chmod(inaccessible, 0);
  await mkdir(path.join(source, ".husky/_"), { recursive: true });
  await writeFile(path.join(source, ".husky/_/generated"), "generated");
  try {
    expect(runMaterializer(script, target).status).toBe(0);
  } finally {
    await chmod(inaccessible, 0o700);
  }
  expect(await readdir(target)).toEqual([".husky", "go.work"]);
  expect(await readdir(path.join(target, ".husky"))).toEqual([]);
});

test("a failed copy settles a started sibling copy before the materializer rejects", async () => {
  const fixture = await createCommandFixture("sinon-sample-copy-");
  const failedTarget = path.join(fixture.directory, "README.md");
  const lateTarget = path.join(fixture.directory, "Taskfile.yaml");
  const failure = Object.assign(new Error("Cannot copy the sample entry"), {
    code: "EACCES"
  });
  const expected = await readFile(
    path.resolve(import.meta.dirname, "../samples/go-workspace/Taskfile.yaml"),
    "utf-8"
  );
  const failureInvoked = Promise.withResolvers<null>();
  const siblingInvoked = Promise.withResolvers<null>();
  const releaseSibling = Promise.withResolvers<null>();
  let copied: string | undefined;
  let failed = false;
  let failedAfterCopy = false;
  let siblingStarted = false;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      cp: vi.fn<typeof fs.cp>(async (source, destination, options) => {
        if (destination === failedTarget) {
          failed = true;
          failedAfterCopy = copied !== undefined;
          failureInvoked.resolve(null);
          throw failure;
        }
        if (destination === lateTarget) {
          siblingStarted = true;
          siblingInvoked.resolve(null);
          await releaseSibling.promise;
          await fs.cp(source, destination, options);
          copied = await readFile(lateTarget, "utf-8");
        } else {
          await fs.cp(source, destination, options);
        }
      })
    };
  });
  vi.resetModules();
  const { materializeSample: materialize } =
    await import("../scripts/materialize-sample.js");
  let reported = false;
  const materializing = (async () => {
    await using ownedFixture = fixture;
    try {
      await materialize("go-workspace", ownedFixture.directory);
    } finally {
      reported = true;
    }
  })();
  const failureResult = (async () => {
    try {
      await settleWithin(failureResultBoundMs, materializing);
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
  const outcome = await failureResult;
  await drainWithin(failureResultBoundMs, materializing);
  expect(outcome).toBe(failure);
  if (siblingHeld) {
    expect(reportedWhileHeld).toBe(false);
    expect(failedWhileHeld || failedAfterCopy).toBe(true);
  } else {
    expect(
      failedWhileHeld && reportedWhileHeld,
      "the failure stayed pending and no sibling copy started within the overlap window"
    ).toBe(true);
  }
  expect(copied === expected).toBe(siblingStarted);
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});

test("a failed copy reports its failure within the bound when copies are serialized", async () => {
  const fixture = await createCommandFixture("sinon-sample-serial-");
  const { directory } = fixture;
  const failedTarget = path.join(directory, "README.md");
  const failure = Object.assign(new Error("Cannot copy the sample entry"), {
    code: "EACCES"
  });
  let turn: Promise<unknown> = Promise.resolve();
  let writesAfterRemoval = 0;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      cp: vi.fn<typeof fs.cp>(async (source, destination, options) => {
        const writing = (async () => {
          await Promise.allSettled([turn]);
          const present = await stat(directory)
            .then(() => true)
            .catch(() => false);
          if (!present) {
            writesAfterRemoval += 1;
          }
          if (destination === failedTarget) {
            throw failure;
          }
          await fs.cp(source, destination, options);
        })();
        turn = writing;
        await writing;
      })
    };
  });
  vi.resetModules();
  const { materializeSample: materialize } =
    await import("../scripts/materialize-sample.js");
  const materializing = (async () => {
    await using ownedFixture = fixture;
    await materialize("go-workspace", ownedFixture.directory);
  })();
  const outcome = await settleWithin(failureResultBoundMs, materializing).catch(
    (error: unknown) => error
  );
  await drainWithin(failureResultBoundMs, materializing);
  expect(outcome).toBe(failure);
  expect(writesAfterRemoval).toBe(0);
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});
