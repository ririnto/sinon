#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema
} from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";

import { registerCatalogTools } from "./lib/catalog.mjs";
import { registerMcpTool } from "./lib/mcp-tool.mjs";
import { createNativeSessions } from "./lib/native-session.mjs";
import { refusal } from "./lib/policy.mjs";
import { closeQueues, registerQueueTools } from "./lib/queue.mjs";
import { SessionTransport } from "./lib/session-transport.mjs";
import { registerThreadAdminTools } from "./lib/thread-admin.mjs";
import { registerThreadTools } from "./lib/thread-tools.mjs";
import { registerTurnTools } from "./lib/turn-tools.mjs";

/**
 * Expose the running Codex app-server as a stdio MCP server.
 * Immediate calls close their own connection; authorized queued messages retain a bounded delivery connection.
 * It starts no daemon and retains no persistent message queue.
 * Archive, unarchive, and delete threads with the native `codex archive|unarchive|delete --remote unix://` commands.
 */

const server = new Server(
  { name: "codex-app-server", version: "1" },
  {
    capabilities: { logging: {}, tools: { listChanged: true } },
    instructions: [
      "Use the running Codex app-server through its Unix control socket.",
      "This MCP server starts no daemon; authorized messages may queue in memory until active work ends.",
      "Server-initiated requests, including approvals, receive a refusal from this client.",
      "Do not use these tools to poll for status or replace native Codex delegation.",
      "Read a thread with codex_threads and codex_tail before steering it.",
      "codex_send queues active threads; codex_steer queues by default and explicit steering requires verified provider support.",
      "codex_queue reports delivery receipts.",
      "Keep one writer per thread.",
      "Do not start a turn when its last turn is in progress or another host holds the thread.",
      "An active-writer error means another host holds the thread.",
      "Find that host through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex) and let it release the thread.",
      "Do not delete writer locks or force a second writer.",
      "Use codex_start, codex_send, codex_steer, codex_interrupt, codex_fork, codex_rename, codex_goal, and codex_compact only under a direct human instruction or an assigned contract authorizing that steering.",
      "Every native ClientRequest method has its own schema-derived tool; use host tool search to load it.",
      "Native mutating tools require the same authorization and writer rules as curated adapters.",
      "Credit consumption and thread archive, unarchive, and delete are refusal-only tools.",
      "codex_methods lists the installed Codex's methods and params schemas when its schema generation succeeds.",
      "Reset-credit consumption requires a separate direct human instruction outside this tool.",
      "Only the Orchestrator reads usage with account/rateLimits/read.",
      "Do not delegate usage or monitoring duties.",
      "codex_mcp_tool calls a configured MCP tool inside a temporary ephemeral thread and may perform writes.",
      "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup, with an authorized thread ID or name.",
      "For a custom socket, use --remote unix://PATH with the same socket as CODEX_APP_SERVER_SOCKET.",
      "Archive only after pending work ends.",
      "Delete only when the transcript is no longer needed.",
      "codex_methods refreshes native tools and provides unfamiliar parameter schemas.",
      "Keep thread, Page, and session IDs, credentials, and raw output out of tracked files.",
      "Summarize results."
    ].join(" ")
  }
);

const registry = new Map();
registry.registerTool = (name, config, handler) => {
  if (registry.has(name)) {
    throw new Error(`Duplicated curated tool: ${name}`);
  }
  const schema = z.object(config.inputSchema ?? {});
  registry.set(name, {
    definition: {
      ...config,
      inputSchema: z.toJSONSchema(schema, { io: "input" }),
      name
    },
    handler: async (args, extra) =>
      handler(await schema.parseAsync(args ?? {}), extra)
  });
};
registerThreadTools(registry);
registerTurnTools(registry);
registerThreadAdminTools(registry);
registerMcpTool(registry);
registerQueueTools(registry);
const native = createNativeSessions(server);
const refresh = await registerCatalogTools(registry, server, native.request);
server.setRequestHandler(ListToolsRequestSchema, async () => {
  await refresh();
  return { tools: [...registry.values()].map((tool) => tool.definition) };
});
server.setRequestHandler(CallToolRequestSchema, async (request, extra) => {
  try {
    const selected = registry.get(request.params.name);
    if (selected?.method && !refusal(selected.method)) {
      await refresh({ regenerate: false });
    }
    const tool = registry.get(request.params.name);
    if (!tool) {
      throw new Error(`Unknown tool: ${request.params.name}`);
    }
    extra.signal.throwIfAborted();
    return await tool.handler(request.params.arguments ?? {}, extra);
  } catch (error) {
    return { content: [{ text: error.message, type: "text" }], isError: true };
  }
});

const close = () => {
  closeQueues();
  native.close();
};
const stop = async () => {
  await server.close();
};
process.stdin.once("end", stop);
process.stdin.once("close", stop);
process.stdin.once("error", stop);
process.stdout.once("error", stop);
await server.connect(new SessionTransport(close));
