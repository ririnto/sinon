import { expect, test } from "bun:test";
import { writeFile } from "node:fs/promises";

import { createFixture, removeFixture } from "./agent-routing-fixture.js";
import { validateAgentRouting } from "./agent-routing.js";

test("missing model and effort declarations fail", async () => {
  const fixture = await createFixture();
  try {
    const content = await Bun.file(fixture.claudePath).text();
    await writeFile(
      fixture.claudePath,
      content.replace("model: sonnet\neffort: medium\n", ""),
      "utf-8"
    );
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(result.errors.some((error) => error.includes("model must be"))).toBe(
      true
    );
    expect(
      result.errors.some((error) => error.includes("effort must be"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("invalid model pairing and effort fail", async () => {
  const fixture = await createFixture({
    claudeEffort: "ultra",
    codexEffort: "ultra",
    codexModel: "gpt-5.6-luna"
  });
  try {
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("must pair with"))
    ).toBe(true);
    expect(
      result.errors.some((error) => error.includes("unsupported effort"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("Haiku low is accepted with an informational compatibility warning", async () => {
  const fixture = await createFixture({
    claudeEffort: "low",
    claudeModel: "haiku",
    codexEffort: "low",
    codexModel: "gpt-5.6-luna",
    name: "inventory-scanner",
    topology: "inventory"
  });
  try {
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(result.errors).toEqual([]);
    expect(result.warnings).toHaveLength(1);
  } finally {
    await removeFixture(fixture);
  }
});

test("high effort requires a written exception", async () => {
  const fixture = await createFixture({
    claudeEffort: "high",
    codexEffort: "high"
  });
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("Effort Exception")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("read-only agents reject mutation tools", async () => {
  const fixture = await createFixture({ tools: ["Read", "Write"] });
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("exposes mutation tools")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("installable agents reject child allowlists", async () => {
  const fixture = await createFixture({
    tools: ["Read", "Agent(unknown-child)"]
  });
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("child allowlists")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("manifest topology accepts arbitrary bounded leaf prose", async () => {
  const fixture = await createFixture({
    body: "# Fixture Agent\n\nInspect the requested surface and report evidence.\n\n## Coordination\n\nThe caller decides task decomposition and receives the result.\n\n## Process\n\n1. Inspect the requested surface.\n2. Return the evidence.\n\n## Boundaries\n\nStop and report a blocker when the requested evidence is missing.\n\n## Output\n\nReturn evidence and blockers."
  });
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toEqual([]);
  } finally {
    await removeFixture(fixture);
  }
});
