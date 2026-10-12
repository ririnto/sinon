import { once } from "node:events";

import { openClient } from "./connection.mjs";

/**
 * Watch native thread status broadcasts without resuming threads or claiming a writer.
 * Output only configured transitions and terminal failures, one structured event at a time.
 */
export const watchThreads = async ({ emit, signal, statuses, threadIds }) => {
  const client = await openClient({ signal });
  // Inspections use a separate read-only socket so paused event intake cannot block their responses.
  let inspector;
  try {
    inspector = await openClient({
      signal: AbortSignal.any([signal, client.signal])
    });
  } catch (error) {
    client.close();
    throw error;
  }
  const initialized = Promise.withResolvers();
  const waiting = AbortSignal.any([signal, client.signal, inspector.signal]);
  const ended = once(waiting, "abort");
  const output = (event) => emit(event, waiting);
  const current = new Map();
  const failures = new Map();
  const selected = (threadId) =>
    threadIds.includes("*") || threadIds.includes(threadId);
  const transition = async (threadId, status, extra = {}) => {
    const previous = current.get(threadId) ?? null;
    if (previous === status) {
      return;
    }
    current.set(threadId, status);
    if (statuses.includes(status)) {
      await output({
        ...extra,
        event: "threadStatus",
        previous,
        status,
        threadId
      });
    }
  };
  const terminalFailure = async (threadId, turn) => {
    if (turn?.status === "failed" && failures.get(threadId) !== turn.id) {
      failures.set(threadId, turn.id);
      if (statuses.includes("failed")) {
        await output({
          ...(turn.error ? { error: turn.error } : {}),
          event: "turnError",
          previous: current.get(threadId) ?? null,
          status: "failed",
          threadId,
          turnId: turn.id
        });
      }
    }
  };
  const inspectionError = async (threadId, error) => {
    if (waiting.aborted) {
      throw error;
    }
    if (!signal.aborted) {
      await output({
        error: error.message,
        event: "inspection",
        status: "systemError",
        threadId
      });
    }
  };
  const inspect = async (threadId, initial = false) => {
    try {
      const latest = await inspector.request("thread/turns/list", {
        itemsView: "notLoaded",
        limit: 1,
        sortDirection: "desc",
        threadId
      });
      await terminalFailure(threadId, latest.data?.[0]);
      if (initial) {
        const { thread } = await inspector.request("thread/read", {
          includeTurns: false,
          threadId
        });
        await transition(
          threadId,
          thread.status?.type ?? thread.status ?? "notLoaded"
        );
      }
    } catch (error) {
      await inspectionError(threadId, error);
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
      await transition(threadId, status);
    } else if (note.method === "turn/completed") {
      await terminalFailure(threadId, note.params.turn);
    } else if (note.method === "error" && !note.params.willRetry) {
      await terminalFailure(threadId, {
        error: note.params.error,
        id: note.params.turnId,
        status: "failed"
      });
    }
  };
  const notification = async (note) => {
    if (
      !["thread/status/changed", "turn/completed", "error"].includes(
        note.method
      )
    ) {
      return;
    }
    await initialized.promise;
    if (!waiting.aborted) {
      await handle(note);
    }
  };
  const unsubscribe = client.listenNotifications(notification);
  try {
    if (!waiting.aborted) {
      let ids = threadIds;
      if (threadIds.includes("*")) {
        ids = [];
        try {
          const cursors = new Set();
          const page = async (cursor) => {
            const loaded = await inspector.request(
              "thread/loaded/list",
              cursor ? { cursor } : {}
            );
            ids.push(...(loaded.data ?? []));
            const next = loaded.nextCursor;
            if (next && cursors.has(next)) {
              throw new Error("Loaded-thread pagination repeated its cursor");
            }
            if (next && !waiting.aborted) {
              cursors.add(next);
              await page(next);
            }
          };
          await page();
        } catch (error) {
          await inspectionError(null, error);
        }
      }
      for await (const threadId of new Set(ids)) {
        if (waiting.aborted) {
          break;
        }
        await inspect(threadId, true);
      }
      initialized.resolve();
      if (!waiting.aborted) {
        await ended;
      }
    }
    if (client.signal.aborted || inspector.signal.aborted) {
      throw client.signal.reason ?? inspector.signal.reason;
    }
  } finally {
    unsubscribe();
    initialized.resolve();
    client.close();
    inspector.close();
  }
};
