import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import test from "node:test";

import { pluginRoot, readJson } from "./helpers.mjs";

const hookPath = path.resolve(pluginRoot, "hooks", "inject-context.mjs");

const invokeHook = (payload) => {
  const result = spawnSync(process.execPath, [hookPath], {
    cwd: pluginRoot,
    encoding: "utf-8",
    input: JSON.stringify(payload)
  });
  assert.equal(result.status, 0, result.stderr);
  if (result.stdout.trim() === "") {
    return null;
  }
  return JSON.parse(result.stdout);
};

const contextFrom = (output, eventName) => {
  assert.ok(output);
  assert.deepEqual(Object.keys(output), ["hookSpecificOutput"]);
  assert.equal(output.hookSpecificOutput.hookEventName, eventName);
  assert.equal(typeof output.hookSpecificOutput.additionalContext, "string");
  assert.ok(output.hookSpecificOutput.additionalContext.trim().length > 0);
  return output.hookSpecificOutput.additionalContext;
};

test("startup injects the Main Agent contract only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "startup" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_MAIN_V1/u);
  assert.doesNotMatch(context, /WORKGRAPH_COMPACT_V1|WORKGRAPH_SUBAGENT_V1/u);
  assert.doesNotMatch(context, /^---$/mu);
});

test("clear injects the Main Agent contract only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "clear" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_MAIN_V1/u);
  assert.doesNotMatch(context, /WORKGRAPH_COMPACT_V1|WORKGRAPH_SUBAGENT_V1/u);
});

test("compact injects conditional recovery only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "compact" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_COMPACT_V1/u);
  assert.match(context, /WORKGRAPH_MAIN_V1/u);
  assert.match(context, /session-core/u);
  assert.doesNotMatch(
    context,
    /WORKGRAPH_SUBAGENT_V1|smallest complete implementation/iu
  );
});

test("resume and unrelated events stay silent", () => {
  assert.equal(
    invokeHook({ hook_event_name: "SessionStart", source: "resume" }),
    null
  );
  assert.equal(invokeHook({ hook_event_name: "PostToolUse" }), null);
});

test("SubagentStart injects the bounded-worker contract only", () => {
  const context = contextFrom(
    invokeHook({
      agent_type: "general-purpose",
      hook_event_name: "SubagentStart"
    }),
    "SubagentStart"
  );
  assert.match(context, /WORKGRAPH_SUBAGENT_V1/u);
  assert.doesNotMatch(context, /WORKGRAPH_MAIN_V1|WORKGRAPH_COMPACT_V1/u);
  assert.match(context, /Status: COMPLETED \| BLOCKED \| FAILED \| UNKNOWN/u);
});

test("hook registration separates full, compact, and subagent injection", async () => {
  const config = await readJson("hooks/hooks.json");
  const sessionEntries = config.hooks.SessionStart;
  const subagentEntries = config.hooks.SubagentStart;
  assert.deepEqual(
    sessionEntries.map((entry) => entry.matcher),
    ["startup|clear", "compact"]
  );
  assert.equal(subagentEntries.length, 1);
  assert.equal(subagentEntries[0].matcher, undefined);
  for (const entry of [...sessionEntries, ...subagentEntries]) {
    assert.equal(entry.hooks.length, 1);
    const [handler] = entry.hooks;
    assert.equal(handler.type, "command");
    assert.match(handler.command, /inject-context\.mjs/u);
    assert.match(handler.commandWindows, /inject-context\.mjs/u);
    assert.ok(handler.timeout <= 5);
  }
});
