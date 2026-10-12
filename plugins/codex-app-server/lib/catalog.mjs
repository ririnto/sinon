import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";

import { z } from "zod";

import { withClient } from "./connection.mjs";
import {
  BLOCKED_METHODS,
  CURATED_METHODS,
  NATIVE_CLI_METHODS,
  USAGE_METHODS
} from "./policy.mjs";

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
 * Generate the installed Codex's own ClientRequest schema into a temporary directory and return its methods.
 */
export const loadCatalog = async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-codex-schema-"));
  try {
    await promisify(execFile)(
      process.env.CODEX_BIN || "codex",
      ["app-server", "generate-json-schema", "--out", directory],
      { timeout: 30_000 }
    );
    const schema = JSON.parse(
      await readFile(path.join(directory, "ClientRequest.json"), "utf-8")
    );
    const defs = JSON.parse(
      JSON.stringify(schema.definitions ?? {}).replaceAll(
        "#/definitions/",
        "#/$defs/"
      )
    );
    return schema.oneOf.map((entry) => {
      const [method] = entry.properties.method.enum;
      const params = entry.properties.params
        ? JSON.parse(
            JSON.stringify(entry.properties.params).replaceAll(
              "#/definitions/",
              "#/$defs/"
            )
          )
        : null;
      return {
        method,
        params: params && { ...params, $defs: defs },
        readOnly: READ_ONLY_METHODS.has(method)
      };
    });
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
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
      "codex_methods",
      "codex_queue"
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
        "List app-server methods from the installed Codex's own schema, with their read-only or mutating class and the tool that serves each. " +
        "With a method name, also return its params JSON schema for use with codex_call. " +
        "Methods refused here are reset-credit consumption and thread archive, unarchive, and delete, which belong to the native codex CLI. " +
        "Set CODEX_APP_SERVER_METHODS to a comma-separated list of methods or prefixes, such as fs,config, to expose them as generated tools.",
      inputSchema: {
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
    ({ method, prefix }) => {
      const found = method
        ? catalog.filter((entry) => entry.method === method)
        : catalog.filter(
            (entry) =>
              !prefix || allowed(entry.method, [prefix.replace(/\/$/u, "")])
          );
      const value = found.map((entry) => {
        let tool = "codex_call";
        if (BLOCKED_METHODS.has(entry.method)) {
          tool = "refused";
        } else if (NATIVE_CLI_METHODS.has(entry.method)) {
          tool = "native_cli";
        } else if (CURATED_METHODS.has(entry.method)) {
          tool = "curated";
        } else if (exposable(entry.method) && allowed(entry.method, entries)) {
          tool = toolName(entry.method);
        }
        return {
          class: entry.readOnly ? "read" : "mutating",
          method: entry.method,
          ...(method && entry.params ? { params: entry.params } : {}),
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
      withClient((client, params) =>
        client.request(entry.method, entry.params ? params : undefined)
      )
    );
  }
};
