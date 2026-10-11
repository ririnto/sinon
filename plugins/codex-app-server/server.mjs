#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

import { registerMcpTool } from "./lib/mcp-tool.mjs";
import { registerRawCall } from "./lib/raw-call.mjs";
import { registerThreadTools } from "./lib/thread-tools.mjs";

/**
 * Expose the running Codex app-server as a stdio MCP server.
 * Each tool call opens one control-socket connection, sends only the named requests, and closes it.
 * It starts no daemon and schedules nothing.
 * Archive, unarchive, and delete threads with the native `codex archive|unarchive|delete --remote unix://` commands.
 */

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

registerThreadTools(server);
registerMcpTool(server);
registerRawCall(server);

await server.connect(new StdioServerTransport());
