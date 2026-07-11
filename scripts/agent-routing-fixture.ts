import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

export type FixtureOptions = Readonly<{
  access?: "executor" | "read-only" | "writer";
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

export type Fixture = Readonly<{
  body: string;
  claudePath: string;
  codexPath: string;
  manifestPath: string;
  root: string;
}>;

export const DEFAULT_BODY = `# Fixture Agent

Inspect the requested surface and report evidence.

## Execution Topology

This role works inside the request's declared routing boundary.
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
  const tools = options.tools.map((tool) => `  - ${tool}`).join("\n");
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
${tools}
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

export const createFixture = async (
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
      `${JSON.stringify({ agents: [{ access: options.access, claudeEffort: options.claudeEffort, claudeModel: options.claudeModel, claudePath: path.relative(root, claudePath), codexEffort: options.codexEffort, codexModel: options.codexModel, codexPath: path.relative(root, codexPath), name: options.name, topology: options.topology }], schemaVersion: 1 }, null, 2)}\n`,
      "utf-8"
    )
  ]);
  return { body: options.body, claudePath, codexPath, manifestPath, root };
};

export const executorOptions = (
  overrides: FixtureOptions = {}
): FixtureOptions => ({
  access: "executor",
  claudeEffort: "low",
  claudeModel: "haiku",
  codexEffort: "low",
  codexModel: "gpt-5.6-luna",
  name: "validation-executor",
  sandbox: "workspace-write",
  tools: ["Read", "Glob", "Grep", "Bash"],
  topology: "mechanical",
  ...overrides
});

export const removeFixture = async (fixture: Fixture): Promise<void> => {
  await rm(fixture.root, { force: true, recursive: true });
};
