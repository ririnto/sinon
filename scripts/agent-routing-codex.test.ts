import { expect, test } from "bun:test";
import { writeFile } from "node:fs/promises";

import { createFixture, removeFixture } from "./agent-routing-fixture.js";
import { validateAgentRouting } from "./agent-routing.js";

test("counterpart instruction drift fails", async () => {
  const fixture = await createFixture();
  try {
    const content = await Bun.file(fixture.codexPath).text();
    await writeFile(
      fixture.codexPath,
      content.replace(
        "Return evidence and blockers.",
        "Return a different result."
      ),
      "utf-8"
    );
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("counterpart developer instructions drift")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("counterpart line-ending-only differences do not drift", async () => {
  const fixture = await createFixture();
  try {
    const content = await Bun.file(fixture.codexPath).text();
    await writeFile(
      fixture.codexPath,
      content.replace("\n## Output\n", "\r\n## Output\r\n"),
      "utf-8"
    );
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toEqual([]);
  } finally {
    await removeFixture(fixture);
  }
});

test("counterpart description rewrapping does not drift", async () => {
  const fixture = await createFixture();
  try {
    const content = await Bun.file(fixture.codexPath).text();
    await writeFile(
      fixture.codexPath,
      content.replace(
        "Inspect a fixture repository surface and report evidence. Use this agent when validating deterministic routing fixtures.",
        "Inspect a fixture repository surface and report evidence.\nUse this agent when validating deterministic routing fixtures."
      ),
      "utf-8"
    );
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors
    ).toEqual([]);
  } finally {
    await removeFixture(fixture);
  }
});
