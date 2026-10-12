import { on } from "node:events";

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
 * Release the caller's optional writer after acknowledgment, before waiting for completion.
 */
export const startTurn = async (
  client,
  threadId,
  text,
  waitSeconds = 0,
  onStarted = null
) => {
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
    onStarted?.();
    if (!notifications) {
      return { finalMessage: null, status: "started", turnId };
    }
    return await awaitTurn(client, notifications, turnId, waitSeconds, waiting);
  } finally {
    await notifications?.return();
  }
};
