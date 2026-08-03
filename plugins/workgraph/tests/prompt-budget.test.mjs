import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import test from "node:test";

import { pluginRoot } from "./helpers.mjs";

const hookPath = path.resolve(pluginRoot, "hooks", "inject-context.mjs");

const injectedContext = (payload) => {
  const result = spawnSync(process.execPath, [hookPath], {
    cwd: pluginRoot,
    encoding: "utf-8",
    input: JSON.stringify(payload)
  });
  assert.equal(result.status, 0, result.stderr);
  const output = JSON.parse(result.stdout);
  return output.hookSpecificOutput.additionalContext;
};

test("injected contracts stay within their context budgets", () => {
  const main = injectedContext({
    hook_event_name: "SessionStart",
    source: "startup"
  });
  const compact = injectedContext({
    hook_event_name: "SessionStart",
    source: "compact"
  });
  const subagent = injectedContext({ hook_event_name: "SubagentStart" });
  assert.ok(
    main.length <= 2200,
    `Main Agent contract is ${main.length} characters`
  );
  assert.ok(
    compact.length <= 600,
    `compact recovery is ${compact.length} characters`
  );
  assert.ok(
    subagent.length <= 2400,
    `Sub Agent contract is ${subagent.length} characters`
  );
});
