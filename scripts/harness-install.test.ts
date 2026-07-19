import { test } from "bun:test";
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

const rootDir = path.join(import.meta.dirname, "..");
const pluginDir = path.join(rootDir, "plugins/harness");

const run = async (command: string[], cwd: string): Promise<void> => {
  const process = Bun.spawn(command, {
    cwd,
    stderr: "pipe",
    stdout: "pipe"
  });
  if ((await process.exited) !== 0) {
    throw new Error(
      `${command.join(" ")} failed:\n${await new Response(process.stderr).text()}`
    );
  }
};

const exists = (target: string): Promise<boolean> => Bun.file(target).exists();

test("installs from a non-Git plugin cache with only target runtime assets", async () => {
  const fixture = await mkdtemp(path.join(tmpdir(), "harness-install-"));
  const cachedPlugin = path.join(fixture, "cache", "harness");
  const target = path.join(fixture, "target");
  try {
    await cp(pluginDir, cachedPlugin, { recursive: true });
    await mkdir(
      path.join(
        cachedPlugin,
        "skills/harness-install/assets/bun/dist/generated"
      ),
      { recursive: true }
    );
    await writeFile(
      path.join(
        cachedPlugin,
        "skills/harness-install/assets/bun/dist/generated/package.json"
      ),
      "{}\n",
      "utf-8"
    );
    const includedAsset = "nested asset\n";
    const includedAssetPath = path.join(
      cachedPlugin,
      "skills/harness-install/assets/bun/qa-probe/kept/deep/asset.txt"
    );
    await mkdir(path.dirname(includedAssetPath), { recursive: true });
    await writeFile(includedAssetPath, includedAsset, "utf-8");
    const nestedCachePath = path.join(
      cachedPlugin,
      "skills/harness-install/assets/bun/qa-probe/.ruff_cache/generated.cache"
    );
    await mkdir(path.dirname(nestedCachePath), { recursive: true });
    await writeFile(nestedCachePath, "cache\n", "utf-8");
    const nearMatchAssetPath = path.join(
      cachedPlugin,
      "skills/harness-install/assets/bun/qa-probe/distribution/asset.txt"
    );
    await mkdir(path.dirname(nearMatchAssetPath), { recursive: true });
    await writeFile(nearMatchAssetPath, "near match\n", "utf-8");
    await run(["git", "init", target], fixture);
    const installerCommand = [
      "bun",
      path.join(
        cachedPlugin,
        "skills/harness-install/scripts/install-harness.ts"
      ),
      "--target",
      target,
      "--mode",
      "bun",
      "--ci-host",
      "github"
    ];
    await run(installerCommand, fixture);

    if (!(await exists(path.join(target, "WORKFLOW.md")))) {
      throw new Error(
        "installer must copy only selected target runtime assets"
      );
    }
    if (await exists(path.join(target, "dist/generated/package.json"))) {
      throw new Error("installer must exclude generated artifact directories");
    }
    if (
      (await readFile(
        path.join(target, "qa-probe/kept/deep/asset.txt"),
        "utf-8"
      )) !== includedAsset
    ) {
      throw new Error("installer must recursively include regular assets");
    }
    if (
      await exists(path.join(target, "qa-probe/.ruff_cache/generated.cache"))
    ) {
      throw new Error("installer must exclude nested cache directories");
    }
    if (!(await exists(path.join(target, "qa-probe/distribution/asset.txt")))) {
      throw new Error("installer must match generated directory names exactly");
    }
    const hooks = await Bun.spawn(
      ["git", "config", "--local", "--get", "core.hooksPath"],
      { cwd: target }
    ).exited;
    if (hooks === 0) {
      throw new Error("installer must leave hooks inactive by default");
    }
    const localWorkflow = "# Local workflow\n";
    await writeFile(path.join(target, "WORKFLOW.md"), localWorkflow, "utf-8");
    const conflict = Bun.spawn(installerCommand, {
      cwd: fixture,
      stderr: "pipe",
      stdout: "pipe"
    });
    const conflictExit = await conflict.exited;
    const conflictStderr = await new Response(conflict.stderr).text();
    if (
      conflictExit === 0 ||
      !conflictStderr.includes("conflict: WORKFLOW.md")
    ) {
      throw new Error("installer must fail when a packaged asset conflicts");
    }
    if (
      (await readFile(path.join(target, "WORKFLOW.md"), "utf-8")) !==
      localWorkflow
    ) {
      throw new Error("installer must preserve a conflicting target asset");
    }
  } finally {
    await rm(fixture, { force: true, recursive: true });
  }
}, 20_000);

test("excludes Python tool caches from a UV installation", async () => {
  const fixture = await mkdtemp(path.join(tmpdir(), "harness-install-uv-"));
  const cachedPlugin = path.join(fixture, "cache", "harness");
  const target = path.join(fixture, "target");
  try {
    await cp(pluginDir, cachedPlugin, { recursive: true });
    const cacheDirectories = [
      ".mypy_cache",
      ".pytest_cache",
      ".ruff_cache",
      "__pycache__"
    ];
    await Promise.all(
      cacheDirectories.map(async (cacheDirectory) => {
        const directory = path.join(
          cachedPlugin,
          "skills/harness-install/assets/uv",
          cacheDirectory
        );
        await mkdir(directory, { recursive: true });
        await writeFile(path.join(directory, "generated.cache"), "cache\n");
      })
    );
    await run(["git", "init", target], fixture);
    await run(
      [
        "bun",
        path.join(
          cachedPlugin,
          "skills/harness-install/scripts/install-harness.ts"
        ),
        "--target",
        target,
        "--mode",
        "uv",
        "--ci-host",
        "none"
      ],
      fixture
    );

    const cacheResults = await Promise.all(
      cacheDirectories.map(async (cacheDirectory) => ({
        cacheDirectory,
        exists: await exists(
          path.join(target, cacheDirectory, "generated.cache")
        )
      }))
    );
    const copiedCaches = cacheResults.filter(
      ({ exists: cacheExists }) => cacheExists
    );
    if (copiedCaches.length > 0) {
      throw new Error(
        `installer must exclude Python tool cache directories: ${copiedCaches.map(({ cacheDirectory }) => cacheDirectory).join(", ")}`
      );
    }
  } finally {
    await rm(fixture, { force: true, recursive: true });
  }
}, 20_000);
