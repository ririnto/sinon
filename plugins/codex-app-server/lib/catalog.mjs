import { z } from "zod";

import { withClient } from "./connection.mjs";
import {
  BLOCKED_METHODS,
  CURATED_METHODS,
  NATIVE_CLI_METHODS,
  USAGE_METHODS
} from "./policy.mjs";
import { loadProtocol, validateRequest } from "./schema.mjs";

// Unknown methods stay mutating until their read-only behavior is reviewed.
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
 * Return client request methods from the shared installed-version protocol cache.
 */
export const loadCatalog = async () => {
  const { protocols } = await loadProtocol();
  return protocols.clientRequest.map((entry) => ({
    ...entry,
    readOnly: READ_ONLY_METHODS.has(entry.method)
  }));
};

/**
 * Return the generated tool name for an app-server method.
 */
const toolName = (method) =>
  `codex_${method.replaceAll("/", "_").replaceAll(/[^A-Za-z0-9_]/gu, "_")}`;

/**
 * Return whether a method may become a generated tool.
 */
const exposable = (method) =>
  ![BLOCKED_METHODS, CURATED_METHODS, NATIVE_CLI_METHODS, USAGE_METHODS].some(
    (set) => set.has(method)
  );

/**
 * Return whether the method matches an allowlist entry, either exactly or as a path prefix.
 */
const allowed = (method, entries) =>
  entries.some((entry) => method === entry || method.startsWith(`${entry}/`));

/**
 * Register the method discovery tool and the allowlisted generated tools from the installed Codex schema.
 * A missing or failing schema leaves only the curated tools and reports the reason on stderr.
 */
export const registerCatalogTools = async (server) => {
  const entries = (process.env.CODEX_APP_SERVER_METHODS || "")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);
  let catalog;
  let generated;
  try {
    const loaded = await loadCatalog();
    catalog = loaded.map((entry) => {
      const inputSchema = entry.params ? z.fromJSONSchema(entry.params) : null;
      const params = inputSchema
        ? z.toJSONSchema(inputSchema, { io: "input" })
        : null;
      return { ...entry, inputSchema, params };
    });
    generated = catalog.filter(
      (entry) => exposable(entry.method) && allowed(entry.method, entries)
    );
    const names = new Set([
      "codex_threads",
      "codex_tail",
      "codex_start",
      "codex_send",
      "codex_steer",
      "codex_interrupt",
      "codex_fork",
      "codex_rename",
      "codex_goal",
      "codex_loaded",
      "codex_compact",
      "codex_mcp_tool",
      "codex_call",
      "codex_methods"
    ]);
    for (const entry of generated) {
      const name = toolName(entry.method);
      if (name.length > 128 || names.has(name)) {
        throw new Error(
          `Generated tool name is invalid or duplicated: ${name}`
        );
      }
      if (entry.params && entry.params.type !== "object") {
        throw new Error(
          `Generated tool parameters must be an object: ${entry.method}`
        );
      }
      names.add(name);
    }
  } catch (error) {
    console.error(
      `codex-app-server: schema catalog unavailable, serving curated tools only: ${error.message}`
    );
    return;
  }
  server.registerTool(
    "codex_methods",
    {
      annotations: { openWorldHint: false, readOnlyHint: true },
      description:
        "List requests and notifications in both protocol directions from the installed Codex native schema. " +
        "Discovery refreshes when codex --version changes; notifications and server requests are discovery-only. " +
        "With a method name, also return its params JSON schema for use with codex_call. " +
        "Methods refused here are reset-credit consumption and thread archive, unarchive, and delete, which belong to the native codex CLI. " +
        "Set CODEX_APP_SERVER_METHODS to a comma-separated list of methods or prefixes, such as fs,config, to expose them as generated tools.",
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
            "Protocol direction to inspect; defaults to clientRequest. Other directions are discovery-only."
          ),
        method: z
          .string()
          .optional()
          .describe(
            "Exact method name to return with its params JSON schema; omitted lists all methods."
          ),
        prefix: z
          .string()
          .optional()
          .describe(
            "Optional method prefix such as thread or fs to narrow the list."
          )
      }
    },
    async ({ kind = "clientRequest", method, prefix }) => {
      let current;
      try {
        const loaded = await loadProtocol();
        current = loaded.protocols[kind];
      } catch (error) {
        return {
          content: [{ text: error.message, type: "text" }],
          isError: true
        };
      }
      const found = method
        ? current.filter((entry) => entry.method === method)
        : current.filter(
            (entry) =>
              !prefix || allowed(entry.method, [prefix.replace(/\/$/u, "")])
          );
      const value = found.map((entry) => {
        let tool = "codex_call";
        if (kind !== "clientRequest") {
          tool = "discovery_only";
        } else if (BLOCKED_METHODS.has(entry.method)) {
          tool = "refused";
        } else if (NATIVE_CLI_METHODS.has(entry.method)) {
          tool = "native_cli";
        } else if (CURATED_METHODS.has(entry.method)) {
          tool = "curated";
        } else if (generated.some((item) => item.method === entry.method)) {
          tool = toolName(entry.method);
        }
        let classification = "discovery_only";
        if (kind === "clientRequest") {
          classification = READ_ONLY_METHODS.has(entry.method)
            ? "read"
            : "mutating";
        }
        return {
          class: classification,
          kind,
          method: entry.method,
          ...(method && entry.params
            ? {
                params: z.toJSONSchema(z.fromJSONSchema(entry.params), {
                  io: "input"
                })
              }
            : {}),
          tool
        };
      });
      return {
        content: [{ text: JSON.stringify(value, null, 1), type: "text" }]
      };
    }
  );
  for (const entry of generated) {
    server.registerTool(
      toolName(entry.method),
      {
        annotations: {
          openWorldHint: false,
          readOnlyHint: entry.readOnly
        },
        description:
          `Send the app-server ${entry.method} request, generated from the installed Codex schema. ${
            entry.readOnly
              ? "This method is on the reviewed read-only allowlist. "
              : "This can mutate daemon state; use only under a direct human instruction or assigned contract authorizing it. "
          }Keep one writer per thread and never start or steer a turn through it. ` +
          `Use native codex archive|unarchive|delete --remote unix:// for thread cleanup.`,
        ...(entry.inputSchema ? { inputSchema: entry.inputSchema } : {})
      },
      async (params) => {
        try {
          await validateRequest(
            entry.method,
            entry.params ? params : undefined
          );
        } catch (error) {
          return {
            content: [{ text: error.message, type: "text" }],
            isError: true
          };
        }
        return withClient((client, input) =>
          client.request(entry.method, entry.params ? input : undefined)
        )(params);
      }
    );
  }
};
