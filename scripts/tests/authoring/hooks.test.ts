import { expect, test } from "bun:test";
import { mkdir, symlink } from "node:fs/promises";
import path from "node:path";

import { repositoryPaths } from "../../test-support/paths.js";
import { runProcess } from "../../test-support/process.js";
import { withTempDirectory } from "../../test-support/temp-directory.js";

const HOOK_PATH = path.join(
  repositoryPaths.authoringAssetRoot,
  "hooks/check.ts"
);

const runHook = (
  projectDirectory: string,
  input: Readonly<Record<string, unknown>>
) =>
  runProcess([process.execPath, HOOK_PATH], {
    env: { ...process.env, CLAUDE_PROJECT_DIR: projectDirectory },
    stdin: JSON.stringify(input)
  });

test("allows a write inside the project root", async () => {
  await withTempDirectory("authoring-hook-allowed-", async (fixture) => {
    const projectDirectory = path.join(fixture, "project");
    const sourceDirectory = path.join(projectDirectory, "src");
    await mkdir(sourceDirectory, { recursive: true });
    const result = await runHook(projectDirectory, {
      tool_input: { file_path: path.join(sourceDirectory, "new.ts") },
      tool_name: "Write"
    });
    expect(result).toBe(0);
  });
});

test("rejects a secret filename", async () => {
  await withTempDirectory("authoring-hook-secret-", async (fixture) => {
    const projectDirectory = path.join(fixture, "project");
    await mkdir(projectDirectory);
    const result = await runHook(projectDirectory, {
      tool_input: { file_path: path.join(projectDirectory, ".env") },
      tool_name: "Write"
    });
    expect(result).toBe(2);
  });
});

test("rejects a write outside the project root", async () => {
  await withTempDirectory("authoring-hook-outside-", async (fixture) => {
    const projectDirectory = path.join(fixture, "project");
    const outsideDirectory = path.join(fixture, "outside");
    await mkdir(projectDirectory);
    await mkdir(outsideDirectory);
    const result = await runHook(projectDirectory, {
      tool_input: {
        file_path: path.join(outsideDirectory, "new.ts")
      },
      tool_name: "Write"
    });
    expect(result).toBe(2);
  });
});

test("rejects a write through a directory symlink", async () => {
  await withTempDirectory("authoring-hook-directory-link-", async (fixture) => {
    const projectDirectory = path.join(fixture, "project");
    const outsideDirectory = path.join(fixture, "outside");
    const linkedDirectory = path.join(projectDirectory, "linked");
    await mkdir(projectDirectory);
    await mkdir(outsideDirectory);
    await symlink(outsideDirectory, linkedDirectory, "dir");
    const result = await runHook(projectDirectory, {
      tool_input: {
        file_path: path.join(linkedDirectory, "new.ts")
      },
      tool_name: "Write"
    });
    expect(result).toBe(2);
  });
});
