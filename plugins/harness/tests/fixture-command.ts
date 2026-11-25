import { spawn } from "node:child_process";
import type { SpawnOptions, SpawnSyncReturns } from "node:child_process";
import { once } from "node:events";

/**
 * Capture a fixture command's exit status and output while other cases run.
 */
export const runFixtureCommand = async (
  command: string,
  args: readonly string[],
  options: Pick<SpawnOptions, "cwd" | "env" | "signal">
): Promise<
  Pick<SpawnSyncReturns<Buffer>, "error" | "status" | "stderr" | "stdout">
> => {
  const child = spawn(command, args, {
    ...options,
    stdio: ["ignore", "pipe", "pipe"]
  });
  const stdout: Buffer[] = [];
  const stderr: Buffer[] = [];
  child.stdout.on("data", (chunk: Buffer) => {
    stdout.push(chunk);
  });
  child.stderr.on("data", (chunk: Buffer) => {
    stderr.push(chunk);
  });
  let status = null;
  let error;
  try {
    const completion: unknown[] = await once(child, "close");
    const [exitCode] = completion;
    status = typeof exitCode === "number" ? exitCode : null;
  } catch (error_) {
    if (!(error_ instanceof Error)) {
      throw error_;
    }
    error = error_;
  }
  return {
    ...(error === undefined ? {} : { error }),
    status,
    stdout: Buffer.concat(stdout),
    stderr: Buffer.concat(stderr)
  };
};
