#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { createInterface } from "node:readline";

type JsonValue =
  | null
  | boolean
  | number
  | string
  | readonly JsonValue[]
  | { readonly [key: string]: JsonValue };

type JsonObject = Readonly<Record<string, JsonValue>>;

const PROTOCOL_VERSION = "2025-11-25";

/** Return true when a parsed value is a JSON object. */
const isJsonObject = (value: unknown): value is JsonObject =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Read a string property from a JSON object. */
const stringField = (message: JsonObject, key: string): string => {
  const value = message[key];
  return typeof value === "string" ? value : "";
};

/** Write one newline-delimited JSON-RPC message to stdout. */
const writeMessage = (message: JsonObject): void => {
  process.stdout.write(`${JSON.stringify(message)}\n`);
};

/** Create a successful JSON-RPC response. */
const success = (
  messageId: JsonValue | undefined,
  result: JsonValue
): JsonObject => ({ id: messageId ?? null, jsonrpc: "2.0", result });

/** Create a JSON-RPC error response. */
const error = (
  messageId: JsonValue | undefined,
  code: number,
  message: string
): JsonObject => ({
  error: { code, message },
  id: messageId ?? null,
  jsonrpc: "2.0"
});

/** Return the tool definition for read_plugin_paths. */
const toolDefinition = (): JsonObject => ({
  description:
    "Return the plugin root and data directories for local runtime checks.",
  inputSchema: {
    additionalProperties: false,
    properties: {},
    type: "object"
  },
  name: "read_plugin_paths"
});

/** Return the result of the read_plugin_paths tool. */
const toolResult = (): JsonObject => ({
  content: [
    {
      text: JSON.stringify(
        {
          pluginData: process.env["CLAUDE_PLUGIN_DATA"] ?? "",
          pluginRoot: process.env["CLAUDE_PLUGIN_ROOT"] ?? ""
        },
        null,
        2
      ),
      type: "text"
    }
  ]
});

/** Handle one MCP request or notification. */
const handleMessage = (message: JsonObject): JsonObject | undefined => {
  const method = stringField(message, "method");
  const messageId = message["id"];
  const isNotification = messageId === undefined;
  if (method === "initialize") {
    return isNotification
      ? undefined
      : success(messageId, {
          capabilities: { tools: {} },
          protocolVersion: PROTOCOL_VERSION,
          serverInfo: { name: "example-mcp", version: "0.1.0" }
        });
  }
  if (method === "notifications/initialized") {
    return undefined;
  }
  if (method === "ping") {
    return isNotification ? undefined : success(messageId, {});
  }
  if (method === "tools/list") {
    return isNotification
      ? undefined
      : success(messageId, { tools: [toolDefinition()] });
  }
  if (method === "tools/call") {
    const { params } = message;
    if (isNotification) {
      return undefined;
    }
    if (
      !isJsonObject(params) ||
      stringField(params, "name") !== "read_plugin_paths"
    ) {
      return error(messageId, -32_602, "Unknown tool");
    }
    return success(messageId, toolResult());
  }
  return isNotification
    ? undefined
    : error(messageId, -32_601, `Unsupported method: ${method}`);
};

/** Read newline-delimited JSON-RPC messages from stdin. */
const runServer = async (): Promise<void> => {
  const lines = createInterface({
    crlfDelay: Number.POSITIVE_INFINITY,
    input: process.stdin
  });
  for await (const line of lines) {
    if (line.length === 0) {
      continue;
    }
    try {
      const parsed: unknown = JSON.parse(line);
      if (!isJsonObject(parsed)) {
        writeMessage(
          error(null, -32_600, "JSON-RPC message must be an object")
        );
        continue;
      }
      const response = handleMessage(parsed);
      if (response !== undefined) {
        writeMessage(response);
      }
    } catch {
      writeMessage(error(null, -32_700, "Invalid JSON"));
    }
  }
};

await runServer();
