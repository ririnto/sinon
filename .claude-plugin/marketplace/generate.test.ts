import { spawnSync } from "node:child_process";
import {
  copyFileSync,
  linkSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  readlinkSync,
  realpathSync,
  rmSync,
  statSync,
  symlinkSync,
  utimesSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";
import { isScalar, parseDocument, Scalar } from "yaml";

import { createCodexMarketplace, syncCodexMarketplace } from "./generate.js";

const makeRoot = (): string => {
  const root = mkdtempSync(path.join(tmpdir(), "sinon-marketplace-test-"));
  mkdirSync(path.join(root, "plugins"), { recursive: true });
  onTestFinished(() => rmSync(root, { force: true, recursive: true }));
  return root;
};

const writePlugin = (
  root: string,
  directory: string,
  overrides: {
    native?: Record<string, unknown>;
  } = {}
): void => {
  const pluginRoot = path.join(root, "plugins", directory);
  const baseManifest = {
    author: { name: "Sinon" },
    description: "Native plugin description.",
    license: "MIT",
    name: directory,
    version: "1.0.0"
  };
  const nativeManifest = {
    ...baseManifest,
    ...overrides.native
  };
  mkdirSync(path.join(pluginRoot, ".codex-plugin"), { recursive: true });
  writeFileSync(
    path.join(pluginRoot, ".codex-plugin/plugin.json"),
    `${JSON.stringify(nativeManifest, null, 2)}\n`
  );
};

const localEntry = (name: string, source = `./plugins/${name}`) => ({
  author: { name: "Sinon" },
  category: "development",
  description: "Marketplace description can differ from package metadata.",
  license: "MIT",
  name,
  source,
  version: "1.0.0"
});

const catalog = (plugins: unknown[]) => ({ name: "sinon", plugins });

test("maps local packages and preserves external URL selectors", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  const result = await createCodexMarketplace(
    catalog([
      localEntry("helper"),
      {
        category: "development",
        description: "External plugin marketplace description.",
        name: "workgraph",
        source: {
          ref: "main",
          source: "url",
          url: "https://github.com/ririnto/workgraph.git"
        },
        version: "1.0.0"
      }
    ]),
    root
  );
  expect(result).toEqual({
    interface: { displayName: "Sinon" },
    name: "sinon",
    plugins: [
      {
        category: "Development",
        name: "helper",
        policy: { authentication: "ON_INSTALL", installation: "AVAILABLE" },
        source: { path: "./plugins/helper", source: "local" }
      },
      {
        category: "Development",
        name: "workgraph",
        policy: { authentication: "ON_INSTALL", installation: "AVAILABLE" },
        source: {
          ref: "main",
          source: "url",
          url: "https://github.com/ririnto/workgraph.git"
        }
      }
    ]
  });
});

test("omits Herdr from Codex when it uses the direct upstream source", async () => {
  const result = await createCodexMarketplace(
    catalog([
      {
        ...localEntry("herdr"),
        source: { repo: "herdrdev/herdr", source: "github" }
      }
    ]),
    makeRoot()
  );
  expect(result.plugins).toEqual([]);
});

test.each([
  { source: "./plugins/herdr" },
  { source: "https://github.com/herdrdev/herdr.git" },
  { source: { repo: "herdrdev/other", source: "github" } },
  { source: { source: "github" } },
  { source: { ref: "master", repo: "herdrdev/herdr", source: "github" } },
  { source: { source: "url", url: "https://github.com/herdrdev/herdr.git" } },
  { source: { source: "unsupported", url: "https://example.com" } }
])(
  "rejects a Herdr source other than its direct upstream repository: %j",
  async ({ source }) => {
    await expect(
      createCodexMarketplace(
        catalog([{ ...localEntry("herdr"), source }]),
        makeRoot()
      )
    ).rejects.toThrow(Error);
  }
);

test.each([
  { path: "pstack", ref: "main" },
  { path: "tools/pstack" },
  { path: "./tools/pstack", sha: "a".repeat(40) }
])("preserves a Git subdirectory source with $path", async (selector) => {
  const source = {
    ...selector,
    source: "git-subdir",
    url: "https://github.com/cursor/plugins.git"
  };
  const result = await createCodexMarketplace(
    catalog([{ ...localEntry("pstack"), source }]),
    makeRoot()
  );
  expect(result.plugins).toEqual([
    {
      category: "Development",
      name: "pstack",
      policy: { authentication: "ON_INSTALL", installation: "AVAILABLE" },
      source
    }
  ]);
});

test.each([
  { path: undefined },
  { path: 7 },
  { path: "" },
  { path: " " },
  { path: "/pstack" },
  { path: "C:/pstack" },
  { path: "./C:/pstack" },
  { path: "./C:pstack" },
  { path: "../pstack" },
  { path: "tools/../../pstack" },
  { path: "tools/./pstack" },
  { path: "tools//pstack" },
  { path: "tools\\pstack" },
  { path: "." },
  { path: "./" },
  { url: undefined },
  { url: "" },
  { ref: 7 },
  { sha: 7 },
  { ref: "main", sha: "a".repeat(40) },
  { directory: "pstack" }
])("rejects a malformed Git subdirectory source: %j", async (overrides) => {
  await expect(
    createCodexMarketplace(
      catalog([
        {
          ...localEntry("pstack"),
          source: {
            path: "pstack",
            source: "git-subdir",
            url: "https://github.com/cursor/plugins.git",
            ...overrides
          }
        }
      ]),
      makeRoot()
    )
  ).rejects.toThrow(Error);
});

test("rejects a subdirectory field on an ordinary URL source", async () => {
  await expect(
    createCodexMarketplace(
      catalog([
        {
          ...localEntry("pstack"),
          source: {
            path: "pstack",
            source: "url",
            url: "https://github.com/cursor/plugins.git"
          }
        }
      ]),
      makeRoot()
    )
  ).rejects.toThrow(Error);
});

test("rejects duplicate identities across URL and Git subdirectory sources", async () => {
  await expect(
    createCodexMarketplace(
      catalog(
        ["url", "git-subdir"].map((source) => ({
          ...localEntry("pstack"),
          source: {
            ...(source === "git-subdir" ? { path: "pstack" } : {}),
            source,
            url: "https://github.com/cursor/plugins.git"
          }
        }))
      ),
      makeRoot()
    )
  ).rejects.toThrow(Error);
});

test("rejects unsupported source types and duplicate local identities", async () => {
  const root = makeRoot();
  await expect(
    createCodexMarketplace(
      catalog([
        {
          ...localEntry("helper"),
          source: { package: "@example/plugin", source: "npm" }
        }
      ]),
      root
    )
  ).rejects.toThrow(Error);
  await expect(
    createCodexMarketplace(
      catalog([localEntry("helper"), localEntry("helper")]),
      root
    )
  ).rejects.toThrow(Error);
  await expect(
    createCodexMarketplace(
      catalog([localEntry("first"), localEntry("second", "./plugins/first")]),
      root
    )
  ).rejects.toThrow(Error);
  writePlugin(root, "helper");
  symlinkSync(
    path.join(root, "plugins", "helper"),
    path.join(root, "plugins", "helper-alias"),
    "dir"
  );
  await expect(
    createCodexMarketplace(
      catalog([
        localEntry("helper"),
        localEntry("helper-alias", "./plugins/helper-alias")
      ]),
      root
    )
  ).rejects.toThrow(Error);
});

test("rejects package identity and metadata disagreement", async () => {
  const root = makeRoot();
  writePlugin(root, "helper", { native: { name: "different" } });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
  writePlugin(root, "helper", {
    native: { description: "" }
  });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
  writePlugin(root, "helper");
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), license: "Apache-2.0" }]),
      root
    )
  ).rejects.toThrow(Error);
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), version: "2.0.0" }]),
      root
    )
  ).rejects.toThrow(Error);
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), author: { name: "Other" } }]),
      root
    )
  ).rejects.toThrow(Error);
});

test("accepts an absent Claude manifest and rejects one when present", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).resolves.toMatchObject({
    plugins: [{ name: "helper" }]
  });
  const claudePluginDirectory = path.join(
    root,
    "plugins/helper/.claude-plugin"
  );
  mkdirSync(claudePluginDirectory, { recursive: true });
  writeFileSync(path.join(claudePluginDirectory, "plugin.json"), "{}\n");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
});

test("omits catalog packages without a native manifest and rejects the root manifest shadow", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  writePlugin(root, "claude-only");
  rmSync(path.join(root, "plugins/claude-only/.codex-plugin/plugin.json"));
  await expect(
    createCodexMarketplace(
      catalog([localEntry("claude-only"), localEntry("helper")]),
      root
    )
  ).resolves.toMatchObject({ plugins: [{ name: "helper" }] });
  const nativeManifest = path.join(
    root,
    "plugins/helper/.codex-plugin/plugin.json"
  );
  writeFileSync(nativeManifest, "not json\n");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
  writePlugin(root, "helper");
  writeFileSync(path.join(root, "plugins/helper/plugin.json"), "{}\n");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
});

test("adds Codex-only packages that the catalog does not list", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  writePlugin(root, "codex-only");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).resolves.toMatchObject({
    plugins: [
      {
        category: "Development",
        name: "codex-only",
        source: { path: "./plugins/codex-only", source: "local" }
      },
      { name: "helper" }
    ]
  });
  writePlugin(root, "codex-only", { native: { name: "renamed" } });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
  writePlugin(root, "codex-only", { native: { license: "" } });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(Error);
});

test("rejects local plugin paths that resolve through symlinks outside the repository", async () => {
  const root = makeRoot();
  const outside = mkdtempSync(
    path.join(tmpdir(), "sinon-marketplace-outside-")
  );
  onTestFinished(() => rmSync(outside, { force: true, recursive: true }));
  writePlugin(outside, "escape");
  symlinkSync(
    path.join(outside, "plugins", "escape"),
    path.join(root, "plugins", "escape"),
    "dir"
  );
  await expect(
    createCodexMarketplace(catalog([localEntry("escape")]), root)
  ).rejects.toThrow(Error);
});

test("check mode reports drift without rewriting the generated marketplace", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  const source = catalog([localEntry("helper")]);
  await syncCodexMarketplace(source, root, false);
  const outputPath = path.join(root, ".agents/plugins/marketplace.json");
  utimesSync(outputPath, 1, 1);
  const originalMtime = statSync(outputPath).mtimeMs;
  await syncCodexMarketplace(source, root, true);
  expect(statSync(outputPath).mtimeMs).toBe(originalMtime);
  const drift = '{"manuallyEdited":true}\n';
  writeFileSync(outputPath, drift);
  await expect(syncCodexMarketplace(source, root, true)).rejects.toThrow(Error);
  expect(readFileSync(outputPath, "utf-8")).toBe(drift);
});

test.each(
  [true, false].flatMap((listed) =>
    [".codex-plugin", ".codex-plugin/plugin.json"].map((component) => ({
      component,
      listed
    }))
  )
)(
  "rejects escaping native manifest $component with listed=$listed before writing the catalog",
  async ({ component, listed }) => {
    const root = makeRoot();
    const outside = makeRoot();
    writePlugin(root, "helper");
    writePlugin(outside, "helper");
    const target = path.join(root, "plugins/helper", component);
    rmSync(target, { recursive: true });
    symlinkSync(
      path.join(outside, "plugins/helper", component),
      target,
      component.endsWith(".json") ? "file" : "dir"
    );
    await expect(
      syncCodexMarketplace(
        catalog(listed ? [localEntry("helper")] : []),
        root,
        false
      )
    ).rejects.toThrow("resolves outside the plugin root");
    expect(readdirSync(root)).toEqual(["plugins"]);
  }
);

test("rejects Codex-only discovery through an external plugins directory", async () => {
  const root = makeRoot();
  const outside = makeRoot();
  writePlugin(outside, "helper");
  rmSync(path.join(root, "plugins"), { recursive: true });
  symlinkSync(path.join(outside, "plugins"), path.join(root, "plugins"), "dir");
  await expect(syncCodexMarketplace(catalog([]), root, false)).rejects.toThrow(
    "resolves outside the repository root"
  );
  expect(readdirSync(root)).toEqual(["plugins"]);
});

test("accepts native manifest links contained within the same plugin", async () => {
  const root = makeRoot();
  writePlugin(root, "helper");
  const manifest = path.join(root, "plugins/helper/.codex-plugin/plugin.json");
  copyFileSync(manifest, path.join(root, "plugins/helper/identity.json"));
  rmSync(manifest);
  symlinkSync("../identity.json", manifest, "file");
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).resolves.toMatchObject({ plugins: [{ name: "helper" }] });
});

const outputComponents = [
  ".agents",
  ".agents/plugins",
  ".agents/plugins/marketplace.json"
];

const emptyMarketplace =
  '{\n  "interface": {\n    "displayName": "Sinon"\n  },\n  "name": "sinon",\n  "plugins": []\n}\n';

test.each(
  outputComponents.flatMap((component) =>
    [false, true].flatMap((checkOnly) =>
      [false, true].map((dangling) => ({ checkOnly, component, dangling }))
    )
  )
)(
  "rejects output symlink $component with check=$checkOnly and dangling=$dangling",
  async ({ component, checkOnly, dangling }) => {
    const root = makeRoot();
    const outside = makeRoot();
    const target = path.join(outside, dangling ? "missing" : "target");
    const isFile = component.endsWith(".json");
    const sentinel = isFile
      ? target
      : path.join(
          target,
          component === ".agents" ? "plugins" : "",
          "marketplace.json"
        );
    const original = checkOnly ? emptyMarketplace : "external sentinel\n";
    if (!dangling) {
      mkdirSync(path.dirname(sentinel), { recursive: true });
      writeFileSync(sentinel, original);
      utimesSync(sentinel, 1, 1);
    }
    const link = path.join(root, component);
    mkdirSync(path.dirname(link), { recursive: true });
    symlinkSync(target, link, isFile ? "file" : "dir");
    const originalEntries = readdirSync(root, { recursive: true }).toSorted();
    const outsideEntries = readdirSync(outside, { recursive: true }).toSorted();
    await expect(
      syncCodexMarketplace(catalog([]), root, checkOnly)
    ).rejects.toThrow(Error);
    expect(lstatSync(link).isSymbolicLink()).toBe(true);
    expect(readlinkSync(link)).toBe(target);
    expect(readdirSync(root, { recursive: true }).toSorted()).toEqual(
      originalEntries
    );
    expect(readdirSync(outside, { recursive: true }).toSorted()).toEqual(
      outsideEntries
    );
    if (!dangling) {
      expect(readFileSync(sentinel, "utf-8")).toBe(original);
      expect(statSync(sentinel).mtimeMs).toBe(1000);
    }
  }
);

test.each(
  outputComponents
    .slice(0, -1)
    .flatMap((component) =>
      [false, true].map((checkOnly) => ({ checkOnly, component }))
    )
)(
  "rejects a regular file at output directory $component with check=$checkOnly",
  async ({ component, checkOnly }) => {
    const root = makeRoot();
    const target = path.join(root, component);
    mkdirSync(path.dirname(target), { recursive: true });
    writeFileSync(target, "directory sentinel\n");
    await expect(
      syncCodexMarketplace(catalog([]), root, checkOnly)
    ).rejects.toThrow(Error);
    expect(readFileSync(target, "utf-8")).toBe("directory sentinel\n");
  }
);

test.each([false, true])(
  "rejects an output directory with check=%s without modification",
  async (checkOnly) => {
    const root = makeRoot();
    const output = path.join(root, ".agents/plugins/marketplace.json");
    mkdirSync(output, { recursive: true });
    const sentinel = path.join(output, "sentinel");
    writeFileSync(sentinel, "directory sentinel\n");
    await expect(
      syncCodexMarketplace(catalog([]), root, checkOnly)
    ).rejects.toThrow(Error);
    expect(readdirSync(output)).toEqual(["sentinel"]);
    expect(readFileSync(sentinel, "utf-8")).toBe("directory sentinel\n");
  }
);

test.each([false, true])(
  "rejects a multiply linked output with check=%s without modification",
  async (checkOnly) => {
    const root = makeRoot();
    const output = path.join(root, ".agents/plugins/marketplace.json");
    mkdirSync(path.dirname(output), { recursive: true });
    const outside = path.join(makeRoot(), "sentinel");
    writeFileSync(outside, emptyMarketplace);
    linkSync(outside, output);
    await expect(
      syncCodexMarketplace(catalog([]), root, checkOnly)
    ).rejects.toThrow(Error);
    expect(readFileSync(outside, "utf-8")).toBe(emptyMarketplace);
    expect(readFileSync(output, "utf-8")).toBe(emptyMarketplace);
    expect(statSync(output).nlink).toBe(2);
    expect(statSync(output).ino).toBe(statSync(outside).ino);
  }
);

test.each([false, true])(
  "creates and refreshes ordinary output with existing agents=%s",
  async (existingAgents) => {
    const root = makeRoot();
    const output = path.join(root, ".agents/plugins/marketplace.json");
    if (existingAgents) {
      mkdirSync(path.join(root, ".agents"));
    }
    const originalEntries = readdirSync(root, { recursive: true }).toSorted();
    await expect(syncCodexMarketplace(catalog([]), root, true)).rejects.toThrow(
      Error
    );
    expect(readdirSync(root, { recursive: true }).toSorted()).toEqual(
      originalEntries
    );
    await syncCodexMarketplace(catalog([]), root, false);
    expect(readFileSync(output, "utf-8")).toBe(emptyMarketplace);
    writeFileSync(output, "stale\n");
    await syncCodexMarketplace(catalog([]), root, false);
    expect(readFileSync(output, "utf-8")).toBe(emptyMarketplace);
  }
);

test.each(outputComponents)(
  "CLI rejects output symlink %s and preserves external bytes",
  (component) => {
    const root = realpathSync(makeRoot());
    const outside = makeRoot();
    const generator = path.join(root, ".claude-plugin/marketplace/generate.ts");
    mkdirSync(path.dirname(generator), { recursive: true });
    copyFileSync(new URL("generate.ts", import.meta.url), generator);
    writeFileSync(path.join(root, "package.json"), '{"type":"module"}\n');
    writeFileSync(
      path.join(root, ".claude-plugin/marketplace.json"),
      JSON.stringify(catalog([]))
    );
    const sentinel = path.join(
      outside,
      component === ".agents" ? "plugins" : "",
      "marketplace.json"
    );
    writeFileSync(sentinel, emptyMarketplace);
    const link = path.join(root, component);
    mkdirSync(path.dirname(link), { recursive: true });
    const target = component.endsWith(".json") ? sentinel : outside;
    symlinkSync(target, link, component.endsWith(".json") ? "file" : "dir");
    for (const args of [[], ["--check"]]) {
      const result = spawnSync(
        process.execPath,
        ["--import", import.meta.resolve("tsx"), generator, ...args],
        { encoding: "utf-8", timeout: 10_000 }
      );
      expect(result.error).toBeUndefined();
      expect(result.signal).toBeNull();
      expect(result.status).toBe(1);
      expect(readFileSync(sentinel, "utf-8")).toBe(emptyMarketplace);
      expect(readlinkSync(link)).toBe(target);
    }
  }
);

test.each(
  outputComponents.flatMap((component) =>
    [false, true].map((checkOnly) => ({ checkOnly, component }))
  )
)(
  "rejects relative in-repository output symlink $component with check=$checkOnly",
  async ({ component, checkOnly }) => {
    const root = makeRoot();
    const target = path.join(root, "target");
    const isFile = component.endsWith(".json");
    const sentinel = isFile
      ? target
      : path.join(
          target,
          component === ".agents" ? "plugins" : "",
          "marketplace.json"
        );
    mkdirSync(path.dirname(sentinel), { recursive: true });
    writeFileSync(sentinel, emptyMarketplace);
    const link = path.join(root, component);
    mkdirSync(path.dirname(link), { recursive: true });
    const relativeTarget = path.relative(path.dirname(link), target);
    symlinkSync(relativeTarget, link, isFile ? "file" : "dir");
    const originalEntries = readdirSync(root, { recursive: true }).toSorted();
    await expect(
      syncCodexMarketplace(catalog([]), root, checkOnly)
    ).rejects.toThrow(Error);
    expect(readlinkSync(link)).toBe(relativeTarget);
    expect(readFileSync(sentinel, "utf-8")).toBe(emptyMarketplace);
    expect(readdirSync(root, { recursive: true }).toSorted()).toEqual(
      originalEntries
    );
  }
);

test("validates model-agent metadata, inventories, and description budget", () => {
  const descriptions: string[] = [];
  for (const plugin of [
    {
      agents: [
        {
          effort: "high",
          model: "gpt-6-luna[1m]",
          name: "luna-high"
        },
        {
          effort: "max",
          model: "gpt-6-luna[1m]",
          name: "luna-max"
        },
        {
          effort: "medium",
          model: "gpt-6.1-sol[1m]",
          name: "sol-medium"
        },
        {
          effort: "high",
          model: "gpt-6.1-sol[1m]",
          name: "sol-high"
        },
        {
          effort: "high",
          model: "gpt-6-astra[1m]",
          name: "astra"
        }
      ],
      name: "gpt-agents"
    },
    {
      agents: [
        {
          effort: "max",
          model: "glm-5.3-flash[1m]",
          name: "glm-flash"
        },
        {
          effort: "high",
          model: "glm-5.3[1m]",
          name: "glm-high"
        },
        {
          effort: "max",
          model: "glm-5.3[1m]",
          name: "glm-max"
        }
      ],
      name: "glm-agents"
    }
  ] as const) {
    const root = new URL(`../../plugins/${plugin.name}/`, import.meta.url);
    expect(
      readdirSync(new URL("agents/", root))
        .filter((file) => file.endsWith(".md"))
        .toSorted()
    ).toEqual(plugin.agents.map((agent) => `${agent.name}.md`).toSorted());
    for (const contract of plugin.agents) {
      const match =
        /^---\r?\n(?<frontmatter>[\s\S]*?)\r?\n---(?:\r?\n|$)(?<body>[\s\S]*)$/u.exec(
          readFileSync(new URL(`agents/${contract.name}.md`, root), "utf-8")
        );
      if (!match?.groups) {
        throw new Error("Missing agent frontmatter boundaries");
      }
      const { frontmatter, body } = match.groups;
      const document = parseDocument(frontmatter, { uniqueKeys: true });
      expect(document.errors).toEqual([]);
      expect(document.get("name")).toBe(contract.name);
      expect(document.get("model")).toBe(contract.model);
      expect(document.get("effort")).toBe(contract.effort);
      expect(body.trim()).toBe("");
      const node = document.get("description", true);
      if (!isScalar(node) || typeof node.value !== "string") {
        throw new Error("Description must be a string scalar");
      }
      expect(node.type).toBe(Scalar.BLOCK_FOLDED);
      expect(node.value.trim()).not.toBe("");
      expect(node.value).toBe(node.value.trim());
      descriptions.push(node.value);
    }
    const tables = readFileSync(new URL("README.md", root), "utf-8")
      .split(/\r?\n\r?\n/u)
      .filter(
        (block) =>
          block
            .split(/\r?\n/u)[0]
            .split("|")
            .slice(1, -1)
            .map((cell) => cell.trim().replaceAll("`", ""))
            .join("|") === "Agent|Model|Effort"
      );
    expect(tables).toHaveLength(1);
    expect(
      tables[0]
        .split(/\r?\n/u)
        .slice(2)
        .map((line) =>
          line
            .split("|")
            .slice(1, -1)
            .map((cell) => cell.trim().replaceAll("`", ""))
        )
    ).toEqual(
      plugin.agents.map((contract) => [
        contract.name,
        contract.model,
        contract.effort
      ])
    );
  }
  expect(Buffer.byteLength(descriptions.join(" "))).toBeLessThan(15_000);
});
