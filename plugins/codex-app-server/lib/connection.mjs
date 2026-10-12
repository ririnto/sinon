import { once } from "node:events";
import { createConnection } from "node:net";
import { homedir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";

import { WebSocket } from "ws";

const REQUEST_TIMEOUT_MS = 300_000;

/**
 * Return the control socket path from the environment or from CODEX_HOME.
 */
const defaultSocket = () =>
  process.env.CODEX_APP_SERVER_SOCKET ||
  path.join(
    process.env.CODEX_HOME || path.join(homedir(), ".codex"),
    "app-server-control",
    "app-server-control.sock"
  );

/**
 * Connect over the Unix control socket, perform the initialize handshake, and return a JSON-RPC client.
 * An optional abort signal closes the socket even while initialization is pending.
 */
export const openClient = async ({ signal } = {}) => {
  signal?.throwIfAborted();
  const ws = new WebSocket("ws://localhost/", {
    createConnection: () => createConnection({ path: defaultSocket() }),
    maxPayload: 0
  });
  const events = new EventTarget();
  const closed = new AbortController();
  const emit = (name, detail) =>
    events.dispatchEvent(new CustomEvent(name, { detail }));
  const fail = (error) => {
    if (!closed.signal.aborted) {
      closed.abort(error);
    }
  };
  const send = async (message) => {
    try {
      await promisify(ws.send.bind(ws))(JSON.stringify(message));
    } catch (error) {
      fail(error);
      ws.terminate();
      throw error;
    }
  };
  ws.on("error", (error) => {
    fail(error);
    ws.terminate();
  });
  const abort = () => {
    fail(signal.reason);
    ws.terminate();
  };
  signal?.addEventListener("abort", abort, { once: true });
  ws.on("close", () => {
    signal?.removeEventListener("abort", abort);
    fail(new Error("App-server connection closed"));
  });
  let nextId = 0;
  ws.on("message", async (data) => {
    let message;
    try {
      message = JSON.parse(data.toString());
    } catch {
      return;
    }
    if (message === null || typeof message !== "object") {
      return;
    }
    if ("id" in message && ("result" in message || "error" in message)) {
      emit(`response:${message.id}`, message);
      return;
    }
    if ("id" in message && "method" in message) {
      try {
        await send({
          error: { code: -32_601, message: "not handled by this client" },
          id: message.id
        });
      } catch (error) {
        fail(error);
      }
      return;
    }
    emit("notification", message);
  });
  const request = async (method, params) => {
    closed.signal.throwIfAborted();
    nextId += 1;
    const id = nextId;
    const requestSignal = AbortSignal.any([
      AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      closed.signal
    ]);
    const response = once(events, `response:${id}`, { signal: requestSignal });
    try {
      const [[{ detail: message }]] = await Promise.all([
        response,
        send({
          id,
          method,
          ...(params === undefined ? {} : { params })
        })
      ]);
      if (message.error) {
        throw new Error(`${method}: ${JSON.stringify(message.error)}`);
      }
      return message.result;
    } catch (error) {
      throw closed.signal.aborted ? closed.signal.reason : error;
    }
  };
  try {
    await once(ws, "open", { signal: closed.signal });
    await request("initialize", {
      capabilities: { experimentalApi: true },
      clientInfo: {
        name: "orchestrator-codex-app-server-mcp",
        title: "Orchestrator MCP client",
        version: "1"
      }
    });
    await send({ method: "initialized" });
    return { close: () => ws.close(), events, request, signal: closed.signal };
  } catch (error) {
    ws.terminate();
    throw closed.signal.aborted ? closed.signal.reason : error;
  }
};

/**
 * Wrap a tool handler so it runs with its own app-server connection and returns MCP text content.
 */
export const withClient = (handler) => async (args) => {
  let client;
  try {
    client = await openClient();
    const value = await handler(client, args);
    return {
      content: [{ text: JSON.stringify(value ?? null, null, 1), type: "text" }]
    };
  } catch (error) {
    return { content: [{ text: error.message, type: "text" }], isError: true };
  } finally {
    client?.close();
  }
};
