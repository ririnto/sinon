#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import path from "node:path";

type Probe = Readonly<{
  effort: "low" | "medium";
  model: "gpt-5.6-luna" | "gpt-5.6-sol" | "gpt-5.6-terra";
}>;

type JsonEvent = Readonly<{
  item?: Readonly<{
    text?: unknown;
    type?: unknown;
  }>;
  type?: unknown;
}>;

const ROOT = path.resolve(import.meta.dirname, "..");
const CODEX_VERSION = "0.144.1";
const PROBES: readonly Probe[] = [
  { effort: "medium", model: "gpt-5.6-sol" },
  { effort: "medium", model: "gpt-5.6-terra" },
  { effort: "low", model: "gpt-5.6-luna" }
];

const runVersion = async (command: readonly string[]): Promise<string> => {
  const child = Bun.spawn([...command], {
    cwd: ROOT,
    stderr: "pipe",
    stdout: "pipe"
  });
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text()
  ]);
  if (exitCode !== 0) {
    throw new Error(`${command.join(" ")} failed: ${stderr.trim()}`);
  }
  return stdout.trim();
};

const runCodexProbe = async (probe: Probe): Promise<void> => {
  const child = Bun.spawn(
    [
      "bunx",
      `@openai/codex@${CODEX_VERSION}`,
      "exec",
      "--ephemeral",
      "--ignore-user-config",
      "-m",
      probe.model,
      "-c",
      `model_reasoning_effort=${probe.effort}`,
      "-s",
      "read-only",
      "--json",
      "-"
    ],
    {
      cwd: ROOT,
      stderr: "pipe",
      stdin: "pipe",
      stdout: "pipe"
    }
  );
  child.stdin.write("Return exactly OK. Do not use tools.\n");
  child.stdin.end();
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text()
  ]);
  if (exitCode !== 0) {
    throw new Error(
      `${probe.model}/${probe.effort} failed with temporary Codex ${CODEX_VERSION}: ${stderr.trim()} ${stdout.trim()}`
    );
  }
  const events = stdout
    .split(/\r?\n/u)
    .filter((line) => line.trim() !== "")
    .map((line) => JSON.parse(line) as JsonEvent);
  const accepted = events.some(
    (event) =>
      event.type === "item.completed" &&
      event.item?.type === "agent_message" &&
      event.item.text === "OK"
  );
  if (!accepted) {
    throw new Error(`${probe.model}/${probe.effort} did not return OK`);
  }
  console.log(
    JSON.stringify({
      codexVersion: CODEX_VERSION,
      effort: probe.effort,
      model: probe.model,
      response: "OK",
      verification: "routing-request-accepted"
    })
  );
};

const main = async (): Promise<number> => {
  try {
    const [installedCodex, claudeHelp, temporaryCodex] = await Promise.all([
      runVersion(["codex", "--version"]),
      runVersion(["claude", "--help"]),
      runVersion(["bunx", `@openai/codex@${CODEX_VERSION}`, "--version"])
    ]);
    if (!claudeHelp.includes("--effort")) {
      throw new Error("installed Claude CLI does not expose --effort");
    }
    console.log(
      JSON.stringify({
        installedCodex,
        temporaryCodex,
        verifiedTemporaryVersion: CODEX_VERSION
      })
    );
    await Promise.all(PROBES.map(runCodexProbe));
    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return 1;
  }
};

process.exit(await main());
