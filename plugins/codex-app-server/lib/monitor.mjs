import { once } from "node:events";

import { openClient } from "./connection.mjs";

/**
 * Watch native thread status broadcasts without resuming threads or claiming a writer.
 * Output only configured transitions and terminal failures, one structured event at a time.
 */
export const watchThreads = async ({ emit, signal, statuses, threadIds }) => {
  const client = await openClient({ signal });
  const current = new Map();
  const failures = new Map();
  const selected = (threadId) =>
    threadIds.includes("*") || threadIds.includes(threadId);
  const transition = (threadId, status, extra = {}) => {
    const previous = current.get(threadId) ?? null;
    if (previous === status) {
      return;
    }
    current.set(threadId, status);
    if (statuses.includes(status)) {
      emit({ ...extra, event: "threadStatus", previous, status, threadId });
    }
  };
  const terminalFailure = (threadId, turn) => {
    if (turn?.status === "failed" && failures.get(threadId) !== turn.id) {
      failures.set(threadId, turn.id);
      transition(threadId, "failed", {
        ...(turn.error ? { error: turn.error } : {}),
        turnId: turn.id
      });
    }
  };
  const inspect = async (threadId, initial = false) => {
    const { thread } = await client.request("thread/read", {
      includeTurns: true,
      threadId
    });
    terminalFailure(threadId, thread.turns?.at(-1));
    if (initial) {
      transition(threadId, thread.status?.type ?? thread.status ?? "notLoaded");
    }
  };
  const handle = async (note) => {
    const threadId = note.params?.threadId;
    if (!threadId || !selected(threadId)) {
      return;
    }
    if (note.method === "thread/status/changed") {
      const status = note.params.status?.type ?? note.params.status;
      if (status === "idle") {
        await inspect(threadId);
      }
      transition(threadId, status);
    } else if (note.method === "turn/completed") {
      terminalFailure(threadId, note.params.turn);
    } else if (note.method === "error" && !note.params.willRetry) {
      terminalFailure(threadId, {
        error: note.params.error,
        id: note.params.turnId,
        status: "failed"
      });
    }
  };
  const initialized = Promise.withResolvers();
  let pending = initialized.promise;
  const failed = new AbortController();
  const waiting = AbortSignal.any([signal, client.signal, failed.signal]);
  const ended = once(waiting, "abort");
  client.events.addEventListener("notification", async ({ detail: note }) => {
    if (
      !["thread/status/changed", "turn/completed", "error"].includes(
        note.method
      )
    ) {
      return;
    }
    const previous = pending;
    pending = (async () => {
      try {
        await previous;
        await handle(note);
      } catch (error) {
        failed.abort(error);
      }
    })();
    await pending;
  });
  try {
    if (!waiting.aborted) {
      let ids = threadIds;
      if (threadIds.includes("*")) {
        const loaded = await client.request("thread/loaded/list", {});
        ids = loaded.data ?? [];
      }
      await Promise.all(ids.map((threadId) => inspect(threadId, true)));
      initialized.resolve();
      if (!waiting.aborted) {
        await ended;
      }
    }
    if (client.signal.aborted || failed.signal.aborted) {
      throw failed.signal.reason ?? client.signal.reason;
    }
  } finally {
    initialized.resolve();
    client.close();
  }
};
