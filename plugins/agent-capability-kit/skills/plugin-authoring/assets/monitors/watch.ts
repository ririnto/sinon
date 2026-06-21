#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

const dataDir = process.env["CLAUDE_PLUGIN_DATA"] ?? ".";
const stateDir = path.join(dataDir, "monitor-state");
const stateFile = path.join(stateDir, "example-monitor.txt");

await mkdir(stateDir, { recursive: true });
await writeFile(stateFile, `${new Date().toISOString()}\n`, "utf-8");
console.log(`example-monitor wrote ${stateFile}`);
