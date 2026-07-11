import { expect, test } from "bun:test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

import {
  createFixture,
  DEFAULT_BODY,
  removeFixture
} from "./agent-routing-fixture.js";
import { validateAgentRouting } from "./agent-routing.js";

test("broken namespaced skill references fail", async () => {
  const fixture = await createFixture({
    body: `${DEFAULT_BODY}\n\nUse \`missing-plugin:missing-skill\` for details.`
  });
  try {
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("broken skill reference")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("nested Markdown below agents fails", async () => {
  const fixture = await createFixture();
  try {
    const nested = path.join(
      path.dirname(fixture.claudePath),
      "notes",
      "extra.md"
    );
    await mkdir(path.dirname(nested), { recursive: true });
    await writeFile(nested, "# Extra\n", "utf-8");
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("may not contain nested files")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("unlisted direct agents fail inventory validation", async () => {
  const fixture = await createFixture();
  try {
    await writeFile(
      path.join(path.dirname(fixture.claudePath), "unlisted.md"),
      "---\nname: unlisted\n---\n",
      "utf-8"
    );
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("inventory drift")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("pending integration files must be promoted into canonical inventory", async () => {
  const fixture = await createFixture();
  try {
    const manifest: Record<string, unknown> = await Bun.file(
      fixture.manifestPath
    ).json();
    manifest["pendingIntegrations"] = [
      {
        access: "writer",
        claudeEffort: "low",
        claudeModel: "haiku",
        claudePath: "plugins/fixture/agents/scoped-implementer.md",
        codexEffort: "low",
        codexModel: "gpt-5.6-luna",
        codexPath: ".codex/agents/scoped-implementer.toml",
        name: "scoped-implementer",
        topology: "mechanical"
      }
    ];
    await writeFile(
      fixture.manifestPath,
      `${JSON.stringify(manifest, null, 2)}\n`,
      "utf-8"
    );
    await writeFile(
      path.join(path.dirname(fixture.claudePath), "scoped-implementer.md"),
      "---\nname: scoped-implementer\n---\n",
      "utf-8"
    );
    expect(
      validateAgentRouting(fixture.root, fixture.manifestPath).errors.some(
        (error) => error.includes("promote the entry")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});
