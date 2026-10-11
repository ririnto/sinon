import { z } from "zod";

import { withClient } from "./connection.mjs";

/**
 * Register the configured MCP tool proxy using a temporary ephemeral thread.
 */
export const registerMcpTool = (server) => {
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
};
