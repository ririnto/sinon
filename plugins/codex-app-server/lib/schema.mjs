import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { promisify } from "node:util";

import { z } from "zod";

const PROTOCOLS = {
  clientNotification: "ClientNotification.json",
  clientRequest: "ClientRequest.json",
  serverNotification: "ServerNotification.json",
  serverRequest: "ServerRequest.json"
};
let cached;

const generate = async (binary, version) => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-codex-schema-"));
  try {
    await promisify(execFile)(
      binary,
      ["app-server", "generate-json-schema", "--out", directory],
      { timeout: 30_000 }
    );
    const protocols = await Promise.all(
      Object.entries(PROTOCOLS).map(async ([kind, file]) => {
        const source = await readFile(path.join(directory, file), "utf-8");
        const schema = JSON.parse(
          source.replaceAll("#/definitions/", "#/$defs/")
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
          }))
        ];
      })
    );
    return { protocols: Object.fromEntries(protocols), version };
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

/**
 * Share one process-local native schema generation per CLI executable and version.
 * Discovery and raw calls recheck the version; failed generations may be retried.
 */
export const loadProtocol = async () => {
  const binary = process.env.CODEX_BIN || "codex";
  const { stdout } = await promisify(execFile)(binary, ["--version"], {
    timeout: 30_000
  });
  const version = stdout.trim();
  if (!version) {
    throw new Error("Codex returned an empty version");
  }
  const key = `${binary}\0${version}`;
  if (cached?.key !== key) {
    cached = { key, promise: generate(binary, version) };
  }
  const current = cached;
  try {
    return await current.promise;
  } catch (error) {
    if (cached === current) {
      cached = undefined;
    }
    throw error;
  }
};

/**
 * Validate a native client request without changing or stripping its parameters.
 * Refuse other protocol directions and unknown methods before connecting.
 */
export const validateRequest = async (method, params) => {
  const { protocols } = await loadProtocol();
  const entry = protocols.clientRequest.find((item) => item.method === method);
  if (!entry) {
    throw new Error(`Not a native ClientRequest method: ${method}`);
  }
  const result = z.fromJSONSchema(entry.request).safeParse({
    id: 0,
    method,
    ...(params === undefined ? {} : { params })
  });
  if (!result.success) {
    throw new Error(
      `Invalid ${method} request parameters: ${result.error.message}`
    );
  }
};
