#!/usr/bin/env node
// -*- coding: utf-8 -*-

import { readFile } from "node:fs/promises";
import path from "node:path";

const hookDir = import.meta.dirname;
const pluginRoot = path.resolve(hookDir, "..");

const skillsBaseContext = `Workgraph Skills base directory: ${path.resolve(pluginRoot, "skills")}`;

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
  const match = markdown.match(
    new RegExp(`<${tagName}>[\\s\\S]*?</${tagName}>`, "u")
  );
  if (!match) {
    throw new Error(`missing <${tagName}> section`);
  }
  return match[0].trim();
};

const loadContext = async (payload) => {
  const eventName = payload.hook_event_name ?? payload.hookEventName;
  if (eventName === "SessionStart") {
    if (payload.source === "startup" || payload.source === "clear") {
      return {
        context: `${stripFrontmatter(
          await readFile(
            path.resolve(pluginRoot, "skills", "session-core", "SKILL.md"),
            "utf-8"
          )
        )}\n\n${skillsBaseContext}`,
        eventName: "SessionStart"
      };
    }
    if (payload.source === "compact") {
      return {
        context: `WORKGRAPH_COMPACT_V2

If WORKGRAPH_MAIN_V2 is absent from the compacted context, load the \`session-core\` Skill before continuing.
If it is present, continue without loading it again.

${skillsBaseContext}`,
        eventName: "SessionStart"
      };
    }
  }
  if (eventName === "SubagentStart") {
    return {
      context: `${extractTaggedSection(
        stripFrontmatter(
          await readFile(
            path.resolve(pluginRoot, "skills", "session-core", "SKILL.md"),
            "utf-8"
          )
        ),
        "operating_policy"
      )}\n\n${String(
        await readFile(path.resolve(hookDir, "subagent-context.md"), "utf-8")
      ).trim()}\n\n${skillsBaseContext}`,
      eventName: "SubagentStart"
    };
  }
  return null;
};

const result = await loadContext(parseInput(await readStdin()));

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
