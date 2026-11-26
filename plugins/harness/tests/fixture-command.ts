import { spawn } from "node:child_process";
import type {
  ChildProcess,
  SpawnOptions,
  SpawnSyncReturns
} from "node:child_process";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { setTimeout as delay } from "node:timers/promises";

type CommandOptions = Pick<SpawnOptions, "env" | "signal">;
type CommandResult = Pick<
  SpawnSyncReturns<Buffer>,
  "error" | "status" | "stderr" | "stdout"
>;
interface StartedCommand {
  readonly child: ChildProcess;
  /**
   * Process group of the command and of the processes it starts. Windows has none.
   */
  readonly group: number | undefined;
  readonly result: Promise<CommandResult>;
}

/**
 * Time within which the disposal of a fixture ends on every path.
 */
export const disposalBoundMs = 1000;
const drainMs = 400;
const stopGraceMs = 200;
const closeMs = 200;
const ownsGroup = process.platform !== "win32";

/**
 * Signal the process tree of a command. A tree that is already gone is no error.
 */
const stop = (started: StartedCommand, signal?: NodeJS.Signals): void => {
  try {
    if (started.group === undefined) {
      started.child.kill(signal);
    } else {
      process.kill(-started.group, signal);
    }
  } catch (error) {
    const gone =
      error instanceof Error && "code" in error && error.code === "ESRCH";
    if (!gone) {
      throw error;
    }
  }
};

/**
 * Hold a shell that stops the group of a command when this worker goes away before a cleanup runs.
 * The shell reads its input until the worker ends it with a line, or until the worker dies.
 * It owns its session, so a stop aimed at the group of the worker cannot end it first.
 */
const holdGroup = (group: number): ChildProcess =>
  spawn(
    "sh",
    [
      "-c",
      'trap "" INT TERM HUP; read release && exit; kill -9 -"$1"',
      "sh",
      String(group)
    ],
    { detached: true, stdio: ["pipe", "ignore", "ignore"] }
  );

/**
 * Stop the tree of a command, and stop it by force when it stays open through its grace.
 */
const requestStop = (started: StartedCommand): void => {
  stop(started);
  const forced = setTimeout(() => {
    stop(started, "SIGKILL");
  }, stopGraceMs);
  forced.unref();
  started.child.once("close", () => {
    clearTimeout(forced);
  });
};

/**
 * Report whether every result of the commands settles within the bound.
 */
const settlesWithin = async (
  commands: Iterable<StartedCommand>,
  boundMs: number
): Promise<boolean> => {
  const results: Promise<CommandResult>[] = [];
  for (const { result } of commands) {
    results.push(result);
  }
  const settled = await Promise.race([
    Promise.allSettled(results),
    delay(boundMs, false, { ref: false })
  ]);
  return settled !== false;
};

const collectCommand = (
  command: string,
  args: readonly string[],
  options: CommandOptions & { cwd: string }
): StartedCommand => {
  const child = spawn(command, args, {
    ...options,
    detached: ownsGroup,
    stdio: ["ignore", "pipe", "pipe"]
  });
  const stdout: Buffer[] = [];
  const stderr: Buffer[] = [];
  const closed = Promise.withResolvers<number | null>();
  let error: Error | undefined;
  child.on("error", (commandError) => {
    error ??= commandError;
  });
  child.stdout.on("data", (chunk: Buffer) => {
    stdout.push(chunk);
  });
  child.stderr.on("data", (chunk: Buffer) => {
    stderr.push(chunk);
  });
  const result = (async (): Promise<CommandResult> => {
    const status = await closed.promise;
    return {
      ...(error === undefined ? { status } : { error, status: null }),
      stdout: Buffer.concat(stdout),
      stderr: Buffer.concat(stderr)
    };
  })();
  const started: StartedCommand = {
    child,
    group: ownsGroup ? child.pid : undefined,
    result
  };
  const stopOnAbort = (): void => {
    requestStop(started);
  };
  const guard =
    started.group === undefined ? undefined : holdGroup(started.group);
  if (guard !== undefined) {
    const report = (guardError: Error): void => {
      error ??= guardError;
    };
    guard.on("error", report);
    guard.stdin?.on("error", report);
  }
  options.signal?.addEventListener("abort", stopOnAbort);
  child.once("close", (status) => {
    options.signal?.removeEventListener("abort", stopOnAbort);
    guard?.stdin?.end("release\n");
    closed.resolve(status);
  });
  return started;
};

/**
 * Keep a fixture directory alive until its commands close and their output drains.
 * Disposal ends within `disposalBoundMs`, stops the process tree of a command that
 * is still open, removes the directory, and then throws.
 */
export const createCommandFixture = async (prefix: string) => {
  const directory = await mkdtemp(path.join(tmpdir(), prefix));
  const running = new Set<StartedCommand>();
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
      const started = collectCommand(command, args, {
        ...options,
        cwd: directory
      });
      running.add(started);
      try {
        return await started.result;
      } finally {
        running.delete(started);
      }
    },
    async [Symbol.asyncDispose](): Promise<void> {
      closing = true;
      if (await settlesWithin(running, drainMs)) {
        await rm(directory, { force: true, recursive: true });
        return;
      }
      for (const started of running) {
        requestStop(started);
      }
      const stopped = await settlesWithin(running, stopGraceMs + closeMs);
      await rm(directory, { force: true, recursive: true });
      throw new Error(
        stopped
          ? `A fixture command was stopped inside its ${disposalBoundMs} ms disposal bound`
          : `A fixture command outlived its ${disposalBoundMs} ms disposal bound`
      );
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

/**
 * Wait for a timed-out fixture operation to drain within a bound, so that its own cleanup runs first.
 * An operation that outlives the bound keeps its fixture directory.
 */
export const drainWithin = async (
  boundMs: number,
  operation: Promise<unknown>
): Promise<void> => {
  await Promise.race([
    Promise.allSettled([operation]),
    delay(boundMs, false, { ref: false })
  ]);
};
