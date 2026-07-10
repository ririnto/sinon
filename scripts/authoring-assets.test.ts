// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

interface ProcessResult {
  exitCode: number;
  stderr: string;
  stdout: string;
}

interface JsonRpcResponse {
  id?: unknown;
  jsonrpc?: unknown;
  result?: {
    protocolVersion?: unknown;
    serverInfo?: {
      name?: unknown;
    };
  };
}

const REPOSITORY_ROOT = path.resolve(import.meta.dirname, "..");
const ASSET_ROOT = path.join(
  REPOSITORY_ROOT,
  "plugins/agent-capability-kit/skills/plugin-authoring/assets"
);
const HOOK_PATH = path.join(ASSET_ROOT, "hooks/check.ts");
const MCP_PATH = path.join(ASSET_ROOT, "servers/example-mcp.ts");
const MONITOR_PATH = path.join(ASSET_ROOT, "monitors/watch.ts");
const JSON_ASSETS = [
  ".lsp.json",
  ".mcp.json",
  "hooks.json",
  "monitors/monitors.json",
  "plugin.json",
  "settings.json"
] as const;

const collectProcess = async (
  child: Bun.Subprocess<"pipe", "pipe", "pipe">
): Promise<ProcessResult> => {
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text()
  ]);
  return { exitCode, stderr, stdout };
};

const runHook = (
  projectDirectory: string,
  input: Readonly<Record<string, unknown>> | string
): Promise<ProcessResult> => {
  const child = Bun.spawn([process.execPath, HOOK_PATH], {
    env: { ...process.env, CLAUDE_PROJECT_DIR: projectDirectory },
    stderr: "pipe",
    stdin: "pipe",
    stdout: "pipe"
  });
  child.stdin.write(typeof input === "string" ? input : JSON.stringify(input));
  child.stdin.end();
  return collectProcess(child);
};

const expectHookExit = async (
  projectDirectory: string,
  input: Readonly<Record<string, unknown>> | string,
  expectedExitCode: number
): Promise<void> => {
  const result = await runHook(projectDirectory, input);
  expect(result.exitCode).toBe(expectedExitCode);
  if (expectedExitCode === 2) {
    expect(result.stderr.length).toBeGreaterThan(0);
  }
};

test("copyable plugin configuration assets parse as JSON", async () => {
  await Promise.all(
    JSON_ASSETS.map(async (assetPath) => {
      JSON.parse(await Bun.file(path.join(ASSET_ROOT, assetPath)).text());
    })
  );
});

test("the MCP starter speaks newline-delimited JSON-RPC", async () => {
  const child = Bun.spawn([process.execPath, MCP_PATH], {
    stderr: "pipe",
    stdin: "pipe",
    stdout: "pipe"
  });
  child.stdin.write(
    `${JSON.stringify({
      id: 1,
      jsonrpc: "2.0",
      method: "initialize",
      params: {
        capabilities: {},
        clientInfo: { name: "asset-smoke-test", version: "1.0.0" },
        protocolVersion: "2025-11-25"
      }
    })}\n`
  );
  child.stdin.end();
  const result = await collectProcess(child);
  expect(result.exitCode).toBe(0);
  expect(result.stderr).toBe("");
  expect(result.stdout).not.toContain("Content-Length");
  const lines = result.stdout.trim().split("\n");
  expect(lines).toHaveLength(1);
  const response = JSON.parse(lines[0] ?? "null") as JsonRpcResponse;
  expect(response.jsonrpc).toBe("2.0");
  expect(response.id).toBe(1);
  expect(response.result?.protocolVersion).toBe("2025-11-25");
  expect(response.result?.serverInfo?.name).toBe("example-mcp");
});

test("the hook starter handles realistic paths and symlink attacks", async () => {
  const projectDirectory = await mkdtemp(
    path.join(tmpdir(), "sinon-hook-project-")
  );
  const outsideDirectory = await mkdtemp(
    path.join(tmpdir(), "sinon-hook-outside-")
  );
  try {
    const insideDirectory = path.join(projectDirectory, "src with spaces");
    const insideFile = path.join(insideDirectory, "safe.ts");
    const outsideFile = path.join(outsideDirectory, "outside.ts");
    await mkdir(insideDirectory, { recursive: true });
    await writeFile(insideFile, "export {};\n", "utf-8");
    await writeFile(outsideFile, "outside\n", "utf-8");
    await expectHookExit(
      projectDirectory,
      { tool_input: { file_path: insideFile }, tool_name: "Write" },
      0
    );
    await expectHookExit(
      projectDirectory,
      {
        tool_input: { file_path: path.join(insideDirectory, "new.ts") },
        tool_name: "Write"
      },
      0
    );
    await expectHookExit(
      projectDirectory,
      {
        tool_input: { file_path: path.join(projectDirectory, ".env") },
        tool_name: "Edit"
      },
      2
    );
    await expectHookExit(
      projectDirectory,
      {
        tool_input: { file_path: path.join(projectDirectory, ".env.local") },
        tool_name: "Write"
      },
      2
    );
    await expectHookExit(
      projectDirectory,
      { tool_input: { file_path: outsideFile }, tool_name: "Write" },
      2
    );
    const linkedFile = path.join(projectDirectory, "linked-file.ts");
    await symlink(outsideFile, linkedFile);
    await expectHookExit(
      projectDirectory,
      { tool_input: { file_path: linkedFile }, tool_name: "Edit" },
      2
    );
    const danglingFile = path.join(projectDirectory, "dangling-file.ts");
    await symlink(path.join(outsideDirectory, "missing.ts"), danglingFile);
    await expectHookExit(
      projectDirectory,
      { tool_input: { file_path: danglingFile }, tool_name: "Write" },
      2
    );
    const linkedDirectory = path.join(projectDirectory, "linked-directory");
    await symlink(outsideDirectory, linkedDirectory, "dir");
    await expectHookExit(
      projectDirectory,
      {
        tool_input: { file_path: path.join(linkedDirectory, "new.ts") },
        tool_name: "Write"
      },
      2
    );
    await expectHookExit(
      projectDirectory,
      { tool_input: { file_path: outsideFile }, tool_name: "Read" },
      0
    );
    await expectHookExit(projectDirectory, "not-json", 2);
  } finally {
    await Promise.all([
      rm(projectDirectory, { force: true, recursive: true }),
      rm(outsideDirectory, { force: true, recursive: true })
    ]);
  }
});

test("monitor assets are complete and persistent", async () => {
  const monitorDefinitions = (await Bun.file(
    path.join(ASSET_ROOT, "monitors/monitors.json")
  ).json()) as readonly Readonly<Record<string, unknown>>[];
  expect(monitorDefinitions.length).toBeGreaterThan(0);
  for (const monitor of monitorDefinitions) {
    expect(typeof monitor["name"]).toBe("string");
    expect(typeof monitor["command"]).toBe("string");
    expect(typeof monitor["description"]).toBe("string");
  }
  const dataDirectory = await mkdtemp(
    path.join(tmpdir(), "sinon-monitor-data-")
  );
  try {
    const child = Bun.spawn([process.execPath, MONITOR_PATH], {
      env: { ...process.env, CLAUDE_PLUGIN_DATA: dataDirectory },
      stderr: "pipe",
      stdout: "pipe"
    });
    try {
      await Bun.sleep(250);
      expect(child.exitCode).toBe(null);
      expect(
        await Bun.file(
          path.join(dataDirectory, "monitor-state/example-monitor.txt")
        ).exists()
      ).toBe(true);
    } finally {
      child.kill();
      await child.exited;
    }
  } finally {
    await rm(dataDirectory, { force: true, recursive: true });
  }
});
