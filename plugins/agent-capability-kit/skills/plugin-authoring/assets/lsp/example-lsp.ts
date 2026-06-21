#!/usr/bin/env bun
// -*- coding: utf-8 -*-
/* eslint-disable func-style, no-await-in-loop, promise/avoid-new */

type JsonValue =
  | null
  | boolean
  | number
  | string
  | readonly JsonValue[]
  | { readonly [key: string]: JsonValue };

type JsonObject = Readonly<Record<string, JsonValue>>;

const decoder = new TextDecoder();
const encoder = new TextEncoder();

/** Return true when a parsed value is a JSON object. */
function isJsonObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Read a string property from a JSON object. */
function stringField(message: JsonObject, key: string): string {
  const value = message[key];
  return typeof value === "string" ? value : "";
}

/** Read a number property from a JSON object. */
function numberField(message: JsonObject, key: string): number {
  const value = message[key];
  return typeof value === "number" ? value : 0;
}

/** Find the byte span that separates JSON-RPC headers from a body. */
function headerBoundary(
  bytes: Uint8Array,
  offset: number
): readonly [number, number] | undefined {
  for (let index = offset; index < bytes.length - 1; index += 1) {
    if (bytes[index] === 10 && bytes[index + 1] === 10) {
      return [index, index + 2];
    }
    if (
      index < bytes.length - 3 &&
      bytes[index] === 13 &&
      bytes[index + 1] === 10 &&
      bytes[index + 2] === 13 &&
      bytes[index + 3] === 10
    ) {
      return [index, index + 4];
    }
  }
  return undefined;
}

/** Append one byte chunk to a buffered byte stream. */
function appendBytes(
  left: Uint8Array<ArrayBufferLike>,
  right: Uint8Array<ArrayBufferLike>
): Uint8Array<ArrayBufferLike> {
  const merged = new Uint8Array(left.byteLength + right.byteLength);
  merged.set(left);
  merged.set(right, left.byteLength);
  return merged;
}

/** Process complete framed JSON-RPC messages from a byte buffer. */
function readMessagesFromBuffer(
  bytes: Uint8Array<ArrayBufferLike>
): readonly [readonly JsonObject[], Uint8Array<ArrayBufferLike>] {
  let offset = 0;
  const messages: JsonObject[] = [];
  while (offset < bytes.length) {
    const boundary = headerBoundary(bytes, offset);
    if (boundary === undefined) {
      break;
    }
    const [headerEnd, bodyStart] = boundary;
    const headers = decoder.decode(bytes.subarray(offset, headerEnd));
    const contentLength = headers
      .split(/\r?\n/u)
      .map((line) => line.split(":", 2))
      .find(([name]) => name?.toLowerCase() === "content-length")?.[1];
    const length = Number.parseInt(contentLength?.trim() ?? "0", 10);
    if (length <= 0 || bodyStart + length > bytes.length) {
      break;
    }
    const parsed: unknown = JSON.parse(
      decoder.decode(bytes.subarray(bodyStart, bodyStart + length))
    );
    if (isJsonObject(parsed)) {
      messages.push(parsed);
    }
    offset = bodyStart + length;
  }
  return [messages, bytes.subarray(offset)];
}

/** Write one framed JSON-RPC message to stdout. */
function writeMessage(message: JsonObject): void {
  const body = encoder.encode(JSON.stringify(message));
  process.stdout.write(`Content-Length: ${body.byteLength}\r\n\r\n`);
  process.stdout.write(body);
}

/** Create a successful JSON-RPC response. */
function success(
  messageId: JsonValue | undefined,
  result: JsonValue
): JsonObject {
  return { id: messageId ?? null, jsonrpc: "2.0", result };
}

/** Generate hover information for a position. */
function hoverResult(uri: string, position: JsonObject): JsonObject {
  const line = numberField(position, "line");
  const character = numberField(position, "character");
  return {
    contents: {
      kind: "markdown",
      value: `Hover from local example LSP.\n\n- uri: \`${uri}\`\n- line: \`${line}\`\n- character: \`${character}\``
    }
  };
}

/** Handle an LSP request message. */
function handleRequest(message: JsonObject): JsonObject | undefined {
  const method = stringField(message, "method");
  const messageId = message["id"];
  if (method === "initialize") {
    return success(messageId, {
      capabilities: {
        hoverProvider: true,
        textDocumentSync: 1
      },
      serverInfo: {
        name: "example-lsp",
        version: "0.1.0"
      }
    });
  }
  if (method === "initialized" || method === "exit") {
    return undefined;
  }
  if (method === "textDocument/hover") {
    const { params } = message;
    const textDocument =
      isJsonObject(params) && isJsonObject(params["textDocument"])
        ? params["textDocument"]
        : {};
    const position =
      isJsonObject(params) && isJsonObject(params["position"])
        ? params["position"]
        : {};
    return success(
      messageId,
      hoverResult(stringField(textDocument, "uri"), position)
    );
  }
  if (method === "shutdown") {
    return success(messageId, null);
  }
  return undefined;
}

/** Read stdin as a live JSON-RPC stream. */
async function runServer(): Promise<void> {
  return await new Promise((resolve) => {
    let buffer: Uint8Array<ArrayBufferLike> = new Uint8Array();
    process.stdin.resume();
    process.stdin.on("data", (chunk: Buffer) => {
      buffer = appendBytes(buffer, chunk);
      const [messages, remaining] = readMessagesFromBuffer(buffer);
      buffer = remaining;
      for (const message of messages) {
        const response = handleRequest(message);
        if (response !== undefined) {
          writeMessage(response);
        }
        if (stringField(message, "method") === "shutdown") {
          resolve();
          return;
        }
      }
    });
    process.stdin.on("end", () => {
      resolve();
    });
  });
}

await runServer();
