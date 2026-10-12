#!/usr/bin/env node
import { parseArgs } from "node:util";

import { watchThreads } from "./lib/monitor.mjs";

const STATUSES = ["idle", "active", "notLoaded", "failed", "systemError"];

const main = async () => {
  const { values } = parseArgs({
    options: {
      status: { multiple: true, type: "string" },
      thread: { multiple: true, type: "string" },
      "timeout-seconds": { type: "string" }
    }
  });
  const threadIds =
    values.thread ??
    (process.env.CODEX_APP_SERVER_MONITOR_THREADS || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  const statuses =
    values.status ??
    (process.env.CODEX_APP_SERVER_MONITOR_STATUSES || STATUSES.join(","))
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  if (threadIds.length === 0) {
    console.error(
      "codex-thread-monitor: set CODEX_APP_SERVER_MONITOR_THREADS or pass --thread to select threads; monitoring is disabled"
    );
    return;
  }
  if (
    statuses.length === 0 ||
    statuses.some((status) => !STATUSES.includes(status))
  ) {
    throw new Error(`Monitor statuses must be one of: ${STATUSES.join(", ")}`);
  }
  const timeout =
    values["timeout-seconds"] === undefined
      ? null
      : Number(values["timeout-seconds"]);
  if (
    timeout !== null &&
    (!Number.isFinite(timeout) || timeout <= 0 || timeout > 1800)
  ) {
    throw new Error(
      "Monitor timeout must be greater than 0 and at most 1800 seconds"
    );
  }
  const stopped = new AbortController();
  const stop = () => stopped.abort();
  process.once("SIGINT", stop);
  process.once("SIGTERM", stop);
  const timer = timeout === null ? null : setTimeout(stop, timeout * 1000);
  try {
    await watchThreads({
      emit: (event) => process.stdout.write(`${JSON.stringify(event)}\n`),
      signal: stopped.signal,
      statuses,
      threadIds
    });
  } catch (error) {
    if (!stopped.signal.aborted) {
      throw error;
    }
  } finally {
    clearTimeout(timer);
    process.removeListener("SIGINT", stop);
    process.removeListener("SIGTERM", stop);
  }
};

try {
  await main();
} catch (error) {
  process.stdout.write(
    `${JSON.stringify({ error: error.message, event: "transport", status: "systemError", threadId: null })}\n`
  );
  process.exitCode = 1;
}
