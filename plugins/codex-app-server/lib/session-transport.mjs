import { once } from "node:events";

import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { serializeMessage } from "@modelcontextprotocol/sdk/shared/stdio.js";

/**
 * Retire native resources on SDK closure and cancel pending stdout drains on shutdown.
 * Framing and input limits remain owned by the SDK.
 */
export class SessionTransport extends StdioServerTransport {
  lifetime = new AbortController();

  constructor(
    cleanup,
    stdin = process.stdin,
    stdout = process.stdout,
    options = {}
  ) {
    super(stdin, stdout, options);
    this.cleanup = cleanup;
    this.stdout = stdout;
  }

  async close() {
    this.lifetime.abort(new Error("MCP transport is closed"));
    this.cleanup();
    await super.close();
  }

  async send(message) {
    this.lifetime.signal.throwIfAborted();
    if (!this.stdout.write(serializeMessage(message))) {
      await once(this.stdout, "drain", { signal: this.lifetime.signal });
    }
  }
}
