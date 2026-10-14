import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { once } from "node:events";
import { mkdir, mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import type { IncomingMessage, ServerResponse } from "node:http";
import type { Socket } from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";
import { fileURLToPath } from "node:url";

import { create } from "tar";
import { expect, test } from "vitest";

import { downloadReleaseAssets, execute, verifiedExecutable } from "./run.js";

const withLocalServer = async (
  handler: (request: IncomingMessage, response: ServerResponse) => void,
  operation: (baseUrl: string) => Promise<void>
): Promise<void> => {
  const server = createServer(handler);
  const sockets = new Set<Socket>();
  server.on("connection", (socket) => {
    sockets.add(socket);
    socket.once("close", () => sockets.delete(socket));
  });
  const listening = once(server, "listening");
  server.listen(0, "127.0.0.1");
  await listening;
  const address = server.address();
  if (!address || typeof address === "string") {
    throw new Error("Missing local server address");
  }
  try {
    await operation(`http://127.0.0.1:${address.port}`);
  } finally {
    for (const socket of sockets) {
      socket.destroy();
    }
    const closed = once(server, "close");
    server.close();
    await closed;
  }
};

const makeResponseTracker = () => {
  const closedPaths = new Set<string>();
  const { promise: allClosed, resolve: resolveAllClosed } =
    Promise.withResolvers<null>();
  return {
    allClosed,
    closedPaths,
    track: (requestPath: string, response: ServerResponse): void => {
      response.once("close", () => {
        closedPaths.add(requestPath);
        if (closedPaths.size === 2) {
          resolveAllClosed(null);
        }
      });
    }
  };
};

const serveOpenResponses = (
  tracker: ReturnType<typeof makeResponseTracker>,
  statusFor: (requestPath: string) => number,
  destroyPath?: string
): ((request: IncomingMessage, response: ServerResponse) => void) => {
  const pending = new Map<string, ServerResponse>();
  return (request, response) => {
    const requestPath = request.url ?? "";
    tracker.track(requestPath, response);
    pending.set(requestPath, response);
    if (pending.size === 2) {
      for (const [pendingPath, pendingResponse] of pending) {
        pendingResponse.writeHead(statusFor(pendingPath));
        pendingResponse.flushHeaders();
        pendingResponse.write("held response body");
        if (pendingPath === destroyPath) {
          const timeout = setTimeout(() => pendingResponse.destroy(), 25);
          pendingResponse.once("close", () => clearTimeout(timeout));
        }
      }
    }
  };
};

const waitForResponseClosures = async (
  allClosed: Promise<null>,
  closedPaths: Set<string>
): Promise<void> => {
  const controller = new AbortController();
  const result = await Promise.race([
    allClosed,
    delay(2000, "timeout", { signal: controller.signal })
  ]);
  controller.abort();
  if (result === "timeout") {
    throw new Error("Release response bodies remained open");
  }
  expect([...closedPaths].toSorted()).toEqual(["/archive", "/checksums"]);
};

const processExists = (pid: number): boolean => {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "ESRCH") {
      return false;
    }
    throw error;
  }
};

const signalListenerCounts = (): number[] =>
  ["SIGINT", "SIGTERM"].map((signal) => process.listenerCount(signal));

const archiveBytes = async (
  files: Readonly<Record<string, string | Uint8Array>>,
  members: readonly string[] = Object.keys(files)
): Promise<Buffer> => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-release-test-"));
  try {
    await Promise.all(
      Object.entries(files).map(async ([member, content]) => {
        await mkdir(path.dirname(path.join(directory, member)), {
          recursive: true
        });
        await writeFile(path.join(directory, member), content);
      })
    );
    return Buffer.concat(
      await create({ cwd: directory, gzip: true }, [...members]).collect()
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
};

const checksumIndex = (bytes: Uint8Array): string =>
  `${createHash("sha256").update(bytes).digest("hex")}  release.tar.gz`;

test("closes failed and sibling release response bodies", async () => {
  const tracker = makeResponseTracker();
  await withLocalServer(
    serveOpenResponses(tracker, (requestPath) =>
      requestPath === "/checksums" ? 503 : 200
    ),
    async (baseUrl) => {
      await expect(
        downloadReleaseAssets(`${baseUrl}/archive`, `${baseUrl}/checksums`)
      ).rejects.toThrow("503");
      await waitForResponseClosures(tracker.allClosed, tracker.closedPaths);
    }
  );
});

test("closes a sibling body when release body consumption fails", async () => {
  const tracker = makeResponseTracker();
  await withLocalServer(
    serveOpenResponses(tracker, () => 200, "/archive"),
    async (baseUrl) => {
      await expect(
        downloadReleaseAssets(`${baseUrl}/archive`, `${baseUrl}/checksums`)
      ).rejects.toThrow();
      await waitForResponseClosures(tracker.allClosed, tracker.closedPaths);
    }
  );
});

test.skipIf(process.platform === "win32").each(["SIGINT", "SIGTERM"] as const)(
  "stops its child and removes signal listeners on %s",
  async (interruption) => {
    const runnerPath = fileURLToPath(new URL("run.ts", import.meta.url));
    const fixtureSource =
      "console.log(process.pid); setTimeout(() => {}, 30000);";
    const wrapperSource = `import { execute } from ${JSON.stringify(runnerPath)};
const signalCounts = ["SIGINT", "SIGTERM"].map((signal) => process.listenerCount(signal));
const status = await execute(process.execPath, ["-e", ${JSON.stringify(fixtureSource)}]);
if (["SIGINT", "SIGTERM"].some((signal, index) => process.listenerCount(signal) !== signalCounts[index])) {
  throw new Error("execute retained a signal listener");
}
process.exitCode = status;`;
    const wrapper = spawn(
      process.execPath,
      ["--import", "tsx", "--input-type=module", "-e", wrapperSource],
      { stdio: ["ignore", "pipe", "ignore"] }
    );
    let childPid: number | undefined;
    try {
      const [output] = await once(wrapper.stdout, "data", {
        signal: AbortSignal.timeout(5000)
      });
      childPid = Number(output.toString().trim());
      if (!Number.isInteger(childPid)) {
        throw new TypeError("Fixture child did not report a valid process ID");
      }
      const wrapperClosed = once(wrapper, "close", {
        signal: AbortSignal.timeout(5000)
      });
      wrapper.kill(interruption);
      const [code, signal] = await wrapperClosed;
      expect(code).toBe(interruption === "SIGINT" ? 130 : 143);
      expect(signal).toBeNull();
      expect(processExists(childPid)).toBe(false);
    } finally {
      if (wrapper.exitCode === null && wrapper.signalCode === null) {
        const wrapperClosed = once(wrapper, "close", {
          signal: AbortSignal.timeout(2000)
        });
        wrapper.kill("SIGKILL");
        await wrapperClosed;
      }
      if (childPid !== undefined && processExists(childPid)) {
        process.kill(childPid, "SIGKILL");
      }
    }
  },
  10_000
);

test("cleans signal listeners after child success, spawn error, and signal exit", async () => {
  const originalListeners = signalListenerCounts();

  await expect(
    execute(process.execPath, ["-e", "process.exit(0)"])
  ).resolves.toBe(0);
  expect(signalListenerCounts()).toEqual(originalListeners);

  const directory = await mkdtemp(path.join(tmpdir(), "sinon-exec-error-"));
  try {
    await expect(
      execute(path.join(directory, "missing-executable"), [])
    ).rejects.toMatchObject({ code: "ENOENT" });
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
  expect(signalListenerCounts()).toEqual(originalListeners);

  await expect(
    execute(process.execPath, ["-e", 'process.kill(process.pid, "SIGTERM")'])
  ).rejects.toThrow("Promtool terminated by signal SIGTERM");
  expect(signalListenerCounts()).toEqual(originalListeners);
});

test("rejects a release archive whose checksum does not match", async () => {
  await expect(
    verifiedExecutable(
      new Uint8Array([1]),
      `${"0".repeat(64)}  release.tar.gz`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("rejects a checksum for another archive", async () => {
  await expect(
    verifiedExecutable(
      new Uint8Array([1]),
      `${"0".repeat(64)}  other.tar.gz`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("rejects ambiguous checksum entries for the selected archive", async () => {
  const bytes = new Uint8Array([1]);
  const checksum = checksumIndex(bytes);
  await expect(
    verifiedExecutable(
      bytes,
      `${checksum}\n${checksum}`,
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Invalid official release checksum");
});

test("selects only the requested regular executable from a verified archive", async () => {
  const payload = Buffer.from([0, 255, 128, 1]);
  const bytes = await archiveBytes({
    "release/promtool": payload,
    "release/unrelated": "other content"
  });
  const executable = await verifiedExecutable(
    bytes,
    checksumIndex(bytes),
    "release.tar.gz",
    "release/promtool"
  );
  expect(executable).toEqual(payload);
});

test("rejects a verified archive that omits its executable", async () => {
  const bytes = await archiveBytes({ "release/other": "content" });
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Missing release executable");
});

test("rejects duplicate executable entries in a verified archive", async () => {
  const bytes = await archiveBytes({ "release/promtool": "executable" }, [
    "release/promtool",
    "release/promtool"
  ]);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Ambiguous release executable");
});

test("rejects a symbolic link selected as the release executable", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-release-test-"));
  try {
    await mkdir(path.join(directory, "release"));
    await writeFile(path.join(directory, "release/other"), "executable");
    await symlink("other", path.join(directory, "release/promtool"));
    const bytes = Buffer.concat(
      await create({ cwd: directory, gzip: true }, [
        "release/other",
        "release/promtool"
      ]).collect()
    );
    await expect(
      verifiedExecutable(
        bytes,
        checksumIndex(bytes),
        "release.tar.gz",
        "release/promtool"
      )
    ).rejects.toThrow("Nonregular release executable");
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});

test("rejects a verified archive with an invalid format", async () => {
  const bytes = new Uint8Array([1]);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow("Unrecognized archive format");
});

test("rejects a verified archive with truncated gzip data", async () => {
  const archive = await archiveBytes({ "release/promtool": "executable" });
  const bytes = archive.subarray(0, -8);
  await expect(
    verifiedExecutable(
      bytes,
      checksumIndex(bytes),
      "release.tar.gz",
      "release/promtool"
    )
  ).rejects.toThrow();
});
