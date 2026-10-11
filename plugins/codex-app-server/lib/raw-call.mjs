import { z } from "zod";

import { withClient } from "./connection.mjs";

/**
 * Reserve reset-credit consumption for a separate direct human instruction.
 */
const BLOCKED_METHODS = new Set(["account/rateLimitResetCredit/consume"]);

/**
 * Register raw app-server access with reset-credit consumption refused before connecting.
 */
export const registerRawCall = (server) => {
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
};
