import assert from "node:assert/strict";
import { execFile, spawn } from "node:child_process";
import { once } from "node:events";
import { chmod, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import path from "node:path";
import { PassThrough, Writable } from "node:stream";
import { test } from "node:test";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import {
  LoggingMessageNotificationSchema,
  ToolListChangedNotificationSchema
} from "@modelcontextprotocol/sdk/types.js";
import { WebSocketServer } from "ws";

import { watchThreads } from "../lib/monitor.mjs";
import { createNativeSessions } from "../lib/native-session.mjs";
import { closeQueues, enqueueMessage } from "../lib/queue.mjs";
import { SessionTransport } from "../lib/session-transport.mjs";

const serverPath = fileURLToPath(new URL("../server.mjs", import.meta.url));
const monitorPath = fileURLToPath(new URL("../monitor.mjs", import.meta.url));
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

const CATALOG = {
  definitions: { Mode: { enum: ["a", "b", "#/definitions/literal"] } },
  oneOf: [
    {
      properties: {
        method: { enum: ["fs/readFile"] },
        params: {
          properties: { path: { type: "string" } },
          required: ["path"],
          type: "object"
        }
      },
      required: ["method", "params"],
      type: "object"
    },
    {
      properties: {
        method: { enum: ["config/write"] },
        params: {
          properties: { mode: { $ref: "#/definitions/Mode" } },
          required: ["mode"],
          type: "object"
        }
      },
      required: ["method", "params"],
      type: "object"
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
      NODE_USE_SYSTEM_CA: "1",
      npm_config_cache: path.join(directory, "npm-cache")
    },
    stderr: "pipe"
  });
  const versionFile = path.join(directory, "version");
  const schemaFile = path.join(directory, "catalog.json");
  const generatedFile = path.join(directory, "generations");
  if (options.catalog) {
    await writeFile(versionFile, "fixture-cli 1");
    await writeFile(
      schemaFile,
      JSON.stringify({
        ClientNotification: options.clientNotifications ?? { oneOf: [] },
        ClientRequest: options.catalog,
        ServerNotification: options.serverNotifications ?? { oneOf: [] },
        ServerRequest: options.serverRequests ?? { oneOf: [] }
      })
    );
    await writeFile(
      codexBin,
      `#!${process.execPath}
import { appendFileSync, readFileSync, writeFileSync } from "node:fs";
if (process.argv[2] === "--version") {
  process.stdout.write(readFileSync(${JSON.stringify(versionFile)}, "utf-8"));
} else {
  appendFileSync(${JSON.stringify(generatedFile)}, "generated\\n");
  const schemas = JSON.parse(readFileSync(${JSON.stringify(schemaFile)}, "utf-8"));
  for (const [name, schema] of Object.entries(schemas)) {
    writeFileSync(process.argv[5] + "/" + name + ".json", JSON.stringify(schema));
  }
}
`
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
  return {
    client,
    connections,
    generatedFile,
    requests,
    schemaFile,
    socket,
    versionFile
  };
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

test("lists self-describing curated tools and all sources without repairing metadata", async (t) => {
  const { client, requests } = await fixture(t, ({ reply }) => {
    reply({
      data: [{ id: "fixture-thread", preview: "Fixture", source: "exec" }]
    });
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 14);
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
  assert.ok(!packed.files.some((entry) => entry.path === "lib/raw-call.mjs"));
  for (const file of [
    "server.mjs",
    "monitor.mjs",
    "monitors/monitors.json",
    "lib/monitor.mjs",
    "lib/connection.mjs",
    "lib/thread-tools.mjs",
    "lib/mcp-tool.mjs",
    "lib/native-session.mjs",
    "lib/session-transport.mjs",
    "skills/thread-control/SKILL.md",
    "skills/thread-control/assets/monitor.json",
    "skills/thread-control/references/monitor.md",
    "lib/turn-tools.mjs",
    "lib/thread-admin.mjs",
    "lib/catalog.mjs",
    "lib/policy.mjs",
    "lib/queue.mjs",
    "lib/turn.mjs",
    "lib/schema.mjs"
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
    { catalog: CATALOG, package: path.join(directory, packed.filename) }
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
      "codex_queue",
      "codex_methods",
      "codex_fs_readFile",
      "codex_config_write",
      "codex_thread_delete",
      "codex_account_rateLimitResetCredit_consume",
      "codex_thread_read"
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
  const native = await call(client, "codex_thread_read", {
    threadId: "fixture-thread"
  });
  assert.deepEqual(native.value, { fixture: "raw" });
  assert.deepEqual(requests.at(-1).params, { threadId: "fixture-thread" });
});

test("starts a new thread's first turn directly, while existing threads resume", async (t) => {
  const { client, requests } = await fixture(t, ({ message, reply }) => {
    if (message.method === "thread/start") {
      reply({ thread: { id: "fixture-new" } });
    } else if (message.method === "turn/start") {
      reply({ turn: { id: "fixture-turn" } });
    } else if (message.method === "thread/read") {
      reply({ thread: { turns: [] } });
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
    requests.slice(-4).map((request) => request.method),
    ["initialize", "thread/read", "thread/resume", "turn/start"]
  );
});

test("rejects non-positive page and trailing counts before opening a daemon connection", async (t) => {
  const { client, requests } = await fixture(t, ({ reply }) => {
    reply({ thread: { turns: [{ id: "old" }, { id: "new" }] } });
  });
  const invalid = await Promise.all(
    [0, -1].flatMap((turns) => [
      call(client, "codex_tail", { thread_id: "fixture-thread", turns }),
      call(client, "codex_threads", { limit: turns })
    ])
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
      reply(message.method === "thread/read" ? { thread: { turns: [] } } : {});
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
    if (message.method === "turn/start") {
      reply({ turn: { id: "fixture-turn" } });
    } else if (message.method === "thread/read") {
      reply({ thread: { turns: [] } });
    } else {
      reply({});
    }
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
    { catalog: CATALOG, failInitialize: true }
  );
  const refused = await call(
    client,
    "codex_account_rateLimitResetCredit_consume"
  );
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
    mode: "steer",
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
  const refused = [
    await call(client, "codex_steer", {
      text: "Late",
      thread_id: "fixture-thread"
    }),
    await call(client, "codex_interrupt", { thread_id: "fixture-thread" })
  ];
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

test("derives every native tool with exact parameter schemas and conservative annotations", async (t) => {
  const { client, requests } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    { catalog: CATALOG }
  );
  const { tools } = await client.listTools();
  assert.equal(tools.length, 19);
  assert.ok(!tools.some((tool) => tool.name === "codex_call"));
  for (const method of CATALOG.oneOf.map(
    (entry) => entry.properties.method.enum[0]
  )) {
    const tool = tools.find(
      (item) => item.name === `codex_${method.replaceAll("/", "_")}`
    );
    assert.ok(tool, method);
    assert.match(tool.description, new RegExp(method, "u"));
    assert.match(tool.description, /class: (?:read|mutating)/u);
    assert.match(tool.description, /writer/u);
    assert.equal(
      tool.annotations.destructiveHint,
      !tool.annotations.readOnlyHint
    );
  }
  const readTool = tools.find((tool) => tool.name === "codex_fs_readFile");
  assert.equal(readTool.annotations.readOnlyHint, true);
  assert.deepEqual(readTool.inputSchema, CATALOG.oneOf[0].properties.params);
  const writeTool = tools.find((tool) => tool.name === "codex_config_write");
  assert.equal(writeTool.annotations.readOnlyHint, false);
  assert.deepEqual(writeTool.inputSchema.$defs.Mode, CATALOG.definitions.Mode);
  assert.equal(writeTool.inputSchema.properties.mode.$ref, "#/$defs/Mode");
  const read = await call(client, "codex_fs_readFile", { path: "/x" });
  assert.deepEqual(read.value, { ok: true });
  assert.deepEqual(requests.at(-1).params, { path: "/x" });
  const literal = await call(client, "codex_config_write", {
    mode: "#/definitions/literal"
  });
  assert.equal(literal.error, false);
  assert.deepEqual(requests.at(-1).params, { mode: "#/definitions/literal" });
  const before = requests.length;
  const invalid = await call(client, "codex_config_write", { mode: "c" });
  assert.equal(invalid.error, true);
  assert.equal(requests.length, before);
  await Promise.all(
    [
      ["codex_thread_delete", /native codex archive/u],
      [
        "codex_account_rateLimitResetCredit_consume",
        /separate direct human instruction/u
      ]
    ].map(async ([name, reason]) => {
      const result = await call(client, name);
      assert.equal(result.error, true);
      assert.match(result.text, reason);
      assert.equal(requests.length, before);
    })
  );
  const listed = await call(client, "codex_methods", { method: "thread/read" });
  assert.equal(listed.value[0].tool, "codex_thread_read");
});

test("discovers definition-free catalogs with namespace-boundary filtering", async (t) => {
  const { client } = await fixture(t, ({ reply }) => reply({}), {
    catalog: {
      oneOf: ["fs/readFile", "fsSection/readFile"].map((method) => ({
        properties: { method: { enum: [method] }, params: { type: "object" } }
      }))
    }
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 16);
  await Promise.all(
    ["fs", "fs/"].map(async (prefix) => {
      const result = await call(client, "codex_methods", { prefix });
      assert.deepEqual(
        result.value.map((entry) => entry.method),
        ["fs/readFile"]
      );
    })
  );
});

test("retains curated tools when native parameter references are missing", async (t) => {
  await Promise.all(
    [{ $ref: "#/definitions/Missing" }].map(async (params) => {
      const { client } = await fixture(t, ({ reply }) => reply({}), {
        catalog: {
          definitions: {},
          oneOf: [{ properties: { method: { enum: ["future/read"] }, params } }]
        }
      });
      const { tools } = await client.listTools();
      assert.equal(tools.length, 14);
      assert.ok(!tools.some((tool) => tool.name === "codex_future_read"));
    })
  );
});

test("preserves boolean property schema semantics in SDK-compatible input schemas", async (t) => {
  const { client, requests } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    {
      catalog: {
        oneOf: [
          {
            properties: {
              method: { enum: ["future/json"] },
              params: {
                properties: { denied: false, payload: true },
                required: ["payload"],
                type: "object"
              }
            },
            required: ["method", "params"],
            type: "object"
          }
        ]
      }
    }
  );
  const { tools } = await client.listTools();
  const tool = tools.find((entry) => entry.name === "codex_future_json");
  assert.deepEqual(tool.inputSchema.properties, {
    denied: { not: {} },
    payload: {}
  });
  const invalid = await call(client, "codex_future_json", {
    denied: null,
    payload: true
  });
  assert.equal(invalid.error, true);
  assert.equal(requests.length, 0);
  const valid = await call(client, "codex_future_json", {
    payload: ["native", { arbitrary: true }]
  });
  assert.equal(valid.error, false);
  assert.deepEqual(requests.at(-1).params, {
    payload: ["native", { arbitrary: true }]
  });
});

test("wraps null and union parameters without changing native validation or dispatch", async (t) => {
  const nullParams = { type: "null" };
  const unionParams = {
    oneOf: [
      {
        additionalProperties: false,
        properties: { mode: { const: "a" } },
        required: ["mode"],
        type: "object"
      },
      { type: "string" }
    ]
  };
  const { client, requests } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    {
      catalog: {
        definitions: { Choice: unionParams },
        oneOf: [
          {
            properties: {
              method: { enum: ["future/null"] },
              params: nullParams
            },
            required: ["method", "params"],
            type: "object"
          },
          {
            properties: {
              method: { enum: ["future/union"] },
              params: { $ref: "#/definitions/Choice" }
            },
            required: ["method", "params"],
            type: "object"
          }
        ]
      }
    }
  );
  const { tools } = await client.listTools();
  assert.deepEqual(
    tools.find((tool) => tool.name === "codex_future_null").inputSchema
      .properties.params,
    nullParams
  );
  assert.deepEqual(
    tools.find((tool) => tool.name === "codex_future_union").inputSchema
      .properties.params,
    unionParams
  );
  const invalid = await call(client, "codex_future_null");
  assert.equal(invalid.error, true);
  assert.equal(requests.length, 0);
  const nil = await call(client, "codex_future_null", { params: null });
  assert.equal(nil.error, false);
  assert.equal(requests.at(-1).params, null);
  const union = await call(client, "codex_future_union", {
    params: { mode: "a" }
  });
  assert.equal(union.error, false);
  assert.deepEqual(requests.at(-1).params, { mode: "a" });
  const before = requests.length;
  const extra = await call(client, "codex_future_union", {
    params: { extra: true, mode: "a" }
  });
  assert.equal(extra.error, true);
  assert.equal(requests.length, before);
});

test("maps ambiguous and reserved names uniquely and stably across catalog changes", async (t) => {
  const methods = [
    "thread/goal/get",
    "a/b",
    "a_b",
    "compact",
    "methods",
    "mcp/tool",
    "method/new",
    `future/${"long".repeat(40)}`
  ];
  const { client, schemaFile } = await fixture(t, ({ reply }) => reply({}), {
    catalog: {
      oneOf: methods.map((method) => ({
        properties: { method: { enum: [method] } }
      }))
    }
  });
  const firstResult = await call(client, "codex_methods");
  const first = firstResult.value;
  assert.equal(
    first.find((entry) => entry.method === "thread/goal/get").tool,
    "codex_thread_goal_get"
  );
  assert.equal(first.find((entry) => entry.method === "a/b").tool, "codex_a_b");
  const names = first.map((entry) => entry.tool);
  assert.equal(new Set(names).size, methods.length);
  assert.ok(
    names.every((name) => name.length <= 128 && /^[A-Za-z0-9_]+$/u.test(name))
  );
  const schemas = JSON.parse(await readFile(schemaFile, "utf-8"));
  schemas.ClientRequest.oneOf.push({
    properties: { method: { enum: ["a-b"] } }
  });
  await writeFile(schemaFile, JSON.stringify(schemas));
  const secondResult = await call(client, "codex_methods");
  const second = secondResult.value;
  assert.deepEqual(second.slice(0, first.length), first);
  assert.ok(!names.includes(second.at(-1).tool));
});

test("rejects duplicate native method names atomically", async (t) => {
  const entry = { properties: { method: { enum: ["future/read"] } } };
  const { client } = await fixture(t, ({ reply }) => reply({}), {
    catalog: { oneOf: [entry, entry] }
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 14);
  assert.ok(!tools.some((tool) => tool.name === "codex_future_read"));
});

test("propagates daemon active-turn and writer refusals without retrying", async (t) => {
  await Promise.all(
    ["thread/resume", "turn/start"].map(async (method) => {
      const { client, requests } = await fixture(
        t,
        ({ message, reply, ws }) => {
          if (message.method === method) {
            ws.send(
              JSON.stringify({
                error: {
                  code: -32_600,
                  message: "Thread has an in-progress turn or active writer"
                },
                id: message.id
              })
            );
          } else if (message.method === "thread/read") {
            reply({ thread: { turns: [] } });
          } else {
            reply({});
          }
        }
      );
      const result = await call(client, "codex_send", {
        text: "next",
        thread_id: "fixture-thread"
      });
      assert.equal(result.error, true);
      assert.match(result.text, /in-progress turn or active writer/u);
      assert.deepEqual(
        requests
          .filter((request) => request.method !== "initialize")
          .map((request) => request.method),
        method === "thread/resume"
          ? ["thread/read", "thread/resume"]
          : ["thread/read", "thread/resume", "turn/start"]
      );
    })
  );
});

test("serves only curated tools when the schema catalog is unavailable", async (t) => {
  const { client } = await fixture(t, ({ reply }) => reply({}), {
    methods: "fs"
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 14);
  assert.ok(tools.some((tool) => tool.name === "codex_methods"));
});

test("queues active sends and delivers fresh turns in order, buffering early completion", async (t) => {
  const threadId = "fixture-queued";
  const turns = [{ id: "active", status: "inProgress" }];
  const delivered = [];
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply, ws }) => {
      if (message.method === "thread/read") {
        reply({ thread: { turns } });
      } else if (message.method === "turn/start") {
        assert.notEqual(turns.at(-1).status, "inProgress");
        delivered.push(message.params.input[0].text);
        const turn = { id: `fresh-${delivered.length}`, status: "completed" };
        turns.push(turn);
        ws.send(
          JSON.stringify({
            method: "turn/completed",
            params: { threadId, turn }
          })
        );
        reply({ turn });
      } else {
        reply({});
      }
    }
  );
  const first = await call(client, "codex_send", {
    text: "first",
    thread_id: threadId
  });
  const second = await call(client, "codex_send", {
    text: "second",
    thread_id: threadId
  });
  assert.equal(first.value.status, "queued");
  assert.equal(second.value.status, "queued");
  assert.ok(first.value.id);
  assert.deepEqual(delivered, []);
  assert.ok(!requests.some((request) => request.method === "turn/steer"));
  turns[0].status = "completed";
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        method: "turn/completed",
        params: { threadId, turn: turns[0] }
      })
    );
  }
  await t.waitFor(() => assert.equal(delivered.length, 2), { timeout: 2000 });
  assert.deepEqual(delivered, ["first", "second"]);
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  const receipt = await call(client, "codex_queue", { thread_id: threadId });
  assert.deepEqual(
    receipt.value.messages.map((record) => record.status),
    ["completed", "completed"]
  );
  assert.deepEqual(
    receipt.value.messages.map((record) => record.turnId),
    ["fresh-1", "fresh-2"]
  );
});

test("default steering queues and a known response.interrupt failure avoids explicit steering", async (t) => {
  const turns = [
    {
      error: {
        message: "unsupported websocket request type: response.interrupt"
      },
      id: "failed",
      status: "failed"
    },
    { id: "active", status: "inProgress" }
  ];
  const { client, requests } = await fixture(t, ({ message, reply }) => {
    reply(message.method === "thread/read" ? { thread: { turns } } : {});
  });
  const queued = await call(client, "codex_steer", {
    mode: "steer",
    text: "later",
    thread_id: "fixture"
  });
  assert.equal(queued.value.status, "queued");
  assert.match(queued.value.reason, /response.interrupt/u);
  assert.ok(
    !requests.some(
      (request) =>
        request.method === "turn/steer" || request.method === "turn/start"
    )
  );
  const cancelled = await call(client, "codex_queue", {
    action: "cancel",
    thread_id: "fixture"
  });
  assert.equal(cancelled.value.messages[0].status, "refused");
  assert.match(cancelled.value.messages[0].reason, /Cancelled/u);
});

test("detects unsupported steering responses and does not retry an ambiguous write", async (t) => {
  const { client, requests } = await fixture(t, ({ message, reply, ws }) => {
    if (message.method === "thread/read") {
      reply({ thread: { turns: [{ id: "active", status: "inProgress" }] } });
    } else if (message.method === "turn/steer") {
      ws.send(
        JSON.stringify({
          error: {
            code: -32_000,
            message: "unsupported websocket request type: response.interrupt"
          },
          id: message.id
        })
      );
    } else {
      reply({});
    }
  });
  const refused = await call(client, "codex_steer", {
    mode: "steer",
    text: "now",
    thread_id: "fixture"
  });
  assert.equal(refused.value.status, "refused");
  assert.equal(refused.value.unsupportedInterrupt, true);
  assert.equal(
    requests.filter((request) => request.method === "turn/steer").length,
    1
  );
  assert.ok(!requests.some((request) => request.method === "turn/start"));
  const later = await call(client, "codex_steer", {
    mode: "steer",
    text: "later",
    thread_id: "fixture"
  });
  assert.equal(later.value.status, "queued");
  assert.equal(
    requests.filter((request) => request.method === "turn/steer").length,
    1
  );
  await call(client, "codex_queue", { action: "cancel", thread_id: "fixture" });
});

test("queue connection loss refuses undelivered messages without reconnecting", async (t) => {
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply }) => {
      reply(
        message.method === "thread/read"
          ? { thread: { turns: [{ id: "active", status: "inProgress" }] } }
          : {}
      );
    }
  );
  await call(client, "codex_send", { text: "later", thread_id: "fixture" });
  for (const ws of connections) {
    ws.close();
  }
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  const receipt = await call(client, "codex_queue", { thread_id: "fixture" });
  assert.equal(receipt.value.messages[0].status, "refused");
  assert.match(receipt.value.messages[0].reason, /connection closed/u);
  assert.ok(!requests.some((request) => request.method === "turn/start"));
});

test("queue writer refusal stops the batch without retrying or forcing another writer", async (t) => {
  let active = true;
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply, ws }) => {
      if (message.method === "thread/read") {
        reply({
          thread: {
            turns: [
              { id: "active", status: active ? "inProgress" : "completed" }
            ]
          }
        });
      } else if (message.method === "turn/start") {
        ws.send(
          JSON.stringify({
            error: { code: -32_000, message: "active writer holds thread" },
            id: message.id
          })
        );
      } else {
        reply({});
      }
    }
  );
  await call(client, "codex_send", { text: "later", thread_id: "fixture" });
  active = false;
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        method: "turn/completed",
        params: {
          threadId: "fixture",
          turn: { id: "active", status: "completed" }
        }
      })
    );
  }
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  const receipt = await call(client, "codex_queue", { thread_id: "fixture" });
  assert.equal(receipt.value.messages[0].status, "refused");
  assert.match(receipt.value.messages[0].reason, /active writer/u);
  assert.equal(
    requests.filter((request) => request.method === "turn/start").length,
    1
  );
});

test("queue rechecks a replacement active turn before delivery", async (t) => {
  let turns = [{ id: "original", status: "inProgress" }];
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply }) => {
      reply(message.method === "thread/read" ? { thread: { turns } } : {});
    }
  );
  await call(client, "codex_send", { text: "later", thread_id: "fixture" });
  const before = requests.filter(
    (request) => request.method === "thread/read"
  ).length;
  turns = [
    { id: "original", status: "completed" },
    { id: "replacement", status: "inProgress" }
  ];
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        method: "turn/completed",
        params: { threadId: "fixture", turn: turns[0] }
      })
    );
  }
  await t.waitFor(
    () =>
      assert.ok(
        requests.filter((request) => request.method === "thread/read").length >
          before
      ),
    { timeout: 2000 }
  );
  assert.ok(!requests.some((request) => request.method === "turn/start"));
  const receipt = await call(client, "codex_queue", {
    action: "cancel",
    thread_id: "fixture"
  });
  assert.equal(receipt.value.messages[0].status, "refused");
});

test("refused server-initiated requests cannot wake queue delivery", async (t) => {
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply }) => {
      if (!message.error) {
        reply(
          message.method === "thread/read"
            ? { thread: { turns: [{ id: "active", status: "inProgress" }] } }
            : {}
        );
      }
    }
  );
  await call(client, "codex_send", { text: "later", thread_id: "fixture" });
  const before = requests.filter(
    (request) => request.method === "thread/read"
  ).length;
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        id: "server-completion",
        method: "turn/completed",
        params: {
          threadId: "fixture",
          turn: { id: "active", status: "completed" }
        }
      })
    );
  }
  await t.waitFor(
    () =>
      assert.ok(
        requests.some(
          (request) =>
            request.id === "server-completion" &&
            request.error?.code === -32_601
        )
      ),
    { timeout: 2000 }
  );
  assert.equal(
    requests.filter((request) => request.method === "thread/read").length,
    before
  );
  await call(client, "codex_queue", { action: "cancel", thread_id: "fixture" });
});

test("an idle send waiting for completion permits authorized interruption of its active turn", async (t) => {
  let active = false;
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply }) => {
      if (message.method === "thread/read") {
        reply({
          thread: {
            turns: active ? [{ id: "active", status: "inProgress" }] : []
          }
        });
      } else if (message.method === "turn/start") {
        active = true;
        reply({ turn: { id: "active" } });
      } else if (message.method === "turn/interrupt") {
        active = false;
        for (const ws of connections) {
          ws.send(
            JSON.stringify({
              method: "turn/completed",
              params: {
                threadId: "fixture",
                turn: { id: "active", status: "interrupted" }
              }
            })
          );
        }
        reply({});
      } else {
        reply({});
      }
    }
  );
  const sending = call(client, "codex_send", {
    text: "now",
    thread_id: "fixture",
    wait_seconds: 2
  });
  await t.waitFor(
    () =>
      assert.ok(requests.some((request) => request.method === "turn/start")),
    { timeout: 2000 }
  );
  const interrupted = await call(client, "codex_interrupt", {
    thread_id: "fixture"
  });
  assert.equal(interrupted.value.status, "interrupted");
  const sent = await sending;
  assert.equal(sent.value.status, "interrupted");
});

test("queue deadline bounds stalled initialization before delivery", async (t) => {
  const { requests, socket, connections } = await fixture(t, () => {}, {
    failInitialize: true
  });
  const previousSocket = process.env.CODEX_APP_SERVER_SOCKET;
  process.env.CODEX_APP_SERVER_SOCKET = socket;
  t.after(() => {
    closeQueues();
    if (previousSocket === undefined) {
      delete process.env.CODEX_APP_SERVER_SOCKET;
    } else {
      process.env.CODEX_APP_SERVER_SOCKET = previousSocket;
    }
  });
  t.mock.timers.enable({ apis: ["setTimeout"] });
  const queued = enqueueMessage("deadline-fixture", "pending text");
  await t.waitFor(() => assert.equal(requests.length, 1), { timeout: 2000 });
  t.mock.timers.tick(1_800_000);
  const result = await queued;
  assert.equal(result.status, "refused");
  assert.match(result.reason, /deadline expired/u);
  t.mock.timers.reset();
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  assert.deepEqual(
    requests.map((request) => request.method),
    ["initialize"]
  );
});

const direction = (method) => ({
  oneOf: [{ properties: { method: { enum: [method] } } }]
});

test("validates native parameters and keeps non-client directions discovery-only", async (t) => {
  const { client, requests } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    {
      catalog: CATALOG,
      clientNotifications: direction("initialized"),
      serverNotifications: direction("thread/status/changed"),
      serverRequests: direction("item/commandExecution/requestApproval")
    }
  );
  await Promise.all(
    [
      ["codex_config_write", { mode: "invalid" }],
      ["codex_fs_readFile", {}],
      ["codex_fs_readFile", { path: 2 }],
      ["codex_initialized", {}],
      ["codex_item_commandExecution_requestApproval", {}],
      ["codex_thread_status_changed", {}],
      ["codex_absent_method", {}]
    ].map(async ([name, args]) => {
      const result = await call(client, name, args);
      assert.equal(result.error, true);
      assert.equal(requests.length, 0);
    })
  );
  await Promise.all(
    [
      ["serverRequest", "item/commandExecution/requestApproval"],
      ["serverNotification", "thread/status/changed"],
      ["clientNotification", "initialized"]
    ].map(async ([kind, method]) => {
      const result = await call(client, "codex_methods", { kind, method });
      assert.equal(result.value[0].tool, "discovery_only");
    })
  );
  const valid = await call(client, "codex_config_write", {
    extra: true,
    mode: "b"
  });
  assert.deepEqual(valid.value, { ok: true });
  assert.deepEqual(requests.at(-1).params, { extra: true, mode: "b" });
});

test("refreshes native tools and notifications on version and same-version schema changes", async (t) => {
  const { client, requests, schemaFile, versionFile } = await fixture(
    t,
    ({ reply }) => reply({ ok: true }),
    {
      catalog: {
        ...CATALOG,
        definitions: {
          ...CATALOG.definitions,
          Unrelated: { description: "unrelated".repeat(10_000), type: "string" }
        }
      }
    }
  );
  let changes = 0;
  client.setNotificationHandler(ToolListChangedNotificationSchema, () => {
    changes += 1;
  });
  assert.equal(client.getServerCapabilities().tools.listChanged, true);
  const before = await call(client, "codex_methods", {
    method: "config/write"
  });
  assert.ok(JSON.stringify(before.value[0].params).length < 1000);
  assert.equal(before.value[0].params.$defs.Unrelated, undefined);
  const schemas = JSON.parse(await readFile(schemaFile, "utf-8"));
  schemas.ClientRequest.definitions.Mode.enum.push("future");
  schemas.ClientRequest.oneOf.push(
    {
      properties: {
        method: { enum: ["future/new"] },
        params: {
          additionalProperties: false,
          properties: { count: { minimum: 1, type: "integer" } },
          required: ["count"],
          type: "object"
        }
      },
      required: ["method", "params"],
      type: "object"
    },
    {
      properties: {
        method: { enum: ["command/exec/newNative"] },
        params: { type: "object" }
      },
      required: ["method", "params"],
      type: "object"
    }
  );
  await writeFile(schemaFile, JSON.stringify(schemas));
  const { tools } = await client.listTools();
  assert.ok(tools.some((tool) => tool.name === "codex_future_new"));
  await t.waitFor(() => assert.equal(changes, 1));
  const updated = await call(client, "codex_config_write", { mode: "future" });
  assert.equal(updated.error, false);
  const requestCount = requests.length;
  const invalid = await call(client, "codex_future_new", {
    count: 0,
    extra: true
  });
  assert.equal(invalid.error, true);
  assert.equal(requests.length, requestCount);
  const future = await call(client, "codex_future_new", { count: 1 });
  assert.equal(future.error, false);
  const futureCommand = await call(client, "codex_command_exec_newNative", {});
  assert.equal(futureCommand.error, false);
  assert.equal(requests.at(-1).method, "command/exec/newNative");
  await client.listTools();
  assert.equal(changes, 1);
  await writeFile(versionFile, "fixture-cli 2");
  const versionChanged = await call(client, "codex_config_write", {
    mode: "future"
  });
  assert.equal(versionChanged.error, false);
  await t.waitFor(() => assert.equal(changes, 2));
  schemas.ClientRequest.oneOf = schemas.ClientRequest.oneOf.filter(
    (entry) => entry.properties.method.enum[0] !== "future/new"
  );
  await writeFile(schemaFile, JSON.stringify(schemas));
  const removed = await client.listTools();
  assert.ok(!removed.tools.some((tool) => tool.name === "codex_future_new"));
  const refused = await call(client, "codex_future_new", { count: 1 });
  assert.equal(refused.error, true);
});

test("refuses native calls offline when current schema validation is unavailable", async (t) => {
  const { client, requests, versionFile } = await fixture(
    t,
    ({ reply }) => reply({}),
    { catalog: CATALOG }
  );
  await rm(versionFile);
  const result = await call(client, "codex_thread_read", {
    threadId: "fixture"
  });
  assert.equal(result.error, true);
  assert.equal(requests.length, 0);
  const refused = await call(
    client,
    "codex_account_rateLimitResetCredit_consume"
  );
  assert.equal(refused.error, true);
  assert.match(refused.text, /separate direct human instruction/u);
  assert.equal(requests.length, 0);
});

test("rejects discovery after valid generation fails catalog derivation", async (t) => {
  const { client, schemaFile } = await fixture(t, ({ reply }) => reply({}), {
    catalog: CATALOG
  });
  const before = await client.listTools();
  const schemas = JSON.parse(await readFile(schemaFile, "utf-8"));
  const method = { properties: { method: { enum: ["future/duplicate"] } } };
  schemas.ClientRequest.oneOf.push(method, method);
  await writeFile(schemaFile, JSON.stringify(schemas));
  const discovery = await call(client, "codex_methods");
  assert.equal(discovery.error, true);
  assert.match(discovery.text, /Duplicated native tool name/u);
  const after = await client.listTools();
  assert.deepEqual(after.tools, before.tools);
});

test("cancels native calls during schema refresh before opening a socket", async (t) => {
  const { client, requests, versionFile } = await fixture(
    t,
    ({ reply }) => reply({}),
    { catalog: CATALOG }
  );
  await writeFile(versionFile, "fixture-cli 2");
  const controller = new AbortController();
  const pending = client.callTool(
    { arguments: { mode: "a" }, name: "codex_config_write" },
    undefined,
    { signal: controller.signal }
  );
  const rejected = assert.rejects(pending);
  controller.abort(new Error("Fixture cancellation"));
  await rejected;
  await client.listTools();
  assert.equal(requests.length, 0);
});

test("cancellation closes a stalled native initialize connection without dispatching", async (t) => {
  const { client, connections, requests } = await fixture(t, () => {}, {
    catalog: CATALOG,
    failInitialize: true
  });
  const controller = new AbortController();
  const pending = client.callTool(
    { arguments: { mode: "a" }, name: "codex_config_write" },
    undefined,
    { signal: controller.signal }
  );
  const rejected = assert.rejects(pending);
  await t.waitFor(() => assert.equal(requests.length, 1), { timeout: 2000 });
  controller.abort(new Error("Fixture cancellation"));
  await rejected;
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  assert.deepEqual(
    requests.map((request) => request.method),
    ["initialize"]
  );
});

const commandCatalog = {
  oneOf: [
    "command/exec",
    "command/exec/write",
    "command/exec/resize",
    "command/exec/terminate",
    "fs/watch",
    "fs/unwatch"
  ].map((method) => ({
    properties: { method: { enum: [method] }, params: { type: "object" } },
    required: ["method", "params"],
    type: "object"
  }))
};

test("keeps interactive command follow-ups on their originating connection and returns streamed output", async (t) => {
  let owner;
  let complete;
  const { client, connections, requests } = await fixture(
    t,
    ({ message, reply, ws }) => {
      if (message.method === "command/exec") {
        owner = ws;
        complete = reply;
      } else {
        assert.equal(ws, owner);
        reply({});
        if (message.method === "command/exec/terminate") {
          ws.send(
            JSON.stringify({
              method: "command/exec/outputDelta",
              params: {
                capReached: false,
                deltaBase64: "Zml4dHVyZQ==",
                processId: "fixture-process",
                stream: "stdout"
              }
            })
          );
          complete({ exitCode: 0, stderr: "", stdout: "" });
        }
      }
    },
    { catalog: commandCatalog }
  );
  const execution = call(client, "codex_command_exec", {
    command: ["fixture"],
    processId: "fixture-process",
    streamStdin: true,
    streamStdoutStderr: true
  });
  await t.waitFor(() => assert.ok(owner), { timeout: 2000 });
  const written = await call(client, "codex_command_exec_write", {
    deltaBase64: "eA==",
    processId: "fixture-process"
  });
  assert.equal(written.error, false);
  const resized = await call(client, "codex_command_exec_resize", {
    processId: "fixture-process",
    size: { cols: 80, rows: 24 }
  });
  assert.equal(resized.error, false);
  const terminated = await call(client, "codex_command_exec_terminate", {
    processId: "fixture-process"
  });
  assert.equal(terminated.error, false);
  const result = await execution;
  assert.equal(result.error, false);
  assert.equal(
    result.value.notifications[0].params.deltaBase64,
    "Zml4dHVyZQ=="
  );
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  assert.equal(
    requests.filter((request) => request.method === "initialize").length,
    1
  );
  const absent = await call(client, "codex_command_exec_write", {
    processId: "fixture-process"
  });
  assert.equal(absent.error, true);
  assert.equal(connections.size, 0);
});

test("cancelling an active native command releases its connection and process identifier", async (t) => {
  const { client, connections, requests } = await fixture(t, () => {}, {
    catalog: commandCatalog
  });
  const controller = new AbortController();
  const pending = client.callTool(
    {
      arguments: { command: ["fixture"], processId: "fixture-cancel" },
      name: "codex_command_exec"
    },
    undefined,
    { signal: controller.signal }
  );
  const rejected = assert.rejects(pending);
  await t.waitFor(
    () =>
      assert.ok(requests.some((request) => request.method === "command/exec")),
    { timeout: 2000 }
  );
  controller.abort(new Error("Fixture cancellation"));
  await rejected;
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  const absent = await call(client, "codex_command_exec_write", {
    processId: "fixture-cancel"
  });
  assert.equal(absent.error, true);
  assert.equal(connections.size, 0);
  assert.equal(
    requests.filter((request) => request.method === "command/exec").length,
    1
  );
});

test("keeps watch notifications live until native unwatch or MCP transport closure", async (t) => {
  let owner;
  const { client, connections } = await fixture(
    t,
    ({ message, reply, ws }) => {
      if (message.method === "fs/watch") {
        owner = ws;
      } else {
        assert.equal(ws, owner);
      }
      reply({});
    },
    { catalog: commandCatalog }
  );
  const events = [];
  client.setNotificationHandler(
    LoggingMessageNotificationSchema,
    ({ params }) => {
      events.push(params.data);
    }
  );
  const started = await call(client, "codex_fs_watch", {
    path: "/fixture",
    watchId: "fixture-watch"
  });
  assert.equal(started.error, false);
  assert.equal(connections.size, 1);
  owner.send(
    JSON.stringify({
      method: "fs/changed",
      params: { paths: ["/fixture"], watchId: "fixture-watch" }
    })
  );
  await t.waitFor(() => assert.equal(events.length, 1), { timeout: 2000 });
  const removed = await call(client, "codex_fs_unwatch", {
    watchId: "fixture-watch"
  });
  assert.equal(removed.error, false);
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
  const watched = await call(client, "codex_fs_watch", {
    path: "/fixture",
    watchId: "fixture-watch"
  });
  assert.equal(watched.error, false);
  await client.close();
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
});

test("retains the last catalog atomically after a failed refresh and refuses stale validation", async (t) => {
  const { client, requests, schemaFile } = await fixture(
    t,
    ({ reply }) => reply({}),
    { catalog: CATALOG }
  );
  const before = await client.listTools();
  await writeFile(schemaFile, "invalid fixture schema");
  const after = await client.listTools();
  assert.deepEqual(after.tools, before.tools);
  const result = await call(client, "codex_fs_readFile", { path: "/x" });
  assert.equal(result.error, true);
  assert.equal(requests.length, 0);
});

test("watch logging respects stdout backpressure and SDK closure releases sockets and drains", async (t) => {
  const { socket, connections } = await fixture(t, ({ reply }) => reply({}));
  const previousSocket = process.env.CODEX_APP_SERVER_SOCKET;
  process.env.CODEX_APP_SERVER_SOCKET = socket;
  let finishWrite;
  const stdin = new PassThrough();
  const stdout = new Writable({
    highWaterMark: 64,
    write(_bytes, _encoding, done) {
      finishWrite = done;
    }
  });
  const server = new Server(
    { name: "fixture", version: "1" },
    { capabilities: { logging: {} } }
  );
  const native = createNativeSessions(server);
  const transport = new SessionTransport(native.close, stdin, stdout, {
    maxBufferSize: 1024
  });
  t.after(async () => {
    native.close();
    await server.close();
    stdin.destroy();
    stdout.destroy();
    if (previousSocket === undefined) {
      delete process.env.CODEX_APP_SERVER_SOCKET;
    } else {
      process.env.CODEX_APP_SERVER_SOCKET = previousSocket;
    }
  });
  await server.connect(transport);
  await native.request("fs/watch", {
    path: "/fixture",
    watchId: "backpressure"
  });
  const [owner] = connections;
  const note = JSON.stringify({
    method: "fs/changed",
    params: { paths: ["/fixture"], watchId: "backpressure" }
  });
  for (let count = 0; count < 200; count += 1) {
    owner.send(note);
  }
  await t.waitFor(() => assert.equal(stdout.listenerCount("drain"), 1));
  const buffered = stdout.writableLength;
  await delay(50);
  assert.equal(stdout.writableLength, buffered);
  assert.equal(stdout.listenerCount("drain"), 1);
  stdin.write(Buffer.alloc(1025));
  await t.waitFor(() => assert.equal(connections.size, 0));
  assert.equal(stdout.listenerCount("drain"), 0);
  assert.equal(stdin.readableEnded, false);
  await assert.rejects(
    native.request("fs/watch", { path: "/fixture", watchId: "closed" }),
    /closed/u
  );
  finishWrite();
});

/**
 * Run the packaged monitor against the same fixture socket and decode line events.
 */
const monitor = (context, socket, args) => {
  const child = spawn(process.execPath, [monitorPath, ...args], {
    env: {
      ...process.env,
      CODEX_APP_SERVER_MONITOR_STATUSES: "",
      CODEX_APP_SERVER_MONITOR_THREADS: "",
      CODEX_APP_SERVER_SOCKET: socket
    },
    stdio: ["ignore", "pipe", "pipe"]
  });
  const events = [];
  let buffered = "";
  child.stdout.on("data", (bytes) => {
    buffered += bytes.toString();
    const lines = buffered.split("\n");
    buffered = lines.pop();
    events.push(...lines.filter(Boolean).map((line) => JSON.parse(line)));
  });
  const exited = once(child, "exit");
  context.after(async () => {
    if (child.exitCode === null && child.signalCode === null) {
      child.kill("SIGTERM");
    }
    await exited;
  });
  return { child, events, exited };
};

test("packaged monitor watches configured native transitions and classifies terminal failures", async (t) => {
  let status = "active";
  let turn = { id: "initial", status: "inProgress" };
  const { connections, requests, socket } = await fixture(
    t,
    ({ message, reply }) => {
      reply(
        message.method === "thread/read"
          ? { thread: { status: { type: status } } }
          : { data: [turn] }
      );
    }
  );
  const { child, events, exited } = monitor(t, socket, ["--thread", "watched"]);
  await t.waitFor(() => assert.equal(events[0]?.status, "active"), {
    timeout: 2000
  });
  const notify = (params) => {
    for (const ws of connections) {
      ws.send(JSON.stringify({ method: "thread/status/changed", params }));
    }
  };
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        id: "server-status",
        method: "thread/status/changed",
        params: { status: { type: "systemError" }, threadId: "watched" }
      })
    );
  }
  await t.waitFor(
    () =>
      assert.ok(
        requests.some(
          (request) =>
            request.id === "server-status" && request.error?.code === -32_601
        )
      ),
    { timeout: 2000 }
  );
  assert.equal(events.length, 1);
  notify({ status: { type: "idle" }, threadId: "other" });
  status = "idle";
  turn = {
    error: { message: "fixture failure" },
    id: "initial",
    status: "failed"
  };
  notify({ status: { type: "idle" }, threadId: "watched" });
  await t.waitFor(() => assert.equal(events.length, 3), { timeout: 2000 });
  notify({ status: { type: "idle" }, threadId: "watched" });
  notify({ status: { type: "notLoaded" }, threadId: "watched" });
  notify({ status: { type: "systemError" }, threadId: "watched" });
  await t.waitFor(() => assert.equal(events.length, 5), { timeout: 2000 });
  assert.deepEqual(
    events.map((event) => event.status),
    ["active", "failed", "idle", "notLoaded", "systemError"]
  );
  assert.equal(events[1].turnId, "initial");
  assert.ok(events.every((event) => event.threadId === "watched"));
  assert.ok(
    requests.every(
      (request) =>
        request.error ||
        ["initialize", "thread/read", "thread/turns/list"].includes(
          request.method
        )
    )
  );
  child.kill("SIGTERM");
  assert.deepEqual(await exited, [0, null]);
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
});

test("monitor status filters and startup deadlines preserve read-only behavior", async (t) => {
  const { connections, requests, socket } = await fixture(t, ({ reply }) =>
    reply({ thread: { status: { type: "idle" }, turns: [] } })
  );
  const { events, exited } = monitor(t, socket, [
    "--thread",
    "watched",
    "--status",
    "systemError",
    "--timeout-seconds",
    "0.4"
  ]);
  await t.waitFor(
    () =>
      assert.ok(requests.some((request) => request.method === "thread/read")),
    { timeout: 2000 }
  );
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        method: "thread/status/changed",
        params: { status: { type: "systemError" }, threadId: "watched" }
      })
    );
  }
  assert.deepEqual(await exited, [0, null]);
  assert.deepEqual(
    events.map((event) => event.status),
    ["systemError"]
  );
});

test("wildcard monitor snapshots every loaded page and reports distinct turn errors without unchanged events", async (t) => {
  const error = { code: "model_not_found", message: "Fixture model is absent" };
  const { connections, requests, socket } = await fixture(
    t,
    ({ message, reply }) => {
      if (message.method === "thread/loaded/list") {
        reply(
          message.params.cursor
            ? { data: ["second", "third"], nextCursor: null }
            : { data: ["first"], nextCursor: "next-page" }
        );
      } else if (message.method === "thread/read") {
        reply({
          thread: {
            status: {
              type: message.params.threadId === "third" ? "systemError" : "idle"
            }
          }
        });
      } else {
        reply({
          data:
            message.params.threadId === "second"
              ? [{ error, id: "initial-failure", status: "failed" }]
              : []
        });
      }
    }
  );
  const { child, events, exited } = monitor(t, socket, [
    "--thread",
    "*",
    "--status",
    "idle",
    "--status",
    "failed",
    "--status",
    "systemError"
  ]);
  await t.waitFor(() => assert.equal(events.length, 4), { timeout: 2000 });
  assert.deepEqual(
    requests
      .filter((request) => request.method === "thread/loaded/list")
      .map((request) => request.params),
    [{}, { cursor: "next-page" }]
  );
  assert.deepEqual(
    events
      .filter((event) => event.event === "threadStatus")
      .map((event) => event.threadId),
    ["first", "second", "third"]
  );
  const [owner] = connections;
  for (const threadId of ["first", "second", "third"]) {
    owner.send(
      JSON.stringify({
        method: "thread/status/changed",
        params: {
          status: { type: threadId === "third" ? "systemError" : "idle" },
          threadId
        }
      })
    );
  }
  await delay(50);
  assert.equal(events.length, 4);
  for (const turnId of [
    "later-failure",
    "consecutive-failure",
    "consecutive-failure"
  ]) {
    owner.send(
      JSON.stringify({
        method: "error",
        params: { error, threadId: "second", turnId, willRetry: false }
      })
    );
  }
  await t.waitFor(() => assert.equal(events.length, 6), { timeout: 2000 });
  assert.deepEqual(
    events
      .filter((event) => event.event === "turnError")
      .map((event) => event.turnId),
    ["initial-failure", "later-failure", "consecutive-failure"]
  );
  assert.ok(
    events
      .filter((event) => event.event === "turnError")
      .every((event) => event.error.code === "model_not_found")
  );
  child.kill("SIGTERM");
  assert.deepEqual(await exited, [0, null]);
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
});

test("monitor deduplicates snapshot failures across an older startup notification backlog", async (t) => {
  const error = { code: "model_not_found", message: "Fixture model is absent" };
  let injected = false;
  const { connections, socket } = await fixture(
    t,
    ({ message, reply, connections: targets }) => {
      if (message.method === "thread/turns/list") {
        if (!injected) {
          injected = true;
          const [owner] = targets;
          for (const id of ["older", "newer"]) {
            owner.send(
              JSON.stringify({
                method: "turn/completed",
                params: {
                  threadId: "watched",
                  turn: { error, id, status: "failed" }
                }
              })
            );
          }
        }
        reply({ data: [{ error, id: "newer", status: "failed" }] });
      } else {
        reply({ thread: { status: "idle" } });
      }
    }
  );
  const { child, events, exited } = monitor(t, socket, ["--thread", "watched"]);
  await t.waitFor(() => assert.equal(events.length, 3));
  assert.deepEqual(
    events
      .filter((event) => event.event === "turnError")
      .map((event) => event.turnId),
    ["newer", "older"]
  );
  const [owner] = connections;
  for (const id of ["newer", "future", "newer"]) {
    owner.send(
      JSON.stringify({
        method: "turn/completed",
        params: { threadId: "watched", turn: { error, id, status: "failed" } }
      })
    );
  }
  owner.send(
    JSON.stringify({
      method: "thread/status/changed",
      params: { status: "idle", threadId: "watched" }
    })
  );
  await t.waitFor(() => assert.equal(events.length, 4));
  await delay(50);
  assert.deepEqual(
    events
      .filter((event) => event.event === "turnError")
      .map((event) => event.turnId),
    ["newer", "older", "future"]
  );
  child.kill("SIGTERM");
  assert.deepEqual(await exited, [0, null]);
  await t.waitFor(() => assert.equal(connections.size, 0));
});

test("monitor backpressure stops notification work and cancellation releases both read-only sockets", async (t) => {
  const { connections, requests, socket } = await fixture(
    t,
    ({ message, reply }) => {
      reply(
        message.method === "thread/read"
          ? { thread: { status: "active" } }
          : { data: [] }
      );
    }
  );
  const previousSocket = process.env.CODEX_APP_SERVER_SOCKET;
  process.env.CODEX_APP_SERVER_SOCKET = socket;
  const stop = new AbortController();
  const events = [];
  const done = (async () => {
    try {
      await watchThreads({
        emit: async (event, signal) => {
          events.push(event);
          await once(signal, "abort");
        },
        signal: stop.signal,
        statuses: ["active", "idle", "failed", "systemError"],
        threadIds: ["backpressure"]
      });
    } catch (error) {
      assert.equal(stop.signal.aborted, true, error.message);
    }
  })();
  t.after(async () => {
    stop.abort();
    await done;
    if (previousSocket === undefined) {
      delete process.env.CODEX_APP_SERVER_SOCKET;
    } else {
      process.env.CODEX_APP_SERVER_SOCKET = previousSocket;
    }
  });
  await t.waitFor(() => assert.equal(events.length, 1));
  assert.equal(connections.size, 2);
  const reads = requests.length;
  const [owner] = connections;
  for (let count = 0; count < 200; count += 1) {
    owner.send(
      JSON.stringify({
        method: "thread/status/changed",
        params: { status: "idle", threadId: "backpressure" }
      })
    );
  }
  await delay(50);
  assert.equal(events.length, 1);
  assert.equal(requests.length, reads);
  stop.abort();
  await done;
  await t.waitFor(() => assert.equal(connections.size, 0));
  assert.equal(events.length, 1);
});

test("monitor reports socket loss as a transport systemError and exits without reconnecting", async (t) => {
  const { connections, socket } = await fixture(t, ({ reply }) =>
    reply({ thread: { status: { type: "idle" }, turns: [] } })
  );
  const { events, exited } = monitor(t, socket, ["--thread", "watched"]);
  await t.waitFor(() => assert.equal(events.length, 1), { timeout: 2000 });
  for (const ws of connections) {
    ws.close();
  }
  assert.deepEqual(await exited, [1, null]);
  assert.equal(events.at(-1).event, "transport");
  assert.equal(events.at(-1).status, "systemError");
  assert.equal(events.at(-1).threadId, null);
});

test("monitor validates configuration before connecting and is disabled without watched threads", async (t) => {
  const { requests, socket } = await fixture(t, ({ reply }) => reply({}));
  const invalid = monitor(t, socket, [
    "--thread",
    "watched",
    "--status",
    "invalid"
  ]);
  assert.deepEqual(await invalid.exited, [1, null]);
  const disabled = monitor(t, socket, []);
  assert.deepEqual(await disabled.exited, [0, null]);
  assert.equal(requests.length, 0);
});

test("monitor deadline cancels a stalled initialize handshake and closes its socket", async (t) => {
  const { connections, socket } = await fixture(t, () => {}, {
    failInitialize: true
  });
  const { events, exited } = monitor(t, socket, [
    "--thread",
    "watched",
    "--timeout-seconds",
    "0.1"
  ]);
  assert.deepEqual(await exited, [0, null]);
  assert.deepEqual(events, []);
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
});

test("monitor isolates failed thread inspections while other selected threads keep emitting", async (t) => {
  const { socket, connections, requests } = await fixture(
    t,
    ({ message, reply, ws }) => {
      if (message.params?.threadId === "missing") {
        ws.send(
          JSON.stringify({
            error: { code: -32_000, message: "Thread unavailable" },
            id: message.id
          })
        );
      } else if (message.method === "thread/turns/list") {
        reply({ data: [] });
      } else {
        reply({ thread: { status: { type: "idle" } } });
      }
    }
  );
  const { child, events, exited } = monitor(t, socket, [
    "--thread",
    "missing",
    "--thread",
    "watched"
  ]);
  await t.waitFor(() => assert.equal(events.length, 2), { timeout: 2000 });
  assert.ok(
    events.some(
      (event) => event.event === "inspection" && event.threadId === "missing"
    )
  );
  for (const ws of connections) {
    ws.send(
      JSON.stringify({
        method: "thread/status/changed",
        params: { status: { type: "active" }, threadId: "watched" }
      })
    );
  }
  await t.waitFor(() => assert.equal(events.at(-1).status, "active"), {
    timeout: 2000
  });
  assert.ok(events.every((event) => event.event !== "transport"));
  for (const entry of requests.filter(
    (request) => request.method === "thread/turns/list"
  )) {
    assert.deepEqual(entry.params, {
      itemsView: "notLoaded",
      limit: 1,
      sortDirection: "desc",
      threadId: entry.params.threadId
    });
  }
  assert.ok(
    requests
      .filter((request) => request.method === "thread/read")
      .every((request) => request.params.includeTurns === false)
  );
  child.kill("SIGTERM");
  assert.deepEqual(await exited, [0, null]);
});

test("monitor drops startup notifications after cancellation", async (t) => {
  const { socket, requests, connections } = await fixture(
    t,
    ({ message, connections: targets }) => {
      if (message.method === "thread/turns/list") {
        for (const type of ["active", "idle"]) {
          for (const ws of targets) {
            ws.send(
              JSON.stringify({
                method: "thread/status/changed",
                params: { status: { type }, threadId: "watched" }
              })
            );
          }
        }
      }
    }
  );
  const { child, events, exited } = monitor(t, socket, ["--thread", "watched"]);
  await t.waitFor(
    () =>
      assert.ok(
        requests.some((request) => request.method === "thread/turns/list")
      ),
    { timeout: 2000 }
  );
  child.kill("SIGTERM");
  assert.deepEqual(await exited, [0, null]);
  assert.deepEqual(events, []);
  assert.deepEqual(
    requests.map((request) => request.method),
    ["initialize", "initialize", "thread/turns/list"]
  );
  await t.waitFor(() => assert.equal(connections.size, 0), { timeout: 2000 });
});
