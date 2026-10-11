import { on } from "node:events";

import { z } from "zod";

import { withClient } from "./connection.mjs";

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
 * Wait for the turn's completion and collect its final agent message until the deadline.
 */
const awaitTurn = async (
  client,
  notifications,
  turnId,
  waitSeconds,
  waiting
) => {
  const result = { finalMessage: null, status: "waitTimedOut", turnId };
  const timer = setTimeout(() => waiting.abort(), waitSeconds * 1000);
  try {
    for await (const [{ detail: note }] of notifications) {
      const params = note.params ?? {};
      if (
        note.method === "item/completed" &&
        (params.turnId ?? turnId) === turnId &&
        params.item?.type === "agentMessage"
      ) {
        result.finalMessage = params.item.text ?? null;
      }
      if (note.method === "turn/completed" && params.turn?.id === turnId) {
        return {
          ...result,
          ...(params.turn.error ? { error: params.turn.error } : {}),
          status: params.turn.status
        };
      }
    }
  } catch (error) {
    if (error.name !== "AbortError") {
      throw error;
    }
  } finally {
    clearTimeout(timer);
  }
  return client.signal.aborted
    ? { ...result, status: "connectionClosed" }
    : result;
};

/**
 * Start one turn with completion notifications buffered before the request is sent.
 */
const startTurn = async (client, threadId, text, waitSeconds = 0) => {
  const waiting = new AbortController();
  const notifications =
    waitSeconds > 0
      ? on(client.events, "notification", {
          signal: AbortSignal.any([waiting.signal, client.signal])
        })
      : null;
  try {
    const started = await client.request("turn/start", {
      input: [{ text, type: "text" }],
      threadId
    });
    const turnId = started.turn.id;
    if (!notifications) {
      return { finalMessage: null, status: "started", turnId };
    }
    return await awaitTurn(client, notifications, turnId, waitSeconds, waiting);
  } finally {
    await notifications?.return();
  }
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
      async (
        client,
        { text, thread_id: threadId, wait_seconds: waitSeconds }
      ) => {
        await client.request("thread/resume", { excludeTurns: true, threadId });
        return startTurn(client, threadId, text, waitSeconds);
      }
    )
  );
};
