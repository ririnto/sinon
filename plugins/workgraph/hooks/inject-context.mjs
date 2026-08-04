#!/usr/bin/env node
// -*- coding: utf-8 -*-

import { readFile } from "node:fs/promises";
import path from "node:path";

const hookDir = import.meta.dirname;
const pluginRoot = path.resolve(hookDir, "..");

const compactContext = `WORKGRAPH_COMPACT_V2

If WORKGRAPH_MAIN_V2 is absent from the compacted context, load the \`session-core\` Skill before continuing.
If it is present, continue without loading it again.`;

const readStdin = async () => {
  let input = "";
  for await (const chunk of process.stdin) {
    input += chunk;
  }
  return input;
};

const parseInput = (input) => {
  if (input.trim() === "") {
    return {};
  }
  try {
    return JSON.parse(input.replace(/^﻿/u, ""));
  } catch {
    return {};
  }
};

const stripFrontmatter = (markdown) =>
  markdown.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/u, "").trim();

const extractTaggedSection = (markdown, tagName) => {
  const pattern = new RegExp(`<${tagName}>[\\s\\S]*?</${tagName}>`, "u");
  const match = markdown.match(pattern);
  if (!match) {
    throw new Error(`missing <${tagName}> section`);
  }
  return match[0].trim();
};

const loadContext = async (payload) => {
  const eventName = payload.hook_event_name ?? payload.hookEventName;
  if (eventName === "SessionStart") {
    if (payload.source === "startup" || payload.source === "clear") {
      const markdown = await readFile(
        path.resolve(pluginRoot, "skills", "session-core", "SKILL.md"),
        "utf-8"
      );
      return { context: stripFrontmatter(markdown), eventName };
    }
    if (payload.source === "compact") {
      return { context: compactContext, eventName };
    }
    return null;
  }
  if (eventName === "SubagentStart") {
    const [coreMarkdown, nodeMarkdown] = await Promise.all([
      readFile(
        path.resolve(pluginRoot, "skills", "session-core", "SKILL.md"),
        "utf-8"
      ),
      readFile(path.resolve(hookDir, "subagent-context.md"), "utf-8")
    ]);
    const policy = extractTaggedSection(
      stripFrontmatter(coreMarkdown),
      "operating_policy"
    );
    return { context: `${policy}\n\n${nodeMarkdown.trim()}`, eventName };
  }
  return null;
};

const payload = parseInput(await readStdin());
const result = await loadContext(payload);

if (result) {
  process.stdout.write(
    `${JSON.stringify({
      hookSpecificOutput: {
        additionalContext: result.context,
        hookEventName: result.eventName
      }
    })}\n`
  );
}
