import { z } from "zod";

import { withClient } from "./connection.mjs";
import { refusal } from "./policy.mjs";
import { validateRequest } from "./schema.mjs";

/**
 * Register raw app-server access with reset-credit consumption and thread cleanup refused before connecting.
 */
export const registerRawCall = (server) => {
  server.registerTool(
    "codex_call",
    {
      description:
        "Send one raw app-server JSON-RPC request and return its result; this can mutate daemon state. " +
        "Validate parameters against the installed CLI's native ClientRequest schema before connecting. " +
        "Read threads with codex_threads/codex_tail before authorized steering and keep one writer per thread. " +
        "Do not start a turn while its last turn is in progress or another host holds it. " +
        "On an active-writer error, let the holder of thread-writer-locks/THREAD_ID.lock under CODEX_HOME release it. " +
        "Never delete writer locks or force another writer. " +
        "This tool refuses account/rateLimitResetCredit/consume before connecting; consumption needs a separate direct human instruction outside this tool. " +
        "It also refuses thread/archive, thread/unarchive, and thread/delete, which run through the native codex CLI. " +
        "Read a method's params schema with codex_methods when it is available. " +
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
          .unknown()
          .optional()
          .describe(
            "Method parameters matching the installed daemon's schema; omitted means no params field. Preserve thread writer and authorization rules."
          )
      }
    },
    async (args) => {
      const reason = refusal(args.method);
      if (reason) {
        return { content: [{ text: reason, type: "text" }], isError: true };
      }
      try {
        await validateRequest(args.method, args.params);
      } catch (error) {
        return {
          content: [{ text: error.message, type: "text" }],
          isError: true
        };
      }
      return withClient((client, { method, params }) =>
        client.request(method, params)
      )(args);
    }
  );
};
