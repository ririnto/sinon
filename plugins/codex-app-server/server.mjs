#!/usr/bin/env node
import { on, once } from "node:events";
import { homedir } from "node:os";
import path from "node:path";

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { WebSocket } from "ws";
import { z } from "zod";

/**
 * Expose the running Codex app-server as a stdio MCP server.
 * Each tool call opens one control-socket connection, sends only the named requests, and closes it.
 * It starts no daemon and schedules nothing.
 * Archive, unarchive, and delete threads with the native `codex archive|unarchive|delete --remote unix://` commands.
 */

const REQUEST_TIMEOUT_MS = 300_000;
/**
 * Reserve reset-credit consumption for a separate direct human instruction.
 */
const BLOCKED_METHODS = new Set(["account/rateLimitResetCredit/consume"]);
const READ_ONLY = { openWorldHint: false, readOnlyHint: true };

/**
 * Return the control socket path from the environment or from CODEX_HOME.
 */
const defaultSocket = () =>
  process.env.CODEX_APP_SERVER_SOCKET ||
  path.join(
    process.env.CODEX_HOME || path.join(homedir(), ".codex"),
    "app-server-control",
    "app-server-control.sock"
  );

/**
 * Connect over the Unix control socket, perform the initialize handshake, and return a JSON-RPC client.
 */
const openClient = async () => {
  const ws = new WebSocket(`ws+unix://${defaultSocket()}:/`, {
    maxPayload: 0
  });
  const events = new EventTarget();
  const emit = (name, detail) =>
    events.dispatchEvent(new CustomEvent(name, { detail }));
  let nextId = 0;
  ws.on("message", (data) => {
    let message;
    try {
      message = JSON.parse(data.toString());
    } catch {
      return;
    }
    if ("id" in message && ("result" in message || "error" in message)) {
      emit(`response:${message.id}`, message);
      return;
    }
    if ("id" in message && "method" in message) {
      ws.send(
        JSON.stringify({
          error: { code: -32_601, message: "not handled by this client" },
          id: message.id
        })
      );
    }
    emit("notification", message);
  });
  ws.on("close", () => emit("close"));
  await once(ws, "open");
  const request = async (method, params) => {
    nextId += 1;
    const id = nextId;
    const signal = AbortSignal.timeout(REQUEST_TIMEOUT_MS);
    const response = once(events, `response:${id}`, { signal });
    ws.send(
      JSON.stringify({
        id,
        method,
        ...(params === undefined ? {} : { params })
      })
    );
    const [{ detail: message }] = await response;
    if (message.error) {
      throw new Error(`${method}: ${JSON.stringify(message.error)}`);
    }
    return message.result;
  };
  await request("initialize", {
    capabilities: { experimentalApi: true },
    clientInfo: {
      name: "orchestrator-codex-app-server-mcp",
      title: "Orchestrator MCP client",
      version: "1"
    }
  });
  ws.send(JSON.stringify({ method: "initialized" }));
  return { close: () => ws.close(), events, request };
};

/**
 * Return the role and text of a user or agent message item, or null for other items.
 */
const messageText = (item) => {
  if (item.type === "userMessage") {
    return {
      role: "USER",
      text: (item.content ?? [])
        .filter((part) => part.type === "text")
        .map((part) => part.text ?? "")
        .join("\n")
    };
  }
  if (item.type === "agentMessage") {
    return { role: "AGENT", text: item.text ?? "" };
  }
  return null;
};

/**
 * Wait for the turn's completion and collect its final agent message until the deadline.
 */
const awaitTurn = async (client, turnId, waitSeconds) => {
  const result = { finalMessage: null, status: "waitTimedOut", turnId };
  const closed = new AbortController();
  client.events.addEventListener("close", () => closed.abort(), { once: true });
  const signal = AbortSignal.any([
    AbortSignal.timeout(waitSeconds * 1000),
    closed.signal
  ]);
  try {
    for await (const [{ detail: note }] of on(client.events, "notification", {
      signal
    })) {
      const params = note.params ?? {};
      if (
        note.method === "item/completed" &&
        (params.turnId ?? turnId) === turnId &&
        params.item?.type === "agentMessage"
      ) {
        result.finalMessage = params.item.text ?? null;
      }
      if (note.method === "turn/completed" && params.turn?.id === turnId) {
        return { ...result, status: params.turn.status };
      }
    }
  } catch (error) {
    if (error.name !== "AbortError") {
      throw error;
    }
  }
  return closed.signal.aborted
    ? { ...result, status: "connectionClosed" }
    : result;
};

/**
 * Resume a thread, start one turn, and optionally wait for completion and the final agent message.
 */
const sendTurn = async (client, threadId, text, waitSeconds = 0) => {
  await client.request("thread/resume", { excludeTurns: true, threadId });
  const started = await client.request("turn/start", {
    input: [{ text, type: "text" }],
    threadId
  });
  const turnId = started.turn.id;
  if (waitSeconds <= 0) {
    return { finalMessage: null, status: "started", turnId };
  }
  return awaitTurn(client, turnId, waitSeconds);
};

/**
 * Wrap a tool handler so it runs with its own app-server connection and returns MCP text content.
 */
const withClient = (handler) => async (args) => {
  let client;
  try {
    client = await openClient();
    const value = await handler(client, args);
    return {
      content: [{ text: JSON.stringify(value ?? null, null, 1), type: "text" }]
    };
  } catch (error) {
    return { content: [{ text: error.message, type: "text" }], isError: true };
  } finally {
    client?.close();
  }
};

const server = new McpServer(
  { name: "codex-app-server", version: "1" },
  {
    instructions: [
      "Use the running Codex app-server through its Unix control socket.",
      "This MCP server starts no daemon and schedules nothing.",
      "Server-initiated requests, including approvals, receive a refusal from this client.",
      "Do not use these tools to poll for status or replace native Codex delegation.",
      "Read a thread with codex_threads and codex_tail before steering it.",
      "Keep one writer per thread.",
      "Do not start a turn when its last turn is in progress or another host holds the thread.",
      "An active-writer error means another host holds the thread.",
      "Find that host through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex) and let it release the thread.",
      "Do not delete writer locks or force a second writer.",
      "Use codex_start and codex_send only under a direct human instruction or an assigned contract authorizing that steering.",
      "Apply the same authorization and writer rules to raw mutating requests through codex_call.",
      "codex_call refuses account/rateLimitResetCredit/consume.",
      "Reset-credit consumption requires a separate direct human instruction outside this tool.",
      "Only the Orchestrator reads usage with account/rateLimits/read.",
      "Do not delegate usage or monitoring duties.",
      "codex_mcp_tool calls a configured MCP tool inside a temporary ephemeral thread and may perform writes.",
      "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup, with an authorized thread ID or name.",
      "For a custom socket, use --remote unix://PATH with the same socket as CODEX_APP_SERVER_SOCKET.",
      "Archive only after pending work ends.",
      "Delete only when the transcript is no longer needed.",
      "Generate unfamiliar protocol schemas with codex app-server generate-json-schema --out DIR before using codex_call.",
      "Keep thread, Page, and session IDs, credentials, and raw output out of tracked files.",
      "Summarize results."
    ].join(" ")
  }
);

server.registerTool(
  "codex_threads",
  {
    annotations: READ_ONLY,
    description:
      "List Codex threads newest first with ID, status, update time, working directory, and title. " +
      "Read this list and codex_tail before authorized steering; keep one writer per thread. " +
      "Do not start a turn while its last turn is in progress or another host holds it. " +
      "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
      "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
    inputSchema: {
      limit: z
        .number()
        .int()
        .optional()
        .describe(
          "Maximum threads in the newest-first page; defaults to 30. The daemon validates its supported limit."
        ),
      search: z
        .string()
        .optional()
        .describe(
          "Optional thread search text passed as searchTerm; omitted or empty means no search filter."
        )
    }
  },
  withClient(async (client, { limit = 30, search }) => {
    const page = await client.request("thread/list", {
      limit,
      sortKey: "updated_at",
      ...(search ? { searchTerm: search } : {})
    });
    return (page.data ?? []).map((thread) => ({
      cwd: thread.cwd ?? "",
      id: thread.id,
      status: thread.status?.type ?? thread.status,
      title: (thread.name || thread.preview || "").slice(0, 80),
      updatedAt: thread.updatedAt
    }));
  })
);

server.registerTool(
  "codex_tail",
  {
    annotations: READ_ONLY,
    description:
      "Read the last turns' user and agent messages of one Codex thread without resuming it. " +
      "Use codex_threads and this tool before authorized steering. " +
      "Do not send a new turn when the last turn is in progress or another host holds the thread. " +
      "Keep one writer per thread and use native codex archive|unarchive|delete --remote unix:// for cleanup. " +
      "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
    inputSchema: {
      thread_id: z
        .string()
        .describe(
          "Thread ID from codex_threads to inspect before steering; keep the ID out of tracked files."
        ),
      turns: z
        .number()
        .int()
        .optional()
        .describe(
          "Trailing turns to return; defaults to 2. Uses slice(-turns): 0 returns all; negative values omit leading turns. Includes user and agent messages only."
        )
    }
  },
  withClient(async (client, { thread_id: threadId, turns = 2 }) => {
    const { thread } = await client.request("thread/read", {
      includeTurns: true,
      threadId
    });
    return {
      name: thread.name,
      status: thread.status,
      turns: (thread.turns ?? []).slice(-turns).map((turn) => ({
        id: turn.id,
        messages: (turn.items ?? []).map(messageText).filter(Boolean),
        status: turn.status
      }))
    };
  })
);

server.registerTool(
  "codex_start",
  {
    description:
      "Start a Codex thread in a working directory, optionally naming it and sending its first turn. " +
      "Use only under a direct human instruction or assigned contract authorizing this steering. " +
      "Read existing threads with codex_threads/codex_tail before choosing whether to create or steer a thread. " +
      "Keep one writer per thread; never start a turn while its last turn is in progress or another host holds it. " +
      "Use native codex archive|unarchive|delete --remote unix:// for cleanup after pending work ends. " +
      "Delete only when the transcript is no longer needed.",
    inputSchema: {
      cwd: z
        .string()
        .describe(
          "Authorized working directory for the new thread on the daemon host."
        ),
      name: z
        .string()
        .optional()
        .describe("Optional thread name; set after creation when nonempty."),
      text: z
        .string()
        .optional()
        .describe(
          "Optional authorized first-turn text; nonempty text starts a turn without waiting for completion."
        )
    }
  },
  withClient(async (client, { cwd, name, text }) => {
    const { thread } = await client.request("thread/start", { cwd });
    if (name) {
      await client.request("thread/name/set", { name, threadId: thread.id });
    }
    const turn = text ? await sendTurn(client, thread.id, text) : null;
    return { threadId: thread.id, turn };
  })
);

server.registerTool(
  "codex_send",
  {
    description:
      "Resume a Codex thread and start one turn; optionally wait for completion and its final agent message. " +
      "Use only under a direct human instruction or assigned contract authorizing steering of this thread. " +
      "Read it with codex_threads and codex_tail first; keep one writer per thread. " +
      "Do not start a turn on a thread whose last turn is in progress or that another host holds. " +
      "On an active-writer error, identify the holder through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex). " +
      "Let that host release it; never delete locks or force a second writer. " +
      "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
      "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
    inputSchema: {
      text: z
        .string()
        .describe(
          "Authorized next-turn text for the inspected thread; do not use this tool to schedule work or poll status."
        ),
      thread_id: z
        .string()
        .describe(
          "Authorized thread ID inspected with codex_threads/codex_tail; its last turn must be finished and its writer available."
        ),
      wait_seconds: z
        .number()
        .optional()
        .describe(
          "Seconds to wait on this connection for completion and the final agent message; defaults to 0 (return started immediately)."
        )
    }
  },
  withClient(
    (client, { text, thread_id: threadId, wait_seconds: waitSeconds }) =>
      sendTurn(client, threadId, text, waitSeconds)
  )
);

server.registerTool(
  "codex_mcp_tool",
  {
    description:
      "Call an MCP tool configured in Codex inside a temporary ephemeral thread rooted at this server's working directory. " +
      "This creates a thread without starting an agent turn; the called tool may read or write. " +
      "Use only a configured server/tool and arguments authorized for the requested operation. " +
      "For example, codex_apps exposes Page tools as chatgpt_space.*. " +
      "Keep Page/session IDs, credentials, and raw output out of tracked files.",
    inputSchema: {
      arguments: z
        .record(z.string(), z.unknown())
        .optional()
        .describe(
          "Arguments for the configured MCP tool; defaults to an empty object. Verify authorization before any write."
        ),
      server: z
        .string()
        .describe(
          "Configured Codex MCP server name, such as codex_apps; resolved by the running daemon."
        ),
      tool: z
        .string()
        .describe(
          "Exact tool name exposed by that MCP server, such as chatgpt_space.read_page; inspect its schema before calling."
        )
    }
  },
  withClient(
    async (
      client,
      { arguments: toolArguments = {}, server: serverName, tool }
    ) => {
      const { thread } = await client.request("thread/start", {
        cwd: process.cwd(),
        ephemeral: true
      });
      const result = await client.request("mcpServer/tool/call", {
        arguments: toolArguments,
        server: serverName,
        threadId: thread.id,
        tool
      });
      if (result.isError) {
        throw new TypeError(JSON.stringify(result.content ?? result));
      }
      return result.structuredContent ?? result.content ?? result;
    }
  )
);

server.registerTool(
  "codex_call",
  {
    description:
      "Send one raw app-server JSON-RPC request and return its result; this can mutate daemon state. " +
      "Inspect unfamiliar methods with codex app-server generate-json-schema --out DIR before use. " +
      "Read threads with codex_threads/codex_tail before authorized steering and keep one writer per thread. " +
      "Do not start a turn while its last turn is in progress or another host holds it. " +
      "On an active-writer error, let the holder of thread-writer-locks/THREAD_ID.lock under CODEX_HOME release it. " +
      "Never delete writer locks or force another writer. " +
      "This tool refuses account/rateLimitResetCredit/consume before connecting; consumption needs a separate direct human instruction outside this tool. " +
      "Only the Orchestrator reads usage with account/rateLimits/read; do not delegate usage or monitoring duties. " +
      "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
      "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
    inputSchema: {
      method: z
        .string()
        .describe(
          "Exact app-server method. account/rateLimitResetCredit/consume is refused; usage reads belong to the Orchestrator. Mutations require authorization."
        ),
      params: z
        .record(z.string(), z.unknown())
        .optional()
        .describe(
          "Method parameters matching the installed daemon's schema; omitted means no params field. Preserve thread writer and authorization rules."
        )
    }
  },
  (args) =>
    BLOCKED_METHODS.has(args.method)
      ? {
          content: [
            {
              text: `${args.method} requires a separate direct human instruction`,
              type: "text"
            }
          ],
          isError: true
        }
      : withClient((client, { method, params }) =>
          client.request(method, params)
        )(args)
);

await server.connect(new StdioServerTransport());
