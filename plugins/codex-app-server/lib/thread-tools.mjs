import { z } from "zod";

import { withClient } from "./connection.mjs";
import { enqueueMessage, queuedThread, withThreadWriter } from "./queue.mjs";
import { startTurn } from "./turn.mjs";

const READ_ONLY = { openWorldHint: false, readOnlyHint: true };

const THREAD_SOURCE_KINDS = [
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
 * Register thread inspection and authorized steering tools on the MCP server.
 */
export const registerThreadTools = (server) => {
  server.registerTool(
    "codex_threads",
    {
      annotations: READ_ONLY,
      description:
        "List a newest-first page of non-archived Codex threads from all supported source kinds, including exec and subagents, using the state database without repairing rollout metadata. " +
        "Returns ID, status, update time, working directory, and title; threads absent from the state database are not included. " +
        "Read this list and codex_tail before authorized steering; keep one writer per thread. " +
        "Do not start a turn while its last turn is in progress or another host holds it. " +
        "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
        "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
      inputSchema: {
        limit: z
          .number()
          .int()
          .positive()
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
        sourceKinds: THREAD_SOURCE_KINDS,
        useStateDbOnly: true,
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
          .positive()
          .optional()
          .describe(
            "Positive number of trailing turns to return; defaults to 2. Includes user and agent messages only. Zero and negative counts are rejected."
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
      const turn = text ? await startTurn(client, thread.id, text) : null;
      return { threadId: thread.id, turn };
    })
  );
  server.registerTool(
    "codex_send",
    {
      description:
        "Send authorized text as a fresh turn, queuing it in this MCP process while the thread is active. " +
        "Queued messages return a queue ID; inspect delivery with codex_queue. Idle sends may wait for completion. " +
        "Use only under a direct human instruction or assigned contract authorizing steering of this thread. " +
        "Read it with codex_threads and codex_tail first; keep one writer per thread. " +
        "An active turn is observed without steering or interrupting it; queued delivery waits until it ends. " +
        "The daemon retains writer guards; delivery refusals propagate without retries. " +
        "On an active-writer error, identify the holder through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex). " +
        "Let that host release it; never delete locks or force a second writer. " +
        "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
        "Archive only after pending work ends. Delete only when the transcript is no longer needed.",
      inputSchema: {
        text: z
          .string()
          .describe(
            "Authorized text to start now when idle or queue for a fresh turn after active work ends."
          ),
        thread_id: z
          .string()
          .describe(
            "Authorized thread ID inspected with codex_threads/codex_tail; queue active work and preserve its writer."
          ),
        wait_seconds: z
          .number()
          .optional()
          .describe(
            "Seconds to wait on this connection for completion and the final agent message; defaults to 0 (return started immediately)."
          )
      }
    },
    withThreadWriter(
      async (
        client,
        { text, thread_id: threadId, wait_seconds: waitSeconds },
        release
      ) => {
        if (queuedThread(threadId)) {
          return enqueueMessage(threadId, text, "Existing queue owns delivery");
        }
        const { thread } = await client.request("thread/read", {
          includeTurns: true,
          threadId
        });
        if (
          thread.turns?.at(-1)?.status === "inProgress" ||
          thread.status?.type === "active"
        ) {
          return enqueueMessage(threadId, text);
        }
        await client.request("thread/resume", { excludeTurns: true, threadId });
        return startTurn(client, threadId, text, waitSeconds, release);
      }
    )
  );
};
