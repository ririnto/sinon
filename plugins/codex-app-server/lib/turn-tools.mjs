import { z } from "zod";

import { withClient } from "./connection.mjs";

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
const activeTurnId = async (client, threadId) => {
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
  return last.id;
};

/**
 * Register active-turn steering and interruption tools on the MCP server.
 */
export const registerTurnTools = (server) => {
  server.registerTool(
    "codex_steer",
    {
      description:
        `Append authorized text to the in-progress turn of a Codex thread without starting a new turn. ` +
        `Reads the thread's last turn and refuses unless it is inProgress; the active turn ID is filled in for you. ` +
        `Use only under a direct human instruction or assigned contract authorizing steering of this thread. ` +
        `Read it with codex_threads and codex_tail first; keep one writer per thread. ` +
        `On an active-writer error, identify the holder through thread-writer-locks/THREAD_ID.lock under CODEX_HOME (default ~/.codex). ` +
        `Let that host release it; never delete locks or force a second writer. ${
          CLEANUP_NOTE
        }`,
      inputSchema: {
        text: z
          .string()
          .describe(
            "Authorized steering text for the active turn; do not use this tool to schedule work or poll status."
          ),
        thread_id: THREAD_ID
      }
    },
    withClient(async (client, { text, thread_id: threadId }) => {
      const expectedTurnId = await activeTurnId(client, threadId);
      return client.request("turn/steer", {
        expectedTurnId,
        input: [{ text, type: "text" }],
        threadId
      });
    })
  );
  server.registerTool(
    "codex_interrupt",
    {
      description:
        `Interrupt the in-progress turn of a Codex thread. ` +
        `Reads the thread's last turn and refuses unless it is inProgress; the active turn ID is filled in for you. ` +
        `Use only under a direct human instruction or assigned contract authorizing steering of this thread. ` +
        `Read it with codex_threads and codex_tail first; keep one writer per thread. ` +
        `On an active-writer error, let the holder of thread-writer-locks/THREAD_ID.lock under CODEX_HOME release it; never delete locks or force a second writer. ${
          CLEANUP_NOTE
        }`,
      inputSchema: { thread_id: THREAD_ID }
    },
    withClient(async (client, { thread_id: threadId }) =>
      client.request("turn/interrupt", {
        threadId,
        turnId: await activeTurnId(client, threadId)
      })
    )
  );
};
