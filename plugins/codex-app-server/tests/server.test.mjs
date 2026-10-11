import assert from "node:assert/strict";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
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
  const transport = new StdioClientTransport({
    args: [serverPath],
    command: process.execPath,
    cwd: directory,
    env: { ...process.env, CODEX_APP_SERVER_SOCKET: socket },
    stderr: "pipe"
  });
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

test("lists six self-describing tools and all sources without repairing metadata", async (t) => {
  const { client, requests } = await fixture(t, ({ reply }) => {
    reply({
      data: [{ id: "fixture-thread", preview: "Fixture", source: "exec" }]
    });
  });
  const { tools } = await client.listTools();
  assert.equal(tools.length, 6);
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
