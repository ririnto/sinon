#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { existsSync, readdirSync } from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "..");
const PLUGINS = path.join(ROOT, "plugins");

const pluginRoots = (): readonly string[] =>
  readdirSync(PLUGINS, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(PLUGINS, entry.name))
    .filter((pluginRoot) =>
      existsSync(path.join(pluginRoot, ".claude-plugin", "plugin.json"))
    )
    .toSorted();

const validatePlugin = async (pluginRoot: string): Promise<string> => {
  const child = Bun.spawn(["claude", "plugin", "validate", pluginRoot], {
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
    throw new Error(
      `${path.relative(ROOT, pluginRoot)}: Claude validation failed: ${stderr.trim()} ${stdout.trim()}`
    );
  }
  return path.relative(ROOT, pluginRoot);
};

const main = async (): Promise<number> => {
  try {
    const validated = await Promise.all(pluginRoots().map(validatePlugin));
    for (const pluginRoot of validated) {
      console.log(JSON.stringify({ plugin: pluginRoot, result: "OK" }));
    }
    return 0;
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    return 1;
  }
};

process.exit(await main());
