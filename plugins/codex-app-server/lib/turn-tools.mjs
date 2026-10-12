import { z } from "zod";

import {
  enqueueMessage,
  queuedThread,
  unsupportedInterrupt,
  withThreadWriter
} from "./queue.mjs";

const unsupportedThreads = new Set();

const CLEANUP_NOTE =
  "Use native codex archive|unarchive|delete --remote unix:// for thread cleanup. " +
  "Archive only after pending work ends. Delete only when the transcript is no longer needed.";

const THREAD_ID = z
  .string()
  .describe(
    "Authorized thread ID inspected with codex_threads/codex_tail; keep the ID out of tracked files."
  );

/**
 * Return the thread's last turn ID, refusing unless that turn is still in progress.
 */
const activeTurn = async (client, threadId) => {
  const { thread } = await client.request("thread/read", {
    includeTurns: true,
    threadId
  });
  const last = (thread.turns ?? []).at(-1);
  if (last?.status !== "inProgress") {
    throw new Error(
      `Last turn of the thread is ${last?.status ?? "absent"}, not inProgress`
    );
  }
  return { last, thread };
};

/**
 * Register active-turn steering and interruption tools on the MCP server.
 */
export const registerTurnTools = (server) => {
  server.registerTool(
    "codex_steer",
    {
      description:
        `Queue authorized text for a fresh turn after active work completes by default; mode steer appends to the active turn only with verified provider support. ` +
        `Reads the thread's last turn and refuses unless it is inProgress; the active turn ID is filled in for you. ` +
        `Known response.interrupt failures select the queue instead; an ambiguous steering failure is refused without retry. ` +
        `Use only under a direct human instruction or assigned contract authorizing steering of this thread. ` +
        `Read it with codex_threads and codex_tail first; keep one writer per thread. ` +
        `On an active-writer error, identify the holder through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex). ` +
        `Let that host release it; never delete locks or force a second writer. ${
          CLEANUP_NOTE
        }`,
      inputSchema: {
        mode: z
          .enum(["queue", "steer"])
          .optional()
          .describe(
            "queue waits for a fresh turn and avoids provider interruption; steer requires verified provider support and is never retried."
          ),
        text: z
          .string()
          .describe(
            "Authorized text to queue after active work, or steer now when explicitly requested and supported."
          ),
        thread_id: THREAD_ID
      }
    },
    withThreadWriter(
      async (client, { mode = "queue", text, thread_id: threadId }) => {
        const { last, thread } = await activeTurn(client, threadId);
        const knownUnsupported =
          unsupportedThreads.has(threadId) ||
          (thread.turns ?? []).some((turn) => unsupportedInterrupt(turn.error));
        if (mode === "queue" || knownUnsupported || queuedThread(threadId)) {
          return enqueueMessage(
            threadId,
            text,
            knownUnsupported
              ? "Provider rejected response.interrupt; using a fresh-turn queue"
              : "Queue requested for active turn"
          );
        }
        try {
          const result = await client.request("turn/steer", {
            expectedTurnId: last.id,
            input: [{ text, type: "text" }],
            threadId
          });
          return { ...result, status: "steered", threadId };
        } catch (error) {
          if (unsupportedInterrupt(error.message)) {
            unsupportedThreads.add(threadId);
          }
          return {
            reason: error.message,
            status: "refused",
            threadId,
            unsupportedInterrupt: unsupportedInterrupt(error.message)
          };
        }
      }
    )
  );
  server.registerTool(
    "codex_interrupt",
    {
      description:
        `Interrupt the in-progress turn of a Codex thread. ` +
        `Reads the thread's last turn and refuses unless it is inProgress; the active turn ID is filled in for you. ` +
        `Verify provider support first: a gateway rejecting response.interrupt can fail the active turn. ` +
        `Use only under a direct human instruction or assigned contract authorizing steering of this thread. ` +
        `Read it with codex_threads and codex_tail first; keep one writer per thread. ` +
        `On an active-writer error, let the holder of thread-writer-locks/THREAD_ID.lock under CODEX_HOME release it; never delete locks or force a second writer. ${
          CLEANUP_NOTE
        }`,
      inputSchema: { thread_id: THREAD_ID }
    },
    withThreadWriter(async (client, { thread_id: threadId }) => {
      const { last } = await activeTurn(client, threadId);
      try {
        await client.request("turn/interrupt", { threadId, turnId: last.id });
        return { status: "interrupted", threadId, turnId: last.id };
      } catch (error) {
        if (unsupportedInterrupt(error.message)) {
          unsupportedThreads.add(threadId);
        }
        return {
          reason: error.message,
          status: "refused",
          threadId,
          unsupportedInterrupt: unsupportedInterrupt(error.message)
        };
      }
    })
  );
};
