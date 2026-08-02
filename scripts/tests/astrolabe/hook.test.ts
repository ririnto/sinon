/*<!--markdownlint-disable-file MD041-->*/
import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import path from "node:path";

import { repositoryPaths } from "../../test-support/paths.js";

const pluginRoot = repositoryPaths.astrolabeRoot;
const hookPath = path.join(pluginRoot, "hooks/astrolabe-hook.mjs");
interface HookCommand {
  type: "command";
  command: string;
  timeout: number;
}
interface HookGroup {
  matcher: string;
  hooks: HookCommand[];
}
interface HookConfiguration {
  hooks: {
    SessionStart: HookGroup[];
    SubagentStart: HookGroup[];
    [eventName: string]: HookGroup[];
  };
}
interface HookEnvelope {
  hookSpecificOutput: {
    hookEventName: "SessionStart" | "SubagentStart";
    additionalContext: string;
  };
}
const readHooks = (): HookConfiguration =>
  JSON.parse(
    readFileSync(path.join(pluginRoot, "hooks/hooks.json"), "utf-8")
  ) as HookConfiguration;
const removeFrontmatter = (text: string): string =>
  text.replace(/^---[\s\S]*?---\s*/u, "");
const rewriteReferenceLinks = (text: string, skillRoot: string): string =>
  text.replaceAll(
    /(?<=\]\()(?:(?:\.\/)?references\/[^)\s]+)/gu,
    (referencePath: string) =>
      path.resolve(skillRoot, referencePath.replace(/^\.\//u, ""))
  );
const readSkillBody = (skillPath: string): string => {
  const absoluteSkillPath = path.join(pluginRoot, skillPath);
  return rewriteReferenceLinks(
    removeFrontmatter(readFileSync(absoluteSkillPath, "utf-8")),
    path.dirname(absoluteSkillPath)
  );
};
const roleSkillRoots = {
  main: "skills/orchestrating-work/SKILL.md",
  subagent: "skills/executing-delegated-work/SKILL.md"
} as const;
const runHook = async (
  role: string,
  stdin?: string,
  environment?: NodeJS.ProcessEnv
): Promise<{ exitCode: number; stderr: string; stdout: string }> => {
  const child = Bun.spawn(["node", hookPath, role], {
    env: environment ?? {
      ...process.env,
      CLAUDE_PLUGIN_ROOT: pluginRoot
    },
    stderr: "pipe",
    stdin: "pipe",
    stdout: "pipe"
  });
  if (stdin !== undefined) {
    child.stdin.write(stdin);
  }
  child.stdin.end();
  const [exitCode, stderr, stdout] = await Promise.all([
    child.exited,
    new Response(child.stderr).text(),
    new Response(child.stdout).text()
  ]);
  return { exitCode, stderr, stdout };
};

test("hook configuration declares only the two supported lifecycle events", () => {
  const hooks = readHooks();
  expect(Object.keys(hooks.hooks).toSorted()).toEqual([
    "SessionStart",
    "SubagentStart"
  ]);
  expect(hooks.hooks.SessionStart).toHaveLength(1);
  expect(hooks.hooks.SubagentStart).toHaveLength(1);
  expect(hooks.hooks.SessionStart[0].matcher).toBe("startup|clear|compact");
  expect(hooks.hooks.SubagentStart[0].matcher).toBe(".*");
  expect(hooks.hooks.SessionStart[0].hooks).toHaveLength(1);
  expect(hooks.hooks.SubagentStart[0].hooks).toHaveLength(1);
  expect(hooks.hooks.SessionStart[0].hooks[0]).toEqual({
    command: `node "\${CLAUDE_PLUGIN_ROOT}/hooks/astrolabe-hook.mjs" main`,
    timeout: 5,
    type: "command"
  });
  expect(hooks.hooks.SubagentStart[0].hooks[0]).toEqual({
    command: `node "\${CLAUDE_PLUGIN_ROOT}/hooks/astrolabe-hook.mjs" subagent`,
    timeout: 5,
    type: "command"
  });
});

test("main hook emits the canonical orchestration skill body", async () => {
  const result = await runHook("main", JSON.stringify({ source: "startup" }));
  expect(result.exitCode).toBe(0);
  const envelope = JSON.parse(result.stdout) as HookEnvelope;
  expect(envelope.hookSpecificOutput.hookEventName).toBe("SessionStart");
  expect(envelope).toEqual({
    hookSpecificOutput: {
      additionalContext: readSkillBody(roleSkillRoots.main),
      hookEventName: "SessionStart"
    }
  });
  expect(envelope.hookSpecificOutput.additionalContext).toContain(
    path.resolve(
      pluginRoot,
      path.dirname(roleSkillRoots.main),
      "references/context-graph.md"
    )
  );
  expect(envelope.hookSpecificOutput.additionalContext).not.toContain(
    "](references/context-graph.md)"
  );
  expect(envelope.hookSpecificOutput.additionalContext).not.toBe(
    readSkillBody(roleSkillRoots.subagent)
  );
});

test("subagent hook emits the canonical delegated-work skill body", async () => {
  const result = await runHook(
    "subagent",
    JSON.stringify({ agentType: "Explore" })
  );
  expect(result.exitCode).toBe(0);
  const envelope = JSON.parse(result.stdout) as HookEnvelope;
  expect(envelope.hookSpecificOutput.hookEventName).toBe("SubagentStart");
  expect(envelope).toEqual({
    hookSpecificOutput: {
      additionalContext: readSkillBody(roleSkillRoots.subagent),
      hookEventName: "SubagentStart"
    }
  });
  expect(envelope.hookSpecificOutput.additionalContext).not.toBe(
    readSkillBody(roleSkillRoots.main)
  );
});

test("hook rejects malformed, absent, and non-object input", async () => {
  const [malformed, empty, absent, scalar, array] = await Promise.all([
    runHook("main", "not-json"),
    runHook("main", ""),
    runHook("main"),
    runHook("main", "null"),
    runHook("main", "[]")
  ]);
  expect(malformed.exitCode).not.toBe(0);
  expect(malformed.stdout).toBe("");
  expect(malformed.stderr).toContain("Unexpected token");
  expect(empty.exitCode).not.toBe(0);
  expect(absent.exitCode).not.toBe(0);
  expect(empty.stderr).toContain("stdin JSON object is required");
  expect(absent.stderr).toContain("stdin JSON object is required");
  for (const result of [scalar, array]) {
    expect(result.exitCode).not.toBe(0);
    expect(result.stdout).toBe("");
    expect(result.stderr).toContain("stdin JSON must be an object");
  }
});

test("hook rejects missing root and invalid role", async () => {
  const missingRoot = await runHook("main", JSON.stringify({}), {
    ...process.env,
    CLAUDE_PLUGIN_ROOT: path.join(pluginRoot, "missing")
  });
  const invalidRole = await runHook("worker", JSON.stringify({}));
  expect(missingRoot.exitCode).not.toBe(0);
  expect(missingRoot.stderr).toContain("ENOENT");
  expect(invalidRole.exitCode).not.toBe(0);
  expect(invalidRole.stderr).toContain("role must be main or subagent");
});

test("hook rejects an absent CLAUDE_PLUGIN_ROOT", async () => {
  const result = await runHook(
    "main",
    JSON.stringify({}),
    Object.fromEntries(
      Object.entries(process.env).filter(
        ([key]) => key !== "CLAUDE_PLUGIN_ROOT"
      )
    )
  );
  expect(result.exitCode).not.toBe(0);
  expect(result.stderr).toContain("CLAUDE_PLUGIN_ROOT is required");
  expect(result.stdout).toBe("");
});
