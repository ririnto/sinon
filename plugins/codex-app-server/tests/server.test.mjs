import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { once } from "node:events";
import { chmod, mkdtemp, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import path from "node:path";
import { test } from "node:test";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { WebSocketServer } from "ws";

const serverPath = fileURLToPath(new URL("../server.mjs", import.meta.url));
const SOURCE_KINDS = [
  "cli",
  "vscode",
  "exec",
  "appServer",
  "subAgent",
  "subAgentReview",
  "subAgentCompact",
  "subAgentThreadSpawn",
  "subAgentOther",
  "unknown"
];

/**
 * Run the real stdio MCP server against a disposable Unix WebSocket fixture.
 */
const fixture = async (context, handler, options = {}) => {
  const directory = await mkdtemp("/tmp/sinon-mcp-");
  const socket = path.join(directory, "socket space-\u03A9?#:.sock");
  const http = createServer();
  const wsServer = new WebSocketServer({ server: http });
  const requests = [];
  const connections = new Set();
  wsServer.on("connection", (ws) => {
    connections.add(ws);
    ws.on("error", () => {});
    ws.on("close", () => connections.delete(ws));
    ws.on("message", (bytes) => {
      const message = JSON.parse(bytes.toString());
      if (!message.id) {
        return;
      }
      requests.push(message);
      const reply = (result) =>
        ws.send(JSON.stringify({ id: message.id, result }));
      if (message.method === "initialize" && !options.failInitialize) {
        reply({});
        return;
      }
      handler({ connections, message, reply, ws });
    });
  });
  http.listen(socket);
  await once(http, "listening");
  const codexBin = path.join(directory, "fake-codex.mjs");
  const transport = new StdioClientTransport({
    args: options.package
      ? [
          "exec",
          "--yes",
          "--install-links",
          `--package=${options.package}`,
          "--",
          "codex-app-server"
        ]
      : [serverPath],
    command: options.package ? "npm" : process.execPath,
    cwd: directory,
    env: {
      ...process.env,
      CODEX_APP_SERVER_SOCKET: socket,
      CODEX_BIN: options.catalog ? codexBin : path.join(directory, "absent"),
      ...(options.methods ? { CODEX_APP_SERVER_METHODS: options.methods } : {}),
      NODE_USE_SYSTEM_CA: "1",
      npm_config_cache: path.join(directory, "npm-cache")
    },
    stderr: "pipe"
  });
  if (options.catalog) {
    await writeFile(
      codexBin,
      `#!${process.execPath}\nimport { writeFileSync } from "node:fs";\nwriteFileSync(process.argv[5] + "/ClientRequest.json", ${JSON.stringify(JSON.stringify(options.catalog))});\n`
    );
    await chmod(codexBin, 0o755);
  }
  const client = new Client({ name: "fixture", version: "1" });
  context.after(async () => {
    await client.close();
    for (const ws of connections) {
      ws.terminate();
    }
    await promisify(wsServer.close.bind(wsServer))();
    await promisify(http.close.bind(http))();
    await rm(directory, { force: true, recursive: true });
  });
  await client.connect(transport);
  return { client, connections, requests };
};

/**
 * Decode an MCP tool's JSON result while preserving the error flag for assertions.
 */
const call = async (client, name, args = {}) => {
  const result = await client.callTool({ arguments: args, name });
  return {
    error: result.isError === true,
    text: result.content[0].text,
    value: result.isError ? null : JSON.parse(result.content[0].text)
  };
};

test("lists thirteen self-describing tools and all sources without repairing metadata", async (t) => {
  const { client, requests } = await fixture(t, ({ reply }) => {
    reply({
      data: [{ id: "fixture-thread", preview: "Fixture", source: "exec" }]
    });
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 13);
  for (const tool of tools) {
    assert.ok(tool.description);
    for (const field of Object.values(tool.inputSchema.properties)) {
      assert.ok(field.description);
    }
  }
  assert.equal(
    tools.find((tool) => tool.name === "codex_threads").annotations
      .readOnlyHint,
    true
  );
  const result = await call(client, "codex_threads");
  assert.equal(result.error, false);
  assert.equal(result.value[0].id, "fixture-thread");
  assert.deepEqual(requests.at(-1).params.sourceKinds, SOURCE_KINDS);
  assert.equal(requests.at(-1).params.useStateDbOnly, true);
});

test("packed executable includes and registers every feature module", async (t) => {
  const directory = await mkdtemp("/tmp/sinon-mcp-package-");
  t.after(() => rm(directory, { force: true, recursive: true }));
  const { stdout } = await promisify(execFile)(
    "npm",
    ["pack", "--json", "--ignore-scripts", "--pack-destination", directory],
    {
      cwd: path.dirname(serverPath),
      env: {
        ...process.env,
        npm_config_cache: path.join(directory, "npm-cache")
      }
    }
  );
  const [packed] = JSON.parse(stdout);
  for (const file of [
    "server.mjs",
    "lib/connection.mjs",
    "lib/thread-tools.mjs",
    "lib/mcp-tool.mjs",
    "lib/raw-call.mjs",
    "lib/turn-tools.mjs",
    "lib/thread-admin.mjs",
    "lib/catalog.mjs",
    "lib/policy.mjs"
  ]) {
    assert.ok(
      packed.files.some((entry) => entry.path === file),
      file
    );
  }
  const { client, requests } = await fixture(
    t,
    ({ message, reply }) => {
      if (message.method === "thread/start") {
        reply({ thread: { id: "fixture-ephemeral" } });
      } else if (message.method === "mcpServer/tool/call") {
        reply({ structuredContent: { fixture: true } });
      } else {
        reply({ fixture: "raw" });
      }
    },
    { package: path.join(directory, packed.filename) }
  );
  const { tools } = await client.listTools();
  assert.deepEqual(
    tools.map((tool) => tool.name),
    [
      "codex_threads",
      "codex_tail",
      "codex_start",
      "codex_send",
      "codex_steer",
      "codex_interrupt",
      "codex_loaded",
      "codex_fork",
      "codex_rename",
      "codex_goal",
      "codex_compact",
      "codex_mcp_tool",
      "codex_call"
    ]
  );
  const proxied = await call(client, "codex_mcp_tool", {
    server: "fixture-server",
    tool: "fixture-tool"
  });
  assert.deepEqual(proxied.value, { fixture: true });
  const started = requests.find((request) => request.method === "thread/start");
  assert.equal(started.params.ephemeral, true);
  assert.ok(
    started.params.cwd.startsWith("/tmp/sinon-mcp-") ||
      started.params.cwd.startsWith("/private/tmp/sinon-mcp-")
  );
  assert.deepEqual(requests.at(-1).params, {
    arguments: {},
    server: "fixture-server",
    threadId: "fixture-ephemeral",
    tool: "fixture-tool"
  });
  const raw = await call(client, "codex_call", {
    method: "thread/read",
    params: { threadId: "fixture-thread" }
  });
  assert.deepEqual(raw.value, { fixture: "raw" });
  assert.deepEqual(requests.at(-1).params, { threadId: "fixture-thread" });
});

test("starts a new thread's first turn directly, while existing threads resume", async (t) => {
  const { client, requests } = await fixture(t, ({ message, reply }) => {
    if (message.method === "thread/start") {
      reply({ thread: { id: "fixture-new" } });
    } else if (message.method === "turn/start") {
      reply({ turn: { id: "fixture-turn" } });
    } else {
      reply({});
    }
  });
  const started = await call(client, "codex_start", {
    cwd: "/tmp",
    name: "Fixture",
    text: "Authorized fixture turn"
  });
  assert.equal(started.value.turn.status, "started");
  assert.deepEqual(
    requests.map((request) => request.method),
    ["initialize", "thread/start", "thread/name/set", "turn/start"]
  );
  await call(client, "codex_send", {
    text: "Authorized fixture turn",
    thread_id: "fixture-existing"
  });
  assert.deepEqual(
    requests.slice(-3).map((request) => request.method),
    ["initialize", "thread/resume", "turn/start"]
  );
});

test("rejects non-positive trailing counts before opening a daemon connection", async (t) => {
  const { client, requests } = await fixture(t, ({ reply }) => {
    reply({ thread: { turns: [{ id: "old" }, { id: "new" }] } });
  });
  const invalid = await Promise.all(
    [0, -1].map((turns) =>
      call(client, "codex_tail", { thread_id: "fixture-thread", turns })
    )
  );
  for (const result of invalid) {
    assert.equal(result.error, true);
  }
  assert.equal(requests.length, 0);
  const result = await call(client, "codex_tail", {
    thread_id: "fixture-thread",
    turns: 1
  });
  assert.deepEqual(
    result.value.turns.map((turn) => turn.id),
    ["new"]
  );
});

test("buffers early completion, matches the turn, and returns structured failure diagnostics", async (t) => {
  const diagnostic = {
    codexErrorInfo: "usageLimitExceeded",
    message: "Fixture diagnostic"
  };
  const { client } = await fixture(t, ({ message, reply, ws }) => {
    if (message.method !== "turn/start") {
      reply({});
      return;
    }
    for (const note of [
      {
        method: "turn/completed",
        params: { turn: { id: "other", status: "completed" } }
      },
      {
        method: "item/completed",
        params: {
          item: { text: "Fixture final", type: "agentMessage" },
          turnId: "fixture-turn"
        }
      },
      {
        method: "turn/completed",
        params: {
          turn: { error: diagnostic, id: "fixture-turn", status: "failed" }
        }
      }
    ]) {
      ws.send(JSON.stringify(note));
    }
    reply({ turn: { id: "fixture-turn" } });
  });
  const result = await call(client, "codex_send", {
    text: "Authorized fixture turn",
    thread_id: "fixture-thread",
    wait_seconds: 2
  });
  assert.equal(result.value.status, "failed");
  assert.equal(result.value.finalMessage, "Fixture final");
  assert.deepEqual(result.value.error, diagnostic);
});

test("reports wait timeout and socket closure during a turn", async (t) => {
  let drop = false;
  const { client } = await fixture(t, ({ message, reply, ws }) => {
    reply(
      message.method === "turn/start" ? { turn: { id: "fixture-turn" } } : {}
    );
    if (message.method === "turn/start" && drop) {
      ws.close();
    }
  });
  const args = {
    text: "Authorized fixture turn",
    thread_id: "fixture-thread",
    wait_seconds: 0.05
  };
  const timeout = await call(client, "codex_send", args);
  assert.equal(timeout.value.status, "waitTimedOut");
  drop = true;
  const closed = await call(client, "codex_send", { ...args, wait_seconds: 2 });
  assert.equal(closed.value.status, "connectionClosed");
});

for (const failure of ["close", "frame"]) {
  test(`fails pending requests promptly on ${failure} and survives the connection failure`, async (t) => {
    let mode = failure;
    const { client } = await fixture(t, ({ reply, ws }) => {
      if (mode === "close") {
        ws.close();
      } else if (mode === "frame") {
        ws._socket.write(Buffer.from([0x83, 0x00]));
      } else {
        reply({ data: [] });
      }
    });
    const before = performance.now();
    const result = await call(client, "codex_threads");
    assert.equal(result.error, true);
    assert.ok(performance.now() - before < 2000);
    mode = "success";
    const recovered = await call(client, "codex_threads");
    assert.equal(recovered.error, false);
  });
}

test("closes rejected initialization connections and keeps reset-credit refusal offline", async (t) => {
  const { client, connections, requests } = await fixture(
    t,
    ({ message, ws }) => {
      ws.send(
        JSON.stringify({
          error: { code: -32_600, message: "Fixture incompatible protocol" },
          id: message.id
        })
      );
    },
    { failInitialize: true }
  );
  const refused = await call(client, "codex_call", {
    method: "account/rateLimitResetCredit/consume"
  });
  assert.equal(refused.error, true);
  assert.match(refused.text, /requires a separate direct human instruction/u);
  assert.equal(requests.length, 0);
  const failures = await Promise.all(
    Array.from({ length: 3 }, () => call(client, "codex_threads"))
  );
  for (const result of failures) {
    assert.match(result.text, /Fixture incompatible protocol/u);
  }
  await delay(50);
  assert.equal(connections.size, 0);
});

test("steers and interrupts only an in-progress last turn with its ID filled in", async (t) => {
  let status = "inProgress";
  const { client, requests } = await fixture(t, ({ message, reply }) => {
    if (message.method === "thread/read") {
      reply({
        thread: {
          turns: [
            { id: "old", status: "completed" },
            { id: "live", status }
          ]
        }
      });
    } else if (message.method === "turn/steer") {
      reply({ turnId: "live" });
    } else {
      reply({});
    }
  });
  const steered = await call(client, "codex_steer", {
    text: "Authorized steer",
    thread_id: "fixture-thread"
  });
  assert.equal(steered.value.turnId, "live");
  assert.deepEqual(requests.at(-1).params, {
    expectedTurnId: "live",
    input: [{ text: "Authorized steer", type: "text" }],
    threadId: "fixture-thread"
  });
  await call(client, "codex_interrupt", { thread_id: "fixture-thread" });
  assert.deepEqual(requests.at(-1).params, {
    threadId: "fixture-thread",
    turnId: "live"
  });
  status = "completed";
  const before = requests.length;
  const refused = await Promise.all([
    call(client, "codex_steer", { text: "Late", thread_id: "fixture-thread" }),
    call(client, "codex_interrupt", { thread_id: "fixture-thread" })
  ]);
  for (const result of refused) {
    assert.equal(result.error, true);
    assert.match(result.text, /not inProgress/u);
  }
  assert.deepEqual(
    requests
      .slice(before)
      .map((request) => request.method)
      .toSorted(),
    ["initialize", "initialize", "thread/read", "thread/read"]
  );
});

test("sends thin thread requests for fork, rename, goal, loaded list, and compaction", async (t) => {
  const { client, requests } = await fixture(t, ({ message, reply }) => {
    reply(message.method === "thread/fork" ? { thread: { id: "forked" } } : {});
  });
  const forked = await call(client, "codex_fork", {
    last_turn_id: "turn-1",
    name: "Fork",
    thread_id: "fixture-thread"
  });
  assert.equal(forked.value.threadId, "forked");
  const methods = () =>
    requests.map((request) => request.method).filter((m) => m !== "initialize");
  assert.deepEqual(methods(), ["thread/fork", "thread/name/set"]);
  assert.deepEqual(requests.find((r) => r.method === "thread/fork").params, {
    excludeTurns: true,
    lastTurnId: "turn-1",
    threadId: "fixture-thread"
  });
  await call(client, "codex_rename", {
    name: "New",
    thread_id: "fixture-thread"
  });
  await call(client, "codex_goal", {
    action: "get",
    thread_id: "fixture-thread"
  });
  await call(client, "codex_goal", {
    action: "set",
    objective: "Goal",
    status: "paused",
    thread_id: "fixture-thread",
    token_budget: 100
  });
  assert.deepEqual(requests.at(-1).params, {
    objective: "Goal",
    status: "paused",
    threadId: "fixture-thread",
    tokenBudget: 100
  });
  await call(client, "codex_goal", {
    action: "clear",
    thread_id: "fixture-thread"
  });
  await call(client, "codex_loaded", { limit: 5 });
  await call(client, "codex_compact", { thread_id: "fixture-thread" });
  assert.deepEqual(methods().slice(2), [
    "thread/name/set",
    "thread/goal/get",
    "thread/goal/set",
    "thread/goal/clear",
    "thread/loaded/list",
    "thread/compact/start"
  ]);
});

const CATALOG = {
  definitions: { Mode: { enum: ["a", "b"] } },
  oneOf: [
    {
      properties: {
        method: { enum: ["fs/readFile"] },
        params: {
          properties: { path: { type: "string" } },
          required: ["path"],
          type: "object"
        }
      }
    },
    {
      properties: {
        method: { enum: ["config/write"] },
        params: {
          properties: { mode: { $ref: "#/definitions/Mode" } },
          required: ["mode"],
          type: "object"
        }
      }
    },
    {
      properties: {
        method: { enum: ["thread/delete"] },
        params: { type: "object" }
      }
    },
    {
      properties: {
        method: { enum: ["account/rateLimitResetCredit/consume"] }
      }
    },
    {
      properties: {
        method: { enum: ["thread/read"] },
        params: { type: "object" }
      }
    }
  ]
};

test("generates allowlisted tools from the installed schema and refuses guarded methods", async (t) => {
  const { client, requests } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    {
      catalog: CATALOG,
      methods: "fs,config,thread,account"
    }
  );
  const { tools } = await client.listTools();
  const names = tools.map((tool) => tool.name);
  assert.ok(names.includes("codex_methods"));
  assert.ok(names.includes("codex_fs_readFile"));
  assert.ok(names.includes("codex_config_write"));
  for (const refused of [
    "codex_thread_delete",
    "codex_account_rateLimitResetCredit_consume"
  ]) {
    assert.ok(!names.includes(refused), refused);
  }
  assert.equal(names.filter((name) => name === "codex_thread_read").length, 0);
  assert.equal(
    tools.find((tool) => tool.name === "codex_fs_readFile").annotations
      .readOnlyHint,
    true
  );
  assert.equal(
    tools.find((tool) => tool.name === "codex_config_write").annotations
      .readOnlyHint,
    false
  );
  const read = await call(client, "codex_fs_readFile", { path: "/x" });
  assert.deepEqual(read.value, { ok: true });
  assert.deepEqual(requests.at(-1).params, { path: "/x" });
  const invalid = await call(client, "codex_config_write", { mode: "c" });
  assert.equal(invalid.error, true);
  const listed = await call(client, "codex_methods", {
    method: "config/write"
  });
  assert.equal(listed.value[0].class, "mutating");
  assert.deepEqual(listed.value[0].params.properties.mode.enum, ["a", "b"]);
  const all = await call(client, "codex_methods");
  assert.equal(
    all.value.find((entry) => entry.method === "thread/read").tool,
    "curated"
  );
  const cleanup = await call(client, "codex_call", { method: "thread/delete" });
  assert.equal(cleanup.error, true);
  assert.match(cleanup.text, /native codex archive/u);
  assert.equal(
    requests.filter((request) => request.method === "thread/delete").length,
    0
  );
});

test("keeps generated tools opt-in while discovery follows the installed catalog", async (t) => {
  const { client } = await fixture(t, ({ reply }) => reply({}), {
    catalog: CATALOG
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 14);
  assert.ok(tools.some((tool) => tool.name === "codex_methods"));
  assert.ok(!tools.some((tool) => tool.name === "codex_fs_readFile"));
  const listed = await call(client, "codex_methods", { prefix: "fs/" });
  assert.deepEqual(
    listed.value.map((entry) => entry.method),
    ["fs/readFile"]
  );
  assert.equal(listed.value[0].tool, "codex_call");
});

test("retains curated tools when a selected parameter schema cannot become an MCP tool", async (t) => {
  await Promise.all(
    [{ $ref: "#/definitions/Missing" }, { type: "string" }].map(
      async (params) => {
        const catalog = {
          definitions: {},
          oneOf: [{ properties: { method: { enum: ["future/read"] }, params } }]
        };
        const { client } = await fixture(t, ({ reply }) => reply({}), {
          catalog,
          methods: "future"
        });
        const { tools } = await client.listTools();
        assert.equal(tools.length, 13);
        assert.ok(!tools.some((tool) => tool.name === "codex_future_read"));
      }
    )
  );
});

test("serves only curated tools when the schema catalog is unavailable", async (t) => {
  const { client } = await fixture(t, ({ reply }) => reply({}), {
    methods: "fs"
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 13);
  assert.ok(!tools.some((tool) => tool.name === "codex_methods"));
});
