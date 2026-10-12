import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";

import { AjvJsonSchemaValidator } from "@modelcontextprotocol/sdk/validation/ajv";

const PROTOCOLS = {
  clientNotification: "ClientNotification.json",
  clientRequest: "ClientRequest.json",
  serverNotification: "ServerNotification.json",
  serverRequest: "ServerRequest.json"
};
let cached;
let pending;

const generate = async (binary, version) => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-codex-schema-"));
  try {
    await promisify(execFile)(
      binary,
      ["app-server", "generate-json-schema", "--out", directory],
      { timeout: 30_000 }
    );
    const hash = createHash("sha256");
    const protocols = await Promise.all(
      Object.entries(PROTOCOLS).map(async ([kind, file]) => {
        const source = await readFile(path.join(directory, file), "utf-8");
        const schema = JSON.parse(source, (key, value) =>
          key === "$ref" && typeof value === "string"
            ? value.replace("#/definitions/", "#/$defs/")
            : value
        );
        const defs = schema.definitions ?? schema.$defs ?? {};
        return [
          kind,
          schema.oneOf.map((entry) => ({
            kind,
            method:
              entry.properties.method.const ?? entry.properties.method.enum[0],
            params: entry.properties.params
              ? { ...entry.properties.params, $defs: defs }
              : null,
            request: { ...entry, $defs: defs }
          })),
          source
        ];
      })
    );
    for (const [kind, , source] of protocols) {
      hash.update(kind).update(source);
    }
    return {
      hash: hash.digest("hex"),
      protocols: Object.fromEntries(protocols),
      validators: new Map(),
      version
    };
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

/**
 * Share concurrent native schema generations and cache successful results by executable and version.
 * Explicit discovery refreshes regenerate to detect schema changes within the same CLI version.
 */
export const loadProtocol = async ({ refresh = false } = {}) => {
  const binary = process.env.CODEX_BIN || "codex";
  const { stdout } = await promisify(execFile)(binary, ["--version"], {
    timeout: 30_000
  });
  const version = stdout.trim();
  if (!version) {
    throw new Error("Codex returned an empty version");
  }
  const key = `${binary}\0${version}`;
  if (!refresh && cached?.key === key && pending?.key !== key) {
    return cached.value;
  }
  if (pending?.key !== key) {
    pending = { key, promise: generate(binary, version) };
  }
  const current = pending;
  try {
    const value = await current.promise;
    if (pending === current) {
      cached = { key, value };
    }
    return value;
  } catch (error) {
    if (cached?.key === key) {
      cached = undefined;
    }
    throw error;
  } finally {
    if (pending === current) {
      pending = undefined;
    }
  }
};

/**
 * Preserve a method's native parameter schema with only its reachable definitions.
 * Resolve root references and wrap non-object parameters in a native-validated params argument.
 */
export const parameterSchema = (params, required = true) => {
  if (!params) {
    return {
      inputSchema: {
        additionalProperties: false,
        properties: {},
        type: "object"
      },
      native: null,
      wrapped: false
    };
  }
  const { $defs: definitions = {}, ...source } = params;
  let root = source;
  if (source.$ref) {
    const name = source.$ref.replace("#/$defs/", "");
    if (!Object.hasOwn(definitions, name)) {
      throw new Error(`Missing native schema definition: ${source.$ref}`);
    }
    root = { ...definitions[name], ...source };
    delete root.$ref;
  }
  const defs = {};
  const queue = [root];
  for (const value of queue) {
    if (value && typeof value === "object") {
      if (value.$ref) {
        const name = value.$ref.replace("#/$defs/", "");
        if (!Object.hasOwn(definitions, name)) {
          throw new Error(`Missing native schema definition: ${name}`);
        }
        if (!Object.hasOwn(defs, name)) {
          defs[name] = definitions[name];
          queue.push(defs[name]);
        }
      }
      queue.push(...Object.values(value));
    }
  }
  const references = Object.keys(defs).length ? { $defs: defs } : {};
  const native = { ...root, ...references };
  const wrapped = root.type !== "object";
  const properties = Object.fromEntries(
    Object.entries(root.properties ?? {}).map(([name, schema]) => [
      name,
      schema === true ? {} : schema
    ])
  );
  for (const [name, schema] of Object.entries(properties)) {
    if (schema === false) {
      properties[name] = { not: {} };
    }
  }
  return {
    inputSchema: wrapped
      ? {
          additionalProperties: false,
          properties: { params: root },
          ...(required ? { required: ["params"] } : {}),
          type: "object",
          ...references
        }
      : { ...native, ...(root.properties ? { properties } : {}) },
    native,
    wrapped
  };
};

/**
 * Validate a native client request without changing or stripping its parameters.
 * Refuse other protocol directions and unknown methods before connecting.
 */
export const validateRequest = async (method, params) => {
  const { protocols, validators } = await loadProtocol();
  const entry = protocols.clientRequest.find((item) => item.method === method);
  if (!entry) {
    throw new Error(`Not a native ClientRequest method: ${method}`);
  }
  if (!validators.has(method)) {
    validators.set(
      method,
      new AjvJsonSchemaValidator().getValidator(entry.request)
    );
  }
  const result = validators.get(method)({
    id: 0,
    method,
    ...(params === undefined ? {} : { params })
  });
  if (!result.valid) {
    throw new Error(
      `Invalid ${method} request parameters: ${result.errorMessage}`
    );
  }
};
