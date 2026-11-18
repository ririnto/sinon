import { execFileSync, spawnSync } from "node:child_process";
import {
  copyFile,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { configureBaseline } from "../../plugins/harness/scripts/configure.ts";
import { readUltraciteResult } from "../../plugins/harness/tests/ultracite-fixture.ts";

const root = path.resolve(import.meta.dirname, "../..");
const run = (directory: string, args: string[]): void => {
  execFileSync("pnpm", ["--dir", directory, ...args], { stdio: "inherit" });
};
const sample = path.join(root, "plugins/harness/samples/pnpm-workspace");
run(sample, ["install", "--frozen-lockfile"]);
run(sample, ["run", "check:ultracite"]);
const source: unknown = JSON.parse(
  await readFile(path.join(root, "package.json"), "utf-8")
);
if (
  typeof source !== "object" ||
  source === null ||
  !("packageManager" in source) ||
  typeof source.packageManager !== "string"
) {
  throw new TypeError("Expected the repository package manager");
}
const checkRenderer = async (renderer: "html" | "react"): Promise<void> => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-consumer-"));
  try {
    await configureBaseline(
      directory,
      renderer === "html" ? "frontend" : "react",
      "eligible"
    );
    const manifest: unknown = JSON.parse(
      await readFile(path.join(directory, "package.json"), "utf-8")
    );
    if (typeof manifest !== "object" || manifest === null) {
      throw new TypeError("Expected a generated manifest");
    }
    await writeFile(
      path.join(directory, "package.json"),
      `${JSON.stringify({ ...manifest, packageManager: source.packageManager }, null, 2)}\n`
    );
    await mkdir(path.join(directory, "src"));
    await copyFile(
      path.join(
        root,
        `plugins/harness/tests/fixtures/storybook/${renderer}/button.stories.js`
      ),
      path.join(directory, "src/button.stories.js")
    );
    run(directory, ["install"]);
    run(directory, ["run", "check:ultracite"]);
    run(directory, ["run", "check:storybook"]);
    const boundary = path.join("src", "type-aware-boundary.ts");
    await writeFile(
      path.join(directory, boundary),
      "void Promise.resolve();\n"
    );
    const { status, diagnostics } = readUltraciteResult(
      spawnSync("pnpm", [
        "--dir",
        directory,
        "run",
        "check:ultracite",
        "--format",
        "json"
      ])
    );
    if (
      status !== 1 ||
      !diagnostics.some(
        ({ code, filename }) =>
          code === "typescript(no-floating-promises)" &&
          filename !== undefined &&
          path.normalize(filename) === boundary
      )
    ) {
      throw new Error(
        `Missing no-floating-promises diagnostic for ${boundary}: ${JSON.stringify(diagnostics)}`
      );
    }
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
};
await checkRenderer("html");
await checkRenderer("react");
