import { expect, test } from "bun:test";

import {
  createFixture,
  executorOptions,
  removeFixture
} from "./agent-routing-fixture.js";
import { validateAgentRouting } from "./agent-routing.js";

test("executor agents require Bash", async () => {
  const fixture = await createFixture(
    executorOptions({ tools: ["Read", "Glob", "Grep"] })
  );
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toContain(
      "plugins/fixture/agents/validation-executor.md: executor agents must expose Bash"
    );
  } finally {
    await removeFixture(fixture);
  }
});

test("executor agents reject source-edit tools", async () => {
  const fixture = await createFixture(
    executorOptions({
      tools: ["Read", "Glob", "Grep", "Bash", "Edit", "Write"]
    })
  );
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) =>
          error.includes("executor agent exposes mutation tools Edit, Write")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("executor agents reject Agent and Task delegation tools", async () => {
  const fixture = await createFixture(
    executorOptions({
      tools: ["Read", "Glob", "Grep", "Bash", "Agent", "Task(child)"]
    })
  );
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) =>
          error.includes(
            "mechanical topology agents may not expose delegation tools"
          )
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("executor agents require mechanical topology", async () => {
  const fixture = await createFixture(
    executorOptions({
      claudeEffort: "medium",
      claudeModel: "sonnet",
      codexEffort: "medium",
      codexModel: "gpt-5.6-terra",
      topology: "leaf"
    })
  );
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toContain(
      "plugins/fixture/agents/validation-executor.md: executor access requires mechanical topology"
    );
  } finally {
    await removeFixture(fixture);
  }
});

test("executor Codex counterparts require workspace-write", async () => {
  const fixture = await createFixture(
    executorOptions({ sandbox: "read-only" })
  );
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toContain(
      ".codex/agents/validation-executor.toml: sandbox_mode must be workspace-write"
    );
  } finally {
    await removeFixture(fixture);
  }
});

test("mechanical validation executors are accepted", async () => {
  const fixture = await createFixture(executorOptions());
  try {
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(result.errors).toEqual([]);
    expect(result.warnings).toHaveLength(1);
  } finally {
    await removeFixture(fixture);
  }
});
