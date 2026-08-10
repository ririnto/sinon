#!/usr/bin/env node
// -*- coding: utf-8 -*-

import { readFile } from "node:fs/promises";
import path from "node:path";

const pluginRoot = path.resolve(import.meta.dirname, "..");
const emitContext = async (contextFile, eventName) => {
  process.stdout.write(
    `${JSON.stringify({
      hookSpecificOutput: {
        additionalContext: String(await readFile(contextFile, "utf-8")).trim(),
        hookEventName: eventName
      }
    })}\n`
  );
};
let input = "";
for await (const chunk of process.stdin) {
  input += chunk;
}
const payload = JSON.parse(input.replace(/^﻿/u, ""));
const eventName = payload.hook_event_name;
if (eventName === "SubagentStart") {
  await emitContext(
    path.resolve(pluginRoot, "hooks", "subagent-context.md"),
    eventName
  );
} else if (eventName === "SessionStart") {
  if (payload.source === "startup" || payload.source === "clear") {
    await emitContext(
      path.resolve(pluginRoot, "skills", "main-agent-contract", "SKILL.md"),
      eventName
    );
  } else if (payload.source === "compact") {
    await emitContext(
      path.resolve(pluginRoot, "hooks", "compact-context.md"),
      eventName
    );
  }
}
