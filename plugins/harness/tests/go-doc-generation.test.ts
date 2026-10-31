import { spawn, spawnSync } from "node:child_process";
import { once } from "node:events";
import { cp, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import net from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";

import { expect, onTestFinished, test } from "vitest";

import { configureBaseline } from "../scripts/configure.js";

const fetchDocumentation = async (
  url: string,
  remaining: number
): Promise<string> => {
  if (remaining === 0) {
    return "";
  }
  try {
    const response = await fetch(url);
    if (response.ok) {
      return await response.text();
    }
  } catch {
    // The server is still starting.
  }
  await delay(500);
  return fetchDocumentation(url, remaining - 1);
};

test.skipIf(!process.env.HARNESS_GO_DOCS_TEST)(
  "the generated Go profile serves its module's API documentation",
  async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-go-docs-"));
    onTestFinished(() => rm(directory, { force: true, recursive: true }));
    await cp(path.join(import.meta.dirname, "fixtures/go-api"), directory, {
      recursive: true
    });
    await configureBaseline(directory, "go", "eligible");
    const manifest = JSON.parse(
      await readFile(path.join(directory, "package.json"), "utf-8")
    );
    const server = net.createServer();
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    const address = server.address();
    if (!address || typeof address === "string") {
      throw new Error("No fixture port assigned.");
    }
    server.close();
    await once(server, "close");
    const child = spawn(
      "sh",
      [
        "-c",
        `exec ${manifest.scripts["go:docs"]} -http=127.0.0.1:${address.port}`
      ],
      { cwd: directory, stdio: "pipe" }
    );
    let output = "";
    child.stderr.on("data", (chunk: Buffer) => {
      output += chunk.toString();
    });
    onTestFinished(async () => {
      if (child.exitCode === null) {
        child.kill("SIGTERM");
        await once(child, "exit");
      }
    });
    const page = await fetchDocumentation(
      `http://127.0.0.1:${address.port}/example.com/harness-docs`,
      120
    );
    expect(page, output).toContain("Label returns the display label for id");
    expect(page).toContain("ErrEmptyID");
  },
  120_000
);

test.skipIf(!process.env.HARNESS_GO_DOCS_TEST)(
  "Go documentation lint reports an unused link without requiring trivial comments",
  async () => {
    const directory = await mkdtemp(path.join(tmpdir(), "sinon-go-doc-lint-"));
    onTestFinished(() => rm(directory, { force: true, recursive: true }));
    await cp(path.join(import.meta.dirname, "fixtures/go-api"), directory, {
      recursive: true
    });
    const configuration = path.join(
      import.meta.dirname,
      "../tooling/go/.golangci.yaml"
    );
    const lint = () =>
      spawnSync("golangci-lint", ["run", "-c", configuration, "./..."], {
        cwd: directory,
        encoding: "utf-8"
      });
    const source = path.join(directory, "lookup.go");
    const correct = await readFile(source, "utf-8");
    expect(lint().status).toBe(0);
    await writeFile(
      source,
      correct.replace(
        "// It returns [ErrEmptyID] when id is empty.\n",
        "// It returns [ErrEmptyID] when id is empty.\n//\n// [unused]: https://example.com\n"
      )
    );
    const warning = lint();
    expect(warning.status).toBe(1);
    expect(warning.stdout).toContain("godoc has unused link");
    await writeFile(source, correct);
    expect(lint().status).toBe(0);
  },
  120_000
);
