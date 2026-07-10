// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { validateAgentRouting } from "./agent-routing.js";

type FixtureOptions = Readonly<{
  access?: "read-only" | "writer";
  body?: string;
  claudeEffort?: string;
  claudeModel?: string;
  codexEffort?: string;
  codexModel?: string;
  name?: string;
  sandbox?: string;
  tools?: readonly string[];
  topology?: "inventory" | "leaf" | "mechanical";
}>;

type Fixture = Readonly<{
  body: string;
  claudePath: string;
  codexPath: string;
  manifestPath: string;
  root: string;
}>;

const DEFAULT_BODY = `# Fixture Agent

Inspect the requested surface and report evidence.

## Execution Topology

This agent is a read-only leaf.
Do not delegate or modify files.

## Process

1. Inspect the requested surface.
2. Return the evidence.

## Boundaries

Stop and report a blocker when the requested evidence is missing.

## Output

Return evidence and blockers.`;

const writeClaude = async (
  filePath: string,
  options: Required<FixtureOptions>,
  body: string
): Promise<void> => {
  const toolLines = options.tools.map((tool) => `  - ${tool}`).join("\n");
  await writeFile(
    filePath,
    `---
name: ${options.name}
description: >-
  Inspect a fixture repository surface and report evidence.
  Use this agent when validating deterministic routing fixtures.
model: ${options.claudeModel}
effort: ${options.claudeEffort}
tools:
${toolLines}
---

${body}
`,
    "utf-8"
  );
};

const writeCodex = async (
  filePath: string,
  options: Required<FixtureOptions>,
  body: string
): Promise<void> => {
  await writeFile(
    filePath,
    `name = '''${options.name}'''
description = '''Inspect a fixture repository surface and report evidence. Use this agent when validating deterministic routing fixtures.'''
model = "${options.codexModel}"
model_reasoning_effort = "${options.codexEffort}"
sandbox_mode = "${options.sandbox}"
developer_instructions = '''${body}
'''
`,
    "utf-8"
  );
};

const createFixture = async (
  overrides: FixtureOptions = {}
): Promise<Fixture> => {
  const root = await mkdtemp(path.join(tmpdir(), "sinon-agent-routing-"));
  const options: Required<FixtureOptions> = {
    access: overrides.access ?? "read-only",
    body: overrides.body ?? DEFAULT_BODY,
    claudeEffort: overrides.claudeEffort ?? "medium",
    claudeModel: overrides.claudeModel ?? "sonnet",
    codexEffort: overrides.codexEffort ?? "medium",
    codexModel: overrides.codexModel ?? "gpt-5.6-terra",
    name: overrides.name ?? "fixture-worker",
    sandbox: overrides.sandbox ?? "read-only",
    tools: overrides.tools ?? ["Read", "Glob", "Grep"],
    topology: overrides.topology ?? "leaf"
  };
  const claudeDirectory = path.join(root, "plugins", "fixture", "agents");
  const codexDirectory = path.join(root, ".codex", "agents");
  const scriptsDirectory = path.join(root, "scripts");
  await Promise.all([
    mkdir(claudeDirectory, { recursive: true }),
    mkdir(codexDirectory, { recursive: true }),
    mkdir(scriptsDirectory, { recursive: true })
  ]);
  const claudePath = path.join(claudeDirectory, `${options.name}.md`);
  const codexPath = path.join(codexDirectory, `${options.name}.toml`);
  const manifestPath = path.join(
    scriptsDirectory,
    "agent-routing-manifest.json"
  );
  await Promise.all([
    writeClaude(claudePath, options, options.body),
    writeCodex(codexPath, options, options.body),
    writeFile(
      manifestPath,
      `${JSON.stringify(
        {
          agents: [
            {
              access: options.access,
              claudeEffort: options.claudeEffort,
              claudeModel: options.claudeModel,
              claudePath: path.relative(root, claudePath),
              codexEffort: options.codexEffort,
              codexModel: options.codexModel,
              codexPath: path.relative(root, codexPath),
              name: options.name,
              topology: options.topology
            }
          ],
          schemaVersion: 1
        },
        null,
        2
      )}\n`,
      "utf-8"
    )
  ]);
  return {
    body: options.body,
    claudePath,
    codexPath,
    manifestPath,
    root
  };
};

const removeFixture = async (fixture: Fixture): Promise<void> => {
  await rm(fixture.root, { force: true, recursive: true });
};

test("the repository routing inventory is valid", () => {
  const root = path.resolve(import.meta.dirname, "..");
  const result = validateAgentRouting(root);
  expect(result.errors).toEqual([]);
  expect(result.warnings).toHaveLength(2);
  expect(result.warnings[0]).toContain("runtime-inert compatibility metadata");
});

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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("Effort Exception"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("read-only agents reject mutation tools", async () => {
  const fixture = await createFixture({ tools: ["Read", "Write"] });
  try {
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("exposes mutation tools"))
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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("child allowlists"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) =>
        error.includes("counterpart developer instructions drift")
      )
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("broken namespaced skill references fail", async () => {
  const body = `${DEFAULT_BODY}\n\nUse \`missing-plugin:missing-skill\` for details.`;
  const fixture = await createFixture({ body });
  try {
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("broken skill reference"))
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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) =>
        error.includes("may not contain nested files")
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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("inventory drift"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});

test("pending integration files must be promoted into canonical inventory", async () => {
  const fixture = await createFixture();
  try {
    const manifest = JSON.parse(
      await Bun.file(fixture.manifestPath).text()
    ) as Record<string, unknown>;
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
    const result = validateAgentRouting(fixture.root, fixture.manifestPath);
    expect(
      result.errors.some((error) => error.includes("promote the entry"))
    ).toBe(true);
  } finally {
    await removeFixture(fixture);
  }
});
