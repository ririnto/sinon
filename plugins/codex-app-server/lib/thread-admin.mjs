import { z } from "zod";

import { withClient } from "./connection.mjs";

const READ_ONLY = { openWorldHint: false, readOnlyHint: true };

const AUTHORIZATION =
  "Use only under a direct human instruction or assigned contract authorizing this change. " +
  "Keep one writer per thread and do not mutate a thread whose last turn is in progress or that another host holds. " +
  "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup.";

const THREAD_ID = z
  .string()
  .describe(
    "Authorized thread ID inspected with codex_threads/codex_tail; keep the ID out of tracked files."
  );

const GOAL_STATUSES = [
  "active",
  "paused",
  "blocked",
  "usageLimited",
  "budgetLimited",
  "complete"
];

/**
 * Register thread fork, rename, goal, loaded-list, and compaction tools on the MCP server.
 */
export const registerThreadAdminTools = (server) => {
  server.registerTool(
    "codex_loaded",
    {
      annotations: READ_ONLY,
      description:
        "List a page of thread IDs currently loaded in the running app-server's memory. " +
        "Use it to see which threads are live before steering; codex_threads lists stored threads instead.",
      inputSchema: {
        cursor: z
          .string()
          .optional()
          .describe(
            "Optional nextCursor from a previous page; omitted starts from the beginning."
          ),
        limit: z
          .number()
          .int()
          .positive()
          .optional()
          .describe(
            "Maximum thread IDs per page; omitted uses the daemon default."
          )
      }
    },
    withClient((client, { cursor, limit }) =>
      client.request("thread/loaded/list", {
        ...(cursor ? { cursor } : {}),
        ...(limit ? { limit } : {})
      })
    )
  );
  server.registerTool(
    "codex_fork",
    {
      description:
        `Fork a Codex thread into a new thread with a copy of its history, optionally up to a turn, and optionally name it. ` +
        `The source thread is not modified; the fork starts no turn. ${
          AUTHORIZATION
        }`,
      inputSchema: {
        last_turn_id: z
          .string()
          .optional()
          .describe(
            "Optional turn ID from codex_tail; the fork keeps history up to and including that turn."
          ),
        name: z
          .string()
          .optional()
          .describe("Optional name for the fork; set after creation."),
        thread_id: THREAD_ID
      }
    },
    withClient(
      async (
        client,
        { last_turn_id: lastTurnId, name, thread_id: threadId }
      ) => {
        const forked = await client.request("thread/fork", {
          ...(lastTurnId ? { lastTurnId } : {}),
          excludeTurns: true,
          threadId
        });
        if (name) {
          await client.request("thread/name/set", {
            name,
            threadId: forked.thread.id
          });
        }
        return { threadId: forked.thread.id };
      }
    )
  );
  server.registerTool(
    "codex_rename",
    {
      description: `Set the display name of a Codex thread. ${AUTHORIZATION}`,
      inputSchema: {
        name: z.string().min(1).describe("New nonempty thread name."),
        thread_id: THREAD_ID
      }
    },
    withClient(async (client, { name, thread_id: threadId }) => {
      await client.request("thread/name/set", { name, threadId });
      return { name, threadId };
    })
  );
  server.registerTool(
    "codex_goal",
    {
      description:
        `Read, set, or clear the goal of a Codex thread. ` +
        `The get action is read-only. The set and clear actions mutate the thread without inferring user provenance. ${
          AUTHORIZATION
        }`,
      inputSchema: {
        action: z
          .enum(["get", "set", "clear"])
          .describe(
            "get reads the goal; set updates the fields given below; clear removes the goal."
          ),
        objective: z
          .string()
          .optional()
          .describe(
            "Goal objective text for set; omitted keeps the current one."
          ),
        status: z
          .enum(GOAL_STATUSES)
          .optional()
          .describe("Goal status for set; omitted keeps the current one."),
        thread_id: THREAD_ID,
        token_budget: z
          .number()
          .int()
          .positive()
          .optional()
          .describe("Goal token budget for set; omitted keeps the current one.")
      }
    },
    withClient(
      (
        client,
        { action, objective, status, thread_id: threadId, token_budget: budget }
      ) =>
        action === "set"
          ? client.request("thread/goal/set", {
              ...(objective ? { objective } : {}),
              ...(status ? { status } : {}),
              ...(budget ? { tokenBudget: budget } : {}),
              threadId
            })
          : client.request(`thread/goal/${action}`, { threadId })
    )
  );
  server.registerTool(
    "codex_compact",
    {
      description: `Start context compaction on a Codex thread and return when the daemon accepts it. ${
        AUTHORIZATION
      }`,
      inputSchema: { thread_id: THREAD_ID }
    },
    withClient((client, { thread_id: threadId }) =>
      client.request("thread/compact/start", { threadId })
    )
  );
};
