import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import test from "node:test";

import { pluginRoot, readJson, readText } from "./helpers.mjs";

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

const taggedSection = (context, tagName) => {
  const match = context.match(
    new RegExp(`<${tagName}>\\n([\\s\\S]*?)\\n<\\/${tagName}>`, "u")
  );
  assert.ok(match, `missing <${tagName}> section`);
  return match[1];
};

test("startup injects the Main Agent contract only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "startup" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_MAIN_V2/u);
  assert.ok(taggedSection(context, "operating_policy").trim().length > 0);
  assert.ok(taggedSection(context, "main_agent_contract").trim().length > 0);
  assert.doesNotMatch(context, /WORKGRAPH_COMPACT_V2|WORKGRAPH_SUBAGENT_V2/u);
  assert.doesNotMatch(context, /^---$/mu);
});

test("clear injects the Main Agent contract only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "clear" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_MAIN_V2/u);
  assert.ok(taggedSection(context, "operating_policy").trim().length > 0);
  assert.ok(taggedSection(context, "main_agent_contract").trim().length > 0);
  assert.doesNotMatch(context, /WORKGRAPH_COMPACT_V2|WORKGRAPH_SUBAGENT_V2/u);
});

test("compact injects conditional recovery only", () => {
  const context = contextFrom(
    invokeHook({ hook_event_name: "SessionStart", source: "compact" }),
    "SessionStart"
  );
  assert.match(context, /WORKGRAPH_COMPACT_V2/u);
  assert.equal((context.match(/WORKGRAPH_MAIN_V2/gu) ?? []).length, 1);
  assert.doesNotMatch(context, /WORKGRAPH_SUBAGENT_V2|<operating_policy>/u);
});

test("supported contexts include the Skills base directory exactly once", () => {
  const contexts = [
    contextFrom(
      invokeHook({ hook_event_name: "SessionStart", source: "startup" }),
      "SessionStart"
    ),
    contextFrom(
      invokeHook({ hook_event_name: "SessionStart", source: "clear" }),
      "SessionStart"
    ),
    contextFrom(
      invokeHook({ hook_event_name: "SessionStart", source: "compact" }),
      "SessionStart"
    ),
    contextFrom(
      invokeHook({ hook_event_name: "SubagentStart" }),
      "SubagentStart"
    )
  ];
  for (const context of contexts) {
    assert.match(
      context,
      new RegExp(
        `^Workgraph Skills base directory: ${path.resolve(pluginRoot, "skills")}$`,
        "mu"
      )
    );
    assert.equal(
      (context.match(/Workgraph Skills base directory: /gu) ?? []).length,
      1
    );
  }
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
  assert.match(context, /WORKGRAPH_SUBAGENT_V2/u);
  assert.ok(taggedSection(context, "operating_policy").trim().length > 0);
  assert.doesNotMatch(
    context,
    /WORKGRAPH_MAIN_V2|WORKGRAPH_COMPACT_V2|<main_agent_contract>/u
  );
  assert.match(context, /Status: COMPLETED \| BLOCKED \| FAILED \| UNKNOWN/u);
});

test("the shared policy has one source", async () => {
  const [sessionCore, subagentSource] = await Promise.all([
    readText("skills/session-core/SKILL.md"),
    readText("hooks/subagent-context.md")
  ]);
  const subagentContext = contextFrom(
    invokeHook({ hook_event_name: "SubagentStart" }),
    "SubagentStart"
  );
  const policy = taggedSection(subagentContext, "operating_policy");
  const sourcePolicy = taggedSection(sessionCore, "operating_policy");
  assert.equal(policy, sourcePolicy);
  assert.equal((sessionCore.match(/<operating_policy>/gu) ?? []).length, 1);
  assert.equal((sessionCore.match(/<\/operating_policy>/gu) ?? []).length, 1);
  assert.doesNotMatch(subagentSource, /<operating_policy>/u);
  assert.ok(policy.trim().length > 0);
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
