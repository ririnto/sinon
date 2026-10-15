import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
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

interface ModelAgentContract {
  readonly name: string;
  readonly model: string;
  readonly effort: string;
  readonly family: string;
  readonly tier: number;
}

const modelAgentPackages = [
  {
    agents: [
      {
        effort: "high",
        family: "Haiku",
        model: "gpt-6-luna[1m]",
        name: "luna-high",
        tier: 1
      },
      {
        effort: "max",
        family: "Sonnet",
        model: "gpt-6-luna[1m]",
        name: "luna-max",
        tier: 2
      },
      {
        effort: "medium",
        family: "Opus",
        model: "gpt-6.1-sol[1m]",
        name: "sol-medium",
        tier: 3
      },
      {
        effort: "high",
        family: "Opus",
        model: "gpt-6.1-sol[1m]",
        name: "sol-high",
        tier: 3
      },
      {
        effort: "high",
        family: "Fable",
        model: "gpt-6-astra[1m]",
        name: "astra",
        tier: 4
      }
    ],
    name: "gpt-agents"
  },
  {
    agents: [
      {
        effort: "max",
        family: "Haiku",
        model: "glm-5.3-flash[1m]",
        name: "glm-flash",
        tier: 1
      },
      {
        effort: "high",
        family: "Sonnet",
        model: "glm-5.3[1m]",
        name: "glm-high",
        tier: 2
      },
      {
        effort: "max",
        family: "Opus",
        model: "glm-5.3[1m]",
        name: "glm-max",
        tier: 3
      }
    ],
    name: "glm-agents"
  }
] as const;

const readModelAgent = (packageName: string, name: string): string =>
  readFileSync(
    new URL(`../../plugins/${packageName}/agents/${name}.md`, import.meta.url),
    "utf-8"
  );

const assertModelAgent = (
  source: string,
  contract: ModelAgentContract
): string => {
  const match =
    /^---\r?\n(?<frontmatter>[\s\S]*?)\r?\n---(?:\r?\n|$)(?<body>[\s\S]*)$/u.exec(
      source
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
  const description = node.value;
  expect(description.trim()).not.toBe("");
  expect(description).toBe(description.trim());
  expect(
    [
      ...description.matchAll(
        /\bClaude\s+(?<family>Haiku|Sonnet|Opus|Fable)\b/gu
      )
    ].map((family) => family.groups?.family)
  ).toEqual([contract.family]);
  expect(description).toMatch(/\bmodel\b/iu);
  expect(description).toMatch(/\beffort\b/iu);
  expect(description).toMatch(/\b(?:overrides?|arguments?|parameters?)\b/iu);
  expect(description).toMatch(
    /\b(?:must not|do not|don't|never|avoid|omit|without)\b/iu
  );
  expect(description).not.toContain(contract.model.replace("[1m]", ""));
  expect(description).not.toMatch(/\b(?:low|medium|high|xhigh|max)\b/iu);
  if (contract.name === "astra") {
    expect(description).toMatch(/\bonly\b/iu);
    expect(description).toMatch(/\bexplicit(?:ly)?\b/iu);
    expect(description).toMatch(/\brequest(?:ed)?\b/iu);
  }
  return description;
};

const tableCells = (line: string): string[] =>
  line
    .split("|")
    .slice(1, -1)
    .map((cell) => cell.trim().replaceAll("`", ""));

const assertModelInventory = (
  readme: string,
  contracts: readonly ModelAgentContract[]
): void => {
  const tables = readme
    .split(/\r?\n\r?\n/u)
    .filter(
      (block) =>
        tableCells(block.split(/\r?\n/u)[0]).join("|") ===
        "Agent|Model|Effort|Tier"
    );
  expect(tables).toHaveLength(1);
  expect(tables[0].split(/\r?\n/u).slice(2).map(tableCells)).toEqual(
    contracts.map((contract) => [
      contract.name,
      contract.model,
      contract.effort,
      String(contract.tier)
    ])
  );
};

test.each(modelAgentPackages)(
  "validates the registered $name source inventory",
  (plugin) => {
    const root = new URL(`../../plugins/${plugin.name}/`, import.meta.url);
    expect(
      readdirSync(new URL("agents/", root))
        .filter((file) => file.endsWith(".md"))
        .toSorted()
    ).toEqual(plugin.agents.map((agent) => `${agent.name}.md`).toSorted());
    for (const contract of plugin.agents) {
      assertModelAgent(readModelAgent(plugin.name, contract.name), contract);
    }
    assertModelInventory(
      readFileSync(new URL("README.md", root), "utf-8"),
      plugin.agents
    );
  }
);

test("keeps combined model-agent descriptions below the host warning budget", () => {
  const descriptions = modelAgentPackages.flatMap((plugin) =>
    plugin.agents.map((contract) =>
      assertModelAgent(readModelAgent(plugin.name, contract.name), contract)
    )
  );
  expect(Buffer.byteLength(descriptions.join(" "))).toBeLessThan(15_000);
});

const withDescription = (source: string, description: string): string =>
  source.replace(
    /description: >-\r?\n[\s\S]*?\r?\nmodel:/u,
    `description: >-\n  ${description}\nmodel:`
  );

test.each(modelAgentPackages)(
  "allows harmless $name description rewording",
  (plugin) => {
    for (const contract of plugin.agents) {
      const restriction =
        contract.name === "astra" ? " Only use when explicitly requested." : "";
      const description = `Matches Claude ${contract.family}. Do not pass model or effort arguments.${restriction}`;
      expect(() =>
        assertModelAgent(
          withDescription(
            readModelAgent(plugin.name, contract.name),
            description
          ),
          contract
        )
      ).not.toThrow();
    }
  }
);

test.each([
  ["missing context suffix", (source: string) => source.replace("[1m]", "")],
  [
    "changed model",
    (source: string) => source.replace("gpt-6-luna", "other-model")
  ],
  [
    "changed effort",
    (source: string) => source.replace("effort: high", "effort: low")
  ],
  ["non-empty body", (source: string) => `${source}\n# Role\n`],
  ["empty description", (source: string) => withDescription(source, "")],
  [
    "missing caller rule",
    (source: string) => withDescription(source, "Equivalent to Claude Haiku.")
  ],
  [
    "wrong equivalent",
    (source: string) =>
      withDescription(
        source,
        "Matches Claude Opus. Do not pass model or effort arguments."
      )
  ],
  [
    "multiple equivalents",
    (source: string) =>
      withDescription(
        source,
        "Matches Claude Haiku and Claude Sonnet. Do not pass model or effort arguments."
      )
  ],
  [
    "configured effort in description",
    (source: string) =>
      withDescription(
        source,
        "Matches Claude Haiku at high effort. Do not pass model or effort arguments."
      )
  ],
  [
    "duplicate model key",
    (source: string) =>
      source.replace("effort: high", "model: gpt-6-luna[1m]\neffort: high")
  ]
] as const)("rejects %s in a model-agent definition", (_label, mutate) => {
  const [contract] = modelAgentPackages[0].agents;
  expect(() =>
    assertModelAgent(
      mutate(readModelAgent("gpt-agents", contract.name)),
      contract
    )
  ).toThrow();
});

test("rejects a missing Astra explicit-request restriction", () => {
  const contract = modelAgentPackages[0].agents.find(
    (agent) => agent.name === "astra"
  );
  if (!contract) {
    throw new Error("Missing Astra contract");
  }
  const source = withDescription(
    readModelAgent("gpt-agents", "astra"),
    "Equivalent to Claude Fable. Never supply model or effort overrides."
  );
  expect(() => assertModelAgent(source, contract)).toThrow();
});

test.each(modelAgentPackages)("rejects $name README model drift", (plugin) => {
  const readme = readFileSync(
    new URL(`../../plugins/${plugin.name}/README.md`, import.meta.url),
    "utf-8"
  );
  const drifted = readme.replaceAll(plugin.agents[0].model, "other-model[1m]");
  expect(() => assertModelInventory(drifted, plugin.agents)).toThrow();
});
