import { openClient, withClient } from "./connection.mjs";

/**
 * Own connection-scoped command and watch resources until their native operation or MCP session ends.
 * Command output remains in its result; watch changes use standard MCP logging without retaining history.
 */
export const createNativeSessions = (server) => {
  const commands = new Map();
  const watches = new Map();
  const lifetime = new AbortController();
  const connect = (signal) => {
    lifetime.signal.throwIfAborted();
    return openClient({ signal });
  };
  const command = async (method, params, extra) => {
    if (method !== "command/exec") {
      const pending = commands.get(params.processId);
      if (!pending) {
        throw new Error("No active command connection for this processId");
      }
      const client = await pending;
      return client.request(method, params, { signal: extra?.signal });
    }
    if (params.processId && commands.has(params.processId)) {
      throw new Error("A command already owns this processId");
    }
    const pending = connect(extra?.signal);
    if (params.processId) {
      commands.set(params.processId, pending);
    }
    let client;
    const notifications = [];
    const receive = ({ detail }) => {
      if (
        detail.method === "command/exec/outputDelta" &&
        detail.params.processId === params.processId
      ) {
        notifications.push(detail);
      }
    };
    try {
      client = await pending;
      client.events.addEventListener("notification", receive);
      const result = await client.request(method, params, {
        signal: extra?.signal
      });
      return notifications.length ? { ...result, notifications } : result;
    } finally {
      if (commands.get(params.processId) === pending) {
        commands.delete(params.processId);
      }
      client?.events.removeEventListener("notification", receive);
      client?.close();
    }
  };
  const watch = async (method, params, extra) => {
    let pending = watches.get(params.watchId);
    if (!pending && method === "fs/unwatch") {
      throw new Error("No active filesystem connection for this watchId");
    }
    const created = !pending;
    if (created) {
      pending = connect(extra?.signal);
      watches.set(params.watchId, pending);
    }
    let client;
    try {
      client = await pending;
      if (created) {
        const receive = async (detail) => {
          if (
            detail.method === "fs/changed" &&
            detail.params.watchId === params.watchId
          ) {
            try {
              await server.sendLoggingMessage({
                data: detail,
                level: "info",
                logger: "codex-app-server"
              });
            } catch (error) {
              console.error(
                `codex-app-server: watch notification failed: ${error.message}`
              );
            }
          }
        };
        const unsubscribe = client.listenNotifications(receive);
        client.signal.addEventListener(
          "abort",
          () => {
            unsubscribe();
            if (watches.get(params.watchId) === pending) {
              watches.delete(params.watchId);
            }
          },
          { once: true }
        );
      }
      const result = await client.request(method, params, {
        signal: extra?.signal
      });
      if (method === "fs/unwatch") {
        watches.delete(params.watchId);
        client.close();
      }
      return result;
    } catch (error) {
      if (created) {
        watches.delete(params.watchId);
        client?.close();
      }
      throw error;
    }
  };
  return {
    close: () => {
      lifetime.abort(new Error("Native MCP session is closed"));
      commands.clear();
      watches.clear();
    },
    request: async (method, params, extra) => {
      const context = {
        signal: AbortSignal.any([
          lifetime.signal,
          ...(extra?.signal ? [extra.signal] : [])
        ])
      };
      context.signal.throwIfAborted();
      let value;
      if (method === "command/exec" || method.startsWith("command/exec/")) {
        value = await command(method, params, context);
      } else if (method === "fs/watch" || method === "fs/unwatch") {
        value = await watch(method, params, context);
      } else {
        return withClient((client) =>
          client.request(method, params, { signal: context.signal })
        )(undefined, context);
      }
      return {
        content: [
          { text: JSON.stringify(value ?? null, null, 1), type: "text" }
        ]
      };
    }
  };
};
