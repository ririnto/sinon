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
import { setImmediate } from "node:timers/promises";

import { expect, onTestFinished, test, vi } from "vitest";

import { materializeSample } from "../scripts/materialize-sample.js";
import {
  createCommandFixture,
  waitForFixtureTasks
} from "./fixture-command.ts";

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

test("a failed copy settles sibling copies before the materializer rejects", async () => {
  const fixture = await createCommandFixture("sinon-sample-copy-");
  const { directory } = fixture;
  const failedTarget = path.join(directory, "README.md");
  const lateTarget = path.join(directory, "Taskfile.yaml");
  const failure = Object.assign(new Error("Cannot copy the sample entry"), {
    code: "EACCES"
  });
  const lateStarted = Promise.withResolvers<null>();
  const failureStarted = Promise.withResolvers<null>();
  const releaseLate = Promise.withResolvers<null>();
  const lateFinished = Promise.withResolvers<null>();
  let copied: string | undefined;
  using mock = vi.doMock("node:fs/promises", async (importOriginal) => {
    const fs = await importOriginal<typeof import("node:fs/promises")>();
    return {
      ...fs,
      cp: vi.fn<typeof fs.cp>(async (source, destination, options) => {
        if (destination === failedTarget) {
          await lateStarted.promise;
          failureStarted.resolve(null);
          throw failure;
        }
        if (destination === lateTarget) {
          lateStarted.resolve(null);
          try {
            await releaseLate.promise;
            await fs.cp(source, destination, options);
            copied = await readFile(lateTarget, "utf-8");
          } finally {
            lateFinished.resolve(null);
          }
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
  const rejection = expect(materializing).rejects.toBe(failure);
  try {
    await failureStarted.promise;
    await setImmediate();
    expect(reported).toBe(false);
  } finally {
    releaseLate.resolve(null);
    await lateFinished.promise;
    await rejection;
  }
  expect(copied).toBe(
    await readFile(
      path.resolve(
        import.meta.dirname,
        "../samples/go-workspace/Taskfile.yaml"
      ),
      "utf-8"
    )
  );
  await expect(stat(fixture.directory)).rejects.toMatchObject({
    code: "ENOENT"
  });
});
