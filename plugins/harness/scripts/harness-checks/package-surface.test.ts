// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import {
  cpSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  renameSync,
  rmSync,
  symlinkSync,
  unlinkSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import {
  checkPackageSurface,
  checkRepositoryPackageSurface
} from "./package-surface.js";

type Manifest = Record<string, unknown>;

const writeJson = (filePath: string, value: unknown): void => {
  writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf-8");
};

const copyRepositoryFixture = (temporaryRoot: string): string => {
  const sourceRoot = path.resolve(import.meta.dirname, "..", "..", "..", "..");
  const fixtureRoot = path.join(temporaryRoot, "repository");
  const blockedParts = new Set([
    ".git",
    ".gradle",
    ".idea",
    ".venv",
    ".worktrees",
    "__pycache__",
    "node_modules",
    "target"
  ]);
  cpSync(sourceRoot, fixtureRoot, {
    dereference: false,
    filter: (source) => {
      const relative = path.relative(sourceRoot, source);
      return !relative.split(path.sep).some((part) => blockedParts.has(part));
    },
    recursive: true
  });
  for (const [link, target] of [
    [".agents/skills", "../.claude/skills"],
    [".claude/agents", "../plugins/agent-capability-kit/agents"],
    [".claude/skills", "../plugins/agent-capability-kit/skills"],
    [
      "scripts/no-box-drawing.ts",
      "../plugins/harness/skills/harness-install/assets/common/scripts/no-box-drawing.ts"
    ]
  ] as const) {
    const linkPath = path.join(fixtureRoot, link);
    rmSync(linkPath, { force: true, recursive: true });
    symlinkSync(target, linkPath);
  }
  return fixtureRoot;
};

const copyPluginFixture = (temporaryRoot: string): string => {
  const sourceRoot = path.resolve(import.meta.dirname, "..", "..");
  const fixtureRoot = path.join(temporaryRoot, "harness");
  const repositoryRoot = path.resolve(sourceRoot, "..", "..");
  const tracked = Bun.spawnSync(
    ["git", "-C", repositoryRoot, "ls-files", "-z", "--", "plugins/harness"],
    { stderr: "pipe", stdout: "pipe" }
  );
  if (!tracked.success) {
    throw new Error(tracked.stderr.toString());
  }
  for (const filePath of tracked.stdout.toString().split("\0")) {
    if (filePath === "") {
      continue;
    }
    const targetPath = path.join(
      fixtureRoot,
      path.relative("plugins/harness", filePath)
    );
    mkdirSync(path.dirname(targetPath), { recursive: true });
    cpSync(path.join(repositoryRoot, filePath), targetPath);
  }
  return fixtureRoot;
};

test("package surface follows Claude 2.1.205 fields and filesystem boundaries", () => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "package-surface-"));
  try {
    const repositoryRoot = copyRepositoryFixture(temporaryRoot);
    const harnessRoot = path.join(repositoryRoot, "plugins", "harness");
    const manifestPath = path.join(
      harnessRoot,
      ".claude-plugin",
      "plugin.json"
    );
    const originalManifest = JSON.parse(
      readFileSync(manifestPath, "utf-8")
    ) as Manifest;
    const agentPath = path.join(harnessRoot, "agents", "harness-architect.md");
    const originalAgent = readFileSync(agentPath, "utf-8");
    const themePath = path.join(harnessRoot, "branding", "theme.json");
    mkdirSync(path.dirname(themePath));
    writeJson(themePath, { base: "dark", name: "Probe", overrides: {} });
    writeJson(manifestPath, {
      ...originalManifest,
      commands: {
        probe: {
          content: "Probe command",
          description: "Schema probe"
        }
      },
      defaultEnabled: false,
      displayName: "Harness Probe",
      experimental: {
        monitors: [
          {
            command: "printf probe",
            description: "Schema probe",
            name: "probe"
          }
        ],
        themes: "./branding/theme.json"
      },
      settings: {
        agent: "harness-architect",
        subagentStatusLine: {
          command: "printf status",
          padding: 1,
          type: "command"
        }
      }
    });
    writeFileSync(
      agentPath,
      originalAgent.replace(
        "color: blue\n",
        "color: blue\ninitialPrompt: Inspect the requested harness.\n"
      ),
      "utf-8"
    );
    expect(() => checkRepositoryPackageSurface(harnessRoot)).not.toThrow();

    writeJson(manifestPath, {
      ...originalManifest,
      settings: { permissions: { allow: ["Bash"] } }
    });
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();

    writeJson(manifestPath, {
      ...originalManifest,
      themes: "./branding/theme.json"
    });
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();

    writeJson(manifestPath, originalManifest);
    writeFileSync(agentPath, originalAgent, "utf-8");
    writeFileSync(agentPath, "---\nname: [\n---\n", "utf-8");
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();
    writeFileSync(agentPath, originalAgent, "utf-8");
    const agentsPath = path.join(harnessRoot, "agents");
    const hiddenAgentsPath = path.join(harnessRoot, "agents.hidden");
    renameSync(agentsPath, hiddenAgentsPath);
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();
    renameSync(hiddenAgentsPath, agentsPath);

    const documentSkills = path.join(
      repositoryRoot,
      "plugins",
      "document-creator",
      "skills"
    );
    const hiddenDocumentSkills = `${documentSkills}.hidden`;
    renameSync(documentSkills, hiddenDocumentSkills);
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();
    renameSync(hiddenDocumentSkills, documentSkills);

    const missingSkill = path.join(harnessRoot, "skills", "missing-entry");
    mkdirSync(missingSkill);
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();
    rmSync(missingSkill, { recursive: true });

    const settingsPath = path.join(harnessRoot, "settings.json");
    writeJson(settingsPath, { permissions: { allow: ["Bash"] } });
    expect(() => checkRepositoryPackageSurface(harnessRoot)).toThrow();
    unlinkSync(settingsPath);

    const outside = path.join(temporaryRoot, "outside-skills");
    mkdirSync(outside);
    const escapedPath = path.join(harnessRoot, "escaped-skills");
    symlinkSync(outside, escapedPath, "dir");
    writeJson(manifestPath, {
      ...originalManifest,
      skills: "./escaped-skills/"
    });
    expect(() => checkPackageSurface(harnessRoot)).toThrow();
    unlinkSync(escapedPath);
    writeJson(manifestPath, originalManifest);
    expect(() => checkRepositoryPackageSurface(harnessRoot)).not.toThrow();
    const repositoryAlias = path.join(temporaryRoot, "repository-alias");
    symlinkSync(repositoryRoot, repositoryAlias, "dir");
    expect(() =>
      checkRepositoryPackageSurface(
        path.join(repositoryAlias, "plugins", "harness")
      )
    ).not.toThrow();
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
}, 120_000);

test("a copied Harness package runs its self-check without repository files", () => {
  const temporaryRoot = mkdtempSync(path.join(tmpdir(), "harness-package-"));
  try {
    const pluginRoot = copyPluginFixture(temporaryRoot);
    expect(() => checkPackageSurface(pluginRoot)).not.toThrow();
    const escapingRuntime = path.join(pluginRoot, "scripts", "escape.ts");
    writeFileSync(
      escapingRuntime,
      'import "../../../../scripts/agent-routing.js";\n',
      "utf-8"
    );
    expect(() => checkPackageSurface(pluginRoot)).toThrow();
    unlinkSync(escapingRuntime);
    const result = Bun.spawnSync(
      [
        process.execPath,
        path.join(pluginRoot, "scripts", "plugin-self-check.ts")
      ],
      { cwd: pluginRoot, stderr: "pipe", stdout: "pipe" }
    );
    expect(
      result.success,
      `${result.stdout.toString()}${result.stderr.toString()}`
    ).toBe(true);
  } finally {
    rmSync(temporaryRoot, { force: true, recursive: true });
  }
}, 120_000);
