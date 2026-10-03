import { execFileSync, spawnSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  symlinkSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

interface NpxCall {
  argv: string[];
  cache: string | null;
  cwd: string;
}

interface HookDefinition {
  type: string;
  command: string;
  async?: boolean;
  timeout?: number;
}

interface HookConfig {
  hooks: Record<string, { matcher: string; hooks: HookDefinition[] }[]>;
}

type HookHost = "claude" | "codex";

const repositoryRoot = path.resolve(import.meta.dirname, "../../..");
const pluginRoot = path.join(repositoryRoot, "plugins/codegraph");
const placeholder = (name: string): string =>
  `${String.fromCodePoint(36)}{${name}}`;

const makeHarness = () => {
  const root = mkdtempSync(path.join(tmpdir(), "sinon-codegraph-test-"));
  onTestFinished(() => rmSync(root, { force: true, recursive: true }));
  const binaryDirectory = path.join(root, "stub bin");
  mkdirSync(binaryDirectory, { recursive: true });
  const logPath = path.join(root, "npx calls.jsonl");
  const npxPath = path.join(binaryDirectory, "npx");
  const npxSource = [
    "#!/usr/bin/env node",
    'import { appendFileSync } from "node:fs";',
    "const argv = process.argv.slice(2);",
    'const command = argv.includes("init") ? "INIT" : argv.includes("index") ? "INDEX" : "OTHER";',
    'appendFileSync(process.env.CODEGRAPH_STUB_LOG, JSON.stringify({ argv, cache: process.env.npm_config_cache ?? null, cwd: process.cwd() }) + "\\n");',
    'process.exit(Number(process.env["CODEGRAPH_STUB_EXIT_" + command] ?? "0"));'
  ].join("\n");
  writeFileSync(npxPath, npxSource, { mode: 0o755 });
  const env: NodeJS.ProcessEnv = {
    ...process.env,
    CODEGRAPH_STUB_EXIT_INDEX: "0",
    CODEGRAPH_STUB_EXIT_INIT: "0",
    CODEGRAPH_STUB_LOG: logPath,
    PATH: binaryDirectory + path.delimiter + (process.env.PATH ?? ""),
    XDG_CACHE_HOME: path.join(root, "xdg cache")
  };
  delete env.CLAUDE_PLUGIN_DATA;
  delete env.PLUGIN_DATA;
  return { env, logPath, npxPath, root };
};

const callsFrom = (logPath: string): NpxCall[] =>
  existsSync(logPath)
    ? readFileSync(logPath, "utf-8")
        .trimEnd()
        .split("\n")
        .filter(Boolean)
        .map((line) => JSON.parse(line) as NpxCall)
    : [];

const runGit = (cwd: string, args: string[]): void => {
  execFileSync("git", args, { cwd, stdio: "ignore" });
};

const makeRepository = (root: string): void => {
  mkdirSync(root, { recursive: true });
  runGit(root, ["init", "--quiet"]);
  writeFileSync(path.join(root, "README.md"), "CodeGraph test fixture\n");
  runGit(root, [
    "-c",
    "user.name=CodeGraph Test",
    "-c",
    "user.email=codegraph-test@example.invalid",
    "add",
    "README.md"
  ]);
  runGit(root, [
    "-c",
    "user.name=CodeGraph Test",
    "-c",
    "user.email=codegraph-test@example.invalid",
    "commit",
    "--quiet",
    "-m",
    "initial"
  ]);
};

const excludePath = (cwd: string): string =>
  execFileSync(
    "git",
    ["rev-parse", "--path-format=absolute", "--git-path", "info/exclude"],
    { cwd, encoding: "utf-8" }
  ).trim();

const readClaudeHooks = (): HookConfig =>
  JSON.parse(
    readFileSync(path.join(pluginRoot, "hooks/hooks.json"), "utf-8")
  ) as HookConfig;

const readCodexHooks = (): HookConfig =>
  JSON.parse(
    readFileSync(path.join(pluginRoot, "hooks/codex-hooks.json"), "utf-8")
  ) as HookConfig;

const hookCommand = (
  host: HookHost,
  event: string,
  matcher: string
): string => {
  const config = host === "claude" ? readClaudeHooks() : readCodexHooks();
  const entry = config.hooks[event]?.find((hook) => hook.matcher === matcher);
  const command = entry?.hooks[0]?.command;
  if (!command) {
    throw new Error(`Missing ${host} hook for ${event} ${matcher}`);
  }
  return command;
};

const runHook = (
  host: HookHost,
  event: string,
  matcher: string,
  cwd: string,
  harness: ReturnType<typeof makeHarness>,
  env = harness.env
) =>
  spawnSync("sh", ["-c", hookCommand(host, event, matcher)], {
    cwd,
    encoding: "utf-8",
    env
  });

const runConfiguredHook = (
  host: HookHost,
  event: string,
  matcher: string,
  cwd: string,
  harness: ReturnType<typeof makeHarness>,
  pluginData: string
) => {
  const dataVariable = host === "claude" ? "CLAUDE_PLUGIN_DATA" : "PLUGIN_DATA";
  const env: NodeJS.ProcessEnv = { ...harness.env, [dataVariable]: pluginData };
  if (dataVariable === "CLAUDE_PLUGIN_DATA") {
    delete env.PLUGIN_DATA;
  } else {
    delete env.CLAUDE_PLUGIN_DATA;
  }
  return runHook(host, event, matcher, cwd, harness, env);
};

test("Claude and Codex configs register startup preparation and direct MCP commands", () => {
  const claudeHooks = readClaudeHooks();
  expect(claudeHooks.hooks.SessionStart.map((entry) => entry.matcher)).toEqual([
    "startup"
  ]);
  expect(claudeHooks.hooks.PostToolUse.map((entry) => entry.matcher)).toEqual([
    "EnterWorktree"
  ]);
  expect(
    [...claudeHooks.hooks.SessionStart, ...claudeHooks.hooks.PostToolUse]
      .flatMap((entry) => entry.hooks)
      .every((hook) => hook.async === true)
  ).toBe(true);
  const claudeMcp = JSON.parse(
    readFileSync(path.join(pluginRoot, ".mcp.json"), "utf-8")
  ) as {
    mcpServers: {
      codegraph: {
        args: string[];
        command: string;
        env: Record<string, string>;
      };
    };
  };
  expect(claudeMcp.mcpServers.codegraph).toEqual({
    args: [
      "--yes",
      "--prefer-online",
      "@colbymchenry/codegraph",
      "serve",
      "--mcp"
    ],
    command: "npx",
    env: {
      npm_config_cache: `${placeholder("CLAUDE_PLUGIN_DATA")}/npm-cache`
    }
  });
  const plugin = JSON.parse(
    readFileSync(path.join(pluginRoot, "plugin.json"), "utf-8")
  ) as { extensions: { "com.openai": { hooks: string } } };
  expect(plugin.extensions["com.openai"].hooks).toBe(
    "./hooks/codex-hooks.json"
  );
  const codexHooks = readCodexHooks();
  expect(codexHooks.hooks.SessionStart.map((entry) => entry.matcher)).toEqual([
    "^startup$"
  ]);
  expect(codexHooks.hooks.SessionStart[0]?.hooks[0]).toMatchObject({
    async: true,
    type: "command"
  });
  expect(hookCommand("codex", "SessionStart", "^startup$")).toContain(
    "PLUGIN_DATA"
  );
  const codexMcp = JSON.parse(
    readFileSync(path.join(pluginRoot, "mcp.json"), "utf-8")
  ) as {
    mcpServers: {
      codegraph: {
        args: string[];
        command: string;
        env: Record<string, string>;
        type: string;
      };
    };
  };
  expect(codexMcp.mcpServers.codegraph).toEqual({
    args: [
      "--yes",
      "--prefer-online",
      "@colbymchenry/codegraph",
      "serve",
      "--mcp"
    ],
    command: "npx",
    env: {
      npm_config_cache: `${placeholder("PLUGIN_DATA")}/npm-cache`
    },
    type: "stdio"
  });
});

test("configured Claude and Codex hooks use their scoped caches and active cwd", () => {
  const harness = makeHarness();
  const repository = path.join(harness.root, "consumer repository with spaces");
  makeRepository(repository);
  const nestedCwd = path.join(repository, "nested workspace", "service");
  mkdirSync(nestedCwd, { recursive: true });
  const claudeData = path.join(harness.root, "claude plugin data");
  const codexData = path.join(harness.root, "codex plugin data");
  expect(
    runConfiguredHook(
      "claude",
      "SessionStart",
      "startup",
      nestedCwd,
      harness,
      claudeData
    ).status
  ).toBe(0);
  expect(
    runConfiguredHook(
      "claude",
      "PostToolUse",
      "EnterWorktree",
      nestedCwd,
      harness,
      claudeData
    ).status
  ).toBe(0);
  expect(
    runConfiguredHook(
      "codex",
      "SessionStart",
      "^startup$",
      nestedCwd,
      harness,
      codexData
    ).status
  ).toBe(0);
  const calls = callsFrom(harness.logPath);
  expect(calls).toHaveLength(6);
  expect(calls.map((call) => call.cwd)).toEqual(
    Array.from({ length: 6 }, () => realpathSync(nestedCwd))
  );
  expect(calls.map((call) => call.cache)).toEqual([
    path.join(claudeData, "npm-cache"),
    path.join(claudeData, "npm-cache"),
    path.join(claudeData, "npm-cache"),
    path.join(claudeData, "npm-cache"),
    path.join(codexData, "npm-cache"),
    path.join(codexData, "npm-cache")
  ]);
  expect(calls.map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"]
  ]);
  expect(readFileSync(excludePath(nestedCwd), "utf-8")).toContain(
    ".codegraph\n"
  );
});

test("primary checkout exclusion is literal, idempotent, and preserves missing final newline", () => {
  const harness = makeHarness();
  const repository = path.join(harness.root, "primary checkout");
  makeRepository(repository);
  const nestedCwd = path.join(repository, "packages", "service");
  mkdirSync(nestedCwd, { recursive: true });
  const exclude = excludePath(nestedCwd);
  mkdirSync(path.dirname(exclude), { recursive: true });
  const existing = "# local exclusions\n*.cache";
  writeFileSync(exclude, existing);
  expect(
    runHook("claude", "SessionStart", "startup", nestedCwd, harness).status
  ).toBe(0);
  expect(readFileSync(exclude, "utf-8")).toBe(`${existing}\n.codegraph\n`);
  expect(
    runHook("claude", "SessionStart", "startup", nestedCwd, harness).status
  ).toBe(0);
  expect(readFileSync(exclude, "utf-8")).toBe(`${existing}\n.codegraph\n`);
  expect(callsFrom(harness.logPath).map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"]
  ]);
  const crlf = "# local exclusions\r\n.codegraph\r\n";
  writeFileSync(exclude, crlf);
  expect(
    runHook("claude", "SessionStart", "startup", nestedCwd, harness).status
  ).toBe(0);
  expect(readFileSync(exclude, "utf-8")).toBe(crlf);
});

test("primary checkout creates a missing Git exclude file", () => {
  const harness = makeHarness();
  const repository = path.join(harness.root, "primary checkout");
  makeRepository(repository);
  const nestedCwd = path.join(repository, "nested");
  mkdirSync(nestedCwd);
  const exclude = excludePath(nestedCwd);
  rmSync(path.dirname(exclude), { force: true, recursive: true });
  expect(
    runHook("claude", "SessionStart", "startup", nestedCwd, harness).status
  ).toBe(0);
  expect(readFileSync(exclude, "utf-8")).toBe("\n.codegraph\n");
});

test("linked worktree keeps its shared exclude unchanged and still initializes", () => {
  const harness = makeHarness();
  const primary = path.join(harness.root, "primary");
  makeRepository(primary);
  const primaryExclude = excludePath(primary);
  const original = "# keep local rules\n";
  writeFileSync(primaryExclude, original);
  const linked = path.join(harness.root, "linked worktree");
  runGit(primary, ["worktree", "add", "--quiet", "--detach", linked, "HEAD"]);
  expect(
    runHook("claude", "SessionStart", "startup", linked, harness).status
  ).toBe(0);
  expect(readFileSync(primaryExclude, "utf-8")).toBe(original);
  expect(callsFrom(harness.logPath).map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"]
  ]);
});

test("bare repositories and non-Git directories skip exclude changes", () => {
  const harness = makeHarness();
  const bare = path.join(harness.root, "bare repository");
  mkdirSync(bare);
  runGit(bare, ["init", "--bare", "--quiet"]);
  expect(
    runHook("claude", "SessionStart", "startup", bare, harness).status
  ).toBe(0);
  const nonGit = path.join(harness.root, "not a repository");
  mkdirSync(nonGit);
  expect(
    runHook("claude", "SessionStart", "startup", nonGit, harness).status
  ).toBe(0);
  expect(callsFrom(harness.logPath).map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"]
  ]);
});

test("missing Git skips exclusion and still runs CodeGraph", () => {
  const harness = makeHarness();
  const repository = path.join(harness.root, "repository");
  makeRepository(repository);
  const exclude = excludePath(repository);
  const original = "# keep local rules\n";
  writeFileSync(exclude, original);
  const minimalPath = path.join(harness.root, "path without git");
  mkdirSync(minimalPath);
  symlinkSync(
    existsSync("/bin/sh") ? "/bin/sh" : "/usr/bin/sh",
    path.join(minimalPath, "sh")
  );
  symlinkSync(process.execPath, path.join(minimalPath, "node"));
  symlinkSync(harness.npxPath, path.join(minimalPath, "npx"));
  const result = runHook(
    "claude",
    "SessionStart",
    "startup",
    repository,
    harness,
    { ...harness.env, PATH: minimalPath }
  );
  expect(result.status).toBe(0);
  expect(readFileSync(exclude, "utf-8")).toBe(original);
  expect(callsFrom(harness.logPath)).toHaveLength(2);
});

test("hook selects the XDG cache fallback when plugin data is absent", () => {
  const harness = makeHarness();
  const cwd = path.join(harness.root, "non-Git directory");
  mkdirSync(cwd);
  expect(
    runHook("claude", "SessionStart", "startup", cwd, harness).status
  ).toBe(0);
  expect(callsFrom(harness.logPath).map((call) => call.cache)).toEqual([
    path.join(harness.root, "xdg cache", "sinon", "codegraph", "npm-cache"),
    path.join(harness.root, "xdg cache", "sinon", "codegraph", "npm-cache")
  ]);
});

test("init failure prevents index and preserves its exit status", () => {
  const harness = makeHarness();
  const cwd = path.join(harness.root, "non-Git directory");
  mkdirSync(cwd);
  harness.env.CODEGRAPH_STUB_EXIT_INIT = "19";
  expect(
    runHook("claude", "SessionStart", "startup", cwd, harness).status
  ).toBe(19);
  expect(callsFrom(harness.logPath).map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"]
  ]);
});

test("index failure is returned after successful initialization", () => {
  const harness = makeHarness();
  const cwd = path.join(harness.root, "non-Git directory");
  mkdirSync(cwd);
  harness.env.CODEGRAPH_STUB_EXIT_INDEX = "23";
  expect(
    runHook("claude", "SessionStart", "startup", cwd, harness).status
  ).toBe(23);
  expect(callsFrom(harness.logPath).map((call) => call.argv)).toEqual([
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "init", "--yes"],
    ["--yes", "--prefer-online", "@colbymchenry/codegraph", "index"]
  ]);
});
