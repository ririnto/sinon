#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { statSync } from "node:fs";
import path from "node:path";

import { resolveInstructionChain } from "./instruction-contract.js";

type ProbeTarget = Readonly<{
  directory: string;
  markers: readonly string[];
  name: string;
}>;

type ProbeResult = Readonly<{
  bytes: number;
  chain: readonly string[];
  name: string;
}>;

const ROOT = path.resolve(import.meta.dirname, "..");
const LIMIT = 32 * 1024;

const TARGETS: readonly ProbeTarget[] = [
  {
    directory: ROOT,
    markers: ["# Sinon Project Rules"],
    name: "root"
  },
  {
    directory: path.join(ROOT, "plugins", "agent-capability-kit", "agents"),
    markers: [
      "# Sinon Project Rules",
      "# Plugin Package Rules",
      "# Agent Capability Kit Rules"
    ],
    name: "agent-capability-kit"
  },
  {
    directory: path.join(ROOT, "plugins", "harness", "agents"),
    markers: [
      "# Sinon Project Rules",
      "# Plugin Package Rules",
      "# Harness Plugin Rules"
    ],
    name: "harness"
  },
  {
    directory: path.join(ROOT, "plugins", "spring", "agents"),
    markers: [
      "# Sinon Project Rules",
      "# Plugin Package Rules",
      "# Spring Plugin Rules"
    ],
    name: "spring"
  },
  {
    directory: path.join(ROOT, "plugins", "workspace-workflow", "agents"),
    markers: [
      "# Sinon Project Rules",
      "# Plugin Package Rules",
      "# Workspace Workflow Rules"
    ],
    name: "workspace-workflow"
  }
];

const runProbe = async (target: ProbeTarget): Promise<ProbeResult> => {
  const chain = resolveInstructionChain(ROOT, target.directory);
  const bytes = chain.reduce(
    (total, filePath) => total + statSync(filePath).size,
    0
  );
  if (bytes >= LIMIT) {
    throw new Error(`${target.name}: instruction chain is ${bytes} bytes`);
  }
  const child = Bun.spawn(
    ["codex", "debug", "prompt-input", "instruction discovery probe"],
    {
      cwd: target.directory,
      stderr: "pipe",
      stdout: "pipe"
    }
  );
  const [exitCode, stdout, stderr] = await Promise.all([
    child.exited,
    new Response(child.stdout).text(),
    new Response(child.stderr).text()
  ]);
  if (exitCode !== 0) {
    throw new Error(
      `${target.name}: codex prompt-input failed: ${stderr.trim()}`
    );
  }
  for (const marker of target.markers) {
    if (!stdout.includes(marker)) {
      throw new Error(`${target.name}: live prompt is missing ${marker}`);
    }
  }
  return {
    bytes,
    chain: chain.map((filePath) => path.relative(ROOT, filePath)),
    name: target.name
  };
};

const main = async (): Promise<number> => {
  try {
    const results = await Promise.all(TARGETS.map(runProbe));
    for (const result of results) {
      console.log(JSON.stringify(result));
    }
    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return 1;
  }
};

process.exit(await main());
