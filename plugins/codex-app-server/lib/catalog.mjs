import { createHash } from "node:crypto";

import { z } from "zod";

import { refusal, USAGE_METHODS } from "./policy.mjs";
import { loadProtocol, parameterSchema, validateRequest } from "./schema.mjs";

const READ_ONLY_METHODS = new Set([
  "config/read",
  "configRequirements/read",
  "fs/readFile",
  "model/list",
  "modelProvider/capabilities/read",
  "thread/goal/get",
  "thread/loaded/list",
  "thread/read"
]);

/**
 * Derive a stable method name, using a digest for ambiguous, reserved, or overlong spellings.
 * Ordinary alphanumeric path segments retain case and replace slashes with underscores.
 */
export const toolName = (method, reserved) => {
  const readable = `codex_${method.replaceAll("/", "_")}`;
  if (
    /^[A-Za-z0-9]+(?:\/[A-Za-z0-9]+)*$/u.test(method) &&
    readable.length <= 128 &&
    !reserved.has(readable) &&
    !readable.startsWith("codex_method_")
  ) {
    return readable;
  }
  return `codex_method_${createHash("sha256").update(method).digest("hex")}`;
};

const classification = (entry, kind) => {
  if (kind !== "clientRequest") {
    return "discovery_only";
  }
  return READ_ONLY_METHODS.has(entry.method) ? "read" : "mutating";
};

const allowed = (method, prefix) =>
  method === prefix || method.startsWith(`${prefix}/`);

/**
 * Derive the full native tool catalog without converting schemas through Zod.
 * Refused methods remain discoverable as tools whose callbacks never open a connection.
 */
export const deriveTools = (protocol, reserved, request) => {
  const names = new Set(reserved);
  return protocol.protocols.clientRequest.map((entry) => {
    const name = toolName(entry.method, reserved);
    if (names.has(name)) {
      throw new Error(`Duplicated native tool name: ${name}`);
    }
    names.add(name);
    const readOnly = READ_ONLY_METHODS.has(entry.method);
    const reason = refusal(entry.method);
    const { inputSchema, wrapped } = parameterSchema(
      entry.params,
      entry.request.required?.includes("params") ?? false
    );
    let authorization =
      "Use only under a direct human instruction or assigned contract authorizing this mutation. ";
    if (reason) {
      authorization = `Refusal-only tool: ${reason}. `;
    } else if (readOnly) {
      authorization = "Use only for authorized inspection. ";
    }
    const definition = {
      annotations: {
        destructiveHint: !readOnly,
        openWorldHint: false,
        readOnlyHint: readOnly
      },
      description:
        `Native app-server method ${entry.method}; class: ${readOnly ? "read" : "mutating"}. ${
          authorization
        }${
          USAGE_METHODS.has(entry.method)
            ? "Only the Orchestrator reads usage; do not delegate usage or monitoring duties. "
            : ""
        }Keep one writer per thread and read it before steering. ` +
        `Never start a turn while its last turn is in progress or another host holds it. ` +
        `Let the native writer release its lock; never delete locks or force a second writer. ` +
        `Prefer guarded codex_send, codex_steer, and codex_interrupt for turn operations. ` +
        `Server-initiated requests, including approvals, are refused. ${
          wrapped
            ? "Supply native parameters in the params argument; null and union schemas retain their native meaning."
            : "Supply native parameter fields directly."
        } ${
          entry.method.startsWith("command/exec")
            ? "Connection-scoped process IDs belong to the pending command/exec call; use follow-up tools concurrently. Streamed outputDelta notifications are included in its final result. "
            : ""
        }${
          ["fs/watch", "fs/unwatch"].includes(entry.method)
            ? "Watch connections persist until fs/unwatch or MCP closure; fs/changed events use standard MCP logging. "
            : ""
        }`,
      inputSchema,
      name
    };
    return {
      definition,
      handler: async (args, extra) => {
        if (reason) {
          return { content: [{ text: reason, type: "text" }], isError: true };
        }
        extra?.signal.throwIfAborted();
        if (wrapped && Object.keys(args).some((key) => key !== "params")) {
          throw new Error(
            `Only the params argument is accepted for ${entry.method}`
          );
        }
        if (!entry.params && Object.keys(args).length) {
          throw new Error(`No parameters are accepted for ${entry.method}`);
        }
        let params;
        if (entry.params) {
          params = wrapped ? args.params : args;
        }
        await validateRequest(entry.method, params);
        extra?.signal.throwIfAborted();
        return request(entry.method, params, extra);
      },
      method: entry.method
    };
  });
};

/**
 * Refresh the native tool registry atomically on discovery, notifying hosts after schema changes.
 * A failed generation retains the last successful list and native calls still require current validation.
 */
export const registerCatalogTools = async (registry, server, request) => {
  const reserved = new Set([...registry.keys(), "codex_methods"]);
  let current;
  let pending;
  let refreshError;
  const refresh = async ({ regenerate = true } = {}) => {
    if (pending) {
      return pending;
    }
    pending = (async () => {
      try {
        const protocol = await loadProtocol({ refresh: regenerate });
        refreshError = undefined;
        if (
          current?.version === protocol.version &&
          current?.hash === protocol.hash
        ) {
          return;
        }
        const tools = deriveTools(protocol, reserved, request);
        for (const tool of current?.tools ?? []) {
          registry.delete(tool.definition.name);
        }
        for (const tool of tools) {
          registry.set(tool.definition.name, tool);
        }
        current = { ...protocol, tools };
        if (server.getClientCapabilities()) {
          await server.sendToolListChanged();
        }
      } catch (error) {
        refreshError = error;
        console.error(
          `codex-app-server: native catalog refresh failed: ${error.message}`
        );
      }
    })();
    try {
      await pending;
    } finally {
      pending = undefined;
    }
  };
  registry.registerTool(
    "codex_methods",
    {
      annotations: {
        destructiveHint: false,
        openWorldHint: false,
        readOnlyHint: true
      },
      description:
        "Discover installed native methods and their parameter schemas, refreshing per-method tools and notifying the host on version or schema changes. " +
        "ClientRequest methods have individual tools; other protocol directions are discovery-only. " +
        "Credit consumption and thread archive, unarchive, and delete have refusal-only tools. " +
        "Mutating methods require direct human instruction or an assigned contract, and one writer per thread. " +
        "Use the host's native tool search to load individual schemas on demand.",
      inputSchema: {
        kind: z
          .enum([
            "clientRequest",
            "clientNotification",
            "serverRequest",
            "serverNotification"
          ])
          .optional()
          .describe(
            "Protocol direction; defaults to clientRequest. Other directions are discovery-only."
          ),
        method: z
          .string()
          .optional()
          .describe(
            "Exact native method name, returning its parameter schema."
          ),
        prefix: z
          .string()
          .optional()
          .describe("Optional namespace prefix such as thread or fs.")
      }
    },
    async ({ kind = "clientRequest", method, prefix }) => {
      await refresh();
      if (refreshError) {
        throw refreshError;
      }
      const entries = current.protocols[kind].filter((entry) =>
        method
          ? entry.method === method
          : !prefix || allowed(entry.method, prefix.replace(/\/$/u, ""))
      );
      return {
        content: [
          {
            text: JSON.stringify(
              entries.map((entry) => ({
                class: classification(entry, kind),
                kind,
                method: entry.method,
                ...(method && entry.params
                  ? { params: parameterSchema(entry.params).native }
                  : {}),
                tool:
                  kind === "clientRequest"
                    ? toolName(entry.method, reserved)
                    : "discovery_only",
                ...(kind === "clientRequest" && refusal(entry.method)
                  ? { refusal: refusal(entry.method) }
                  : {})
              })),
              null,
              1
            ),
            type: "text"
          }
        ]
      };
    }
  );
  await refresh();
  return refresh;
};
