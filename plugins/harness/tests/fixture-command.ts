import { spawn } from "node:child_process";
import type { SpawnOptions, SpawnSyncReturns } from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";

type CommandOptions = Pick<SpawnOptions, "env" | "signal">;
type CommandResult = Pick<
  SpawnSyncReturns<Buffer>,
  "error" | "status" | "stderr" | "stdout"
>;

const collectCommand = async (
  command: string,
  args: readonly string[],
  options: CommandOptions & { cwd: string }
): Promise<CommandResult> => {
  const child = spawn(command, args, {
    ...options,
    stdio: ["ignore", "pipe", "pipe"]
  });
  const stdout: Buffer[] = [];
  const stderr: Buffer[] = [];
  const closed = Promise.withResolvers<number | null>();
  let error: Error | undefined;
  child.on("error", (commandError) => {
    error ??= commandError;
  });
  child.once("close", closed.resolve);
  child.stdout.on("data", (chunk: Buffer) => {
    stdout.push(chunk);
  });
  child.stderr.on("data", (chunk: Buffer) => {
    stderr.push(chunk);
  });
  const status = await closed.promise;
  return {
    ...(error === undefined ? { status } : { error, status: null }),
    stdout: Buffer.concat(stdout),
    stderr: Buffer.concat(stderr)
  };
};

/**
 * Keep a fixture directory alive until its commands close and their output drains.
 */
export const createCommandFixture = async (prefix: string) => {
  const directory = await mkdtemp(path.join(tmpdir(), prefix));
  const running = new Set<Promise<CommandResult>>();
  let closing = false;
  return {
    directory,
    async run(
      command: string,
      args: readonly string[],
      options: CommandOptions = {}
    ): Promise<CommandResult> {
      if (closing) {
        throw new Error("Cannot start a command while removing its fixture");
      }
      const result = collectCommand(command, args, {
        ...options,
        cwd: directory
      });
      running.add(result);
      try {
        return await result;
      } finally {
        running.delete(result);
      }
    },
    async [Symbol.asyncDispose](): Promise<void> {
      closing = true;
      await Promise.allSettled(running);
      await rm(directory, { force: true, recursive: true });
    }
  };
};

/**
 * Finish sibling fixture operations before propagating a failure.
 */
export const waitForFixtureTasks = async (
  tasks: readonly Promise<unknown>[]
): Promise<void> => {
  for (const result of await Promise.allSettled(tasks)) {
    if (result.status === "rejected") {
      const cause: unknown = result.reason;
      throw cause instanceof Error
        ? cause
        : new Error("Fixture task failed", { cause });
    }
  }
};

/**
 * Wait for a fixture operation to settle and reject when it outlives its bound.
 */
export const settleWithin = async <T>(
  boundMs: number,
  operation: Promise<T>
): Promise<T> => {
  const expired = delay(boundMs, boundMs, { ref: false }).then((elapsed) => {
    throw new Error(`A fixture operation outlived its ${elapsed} ms bound`);
  });
  return await Promise.race([operation, expired]);
};
