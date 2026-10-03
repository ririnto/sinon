import {
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  statSync,
  symlinkSync,
  utimesSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import {
  createCodexMarketplace,
  syncCodexMarketplace
} from "../../marketplace/generate.js";

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
    claude?: Record<string, unknown>;
    portable?: Record<string, unknown>;
  } = {}
): void => {
  const pluginRoot = path.join(root, "plugins", directory);
  const manifest = {
    author: { name: "Sinon" },
    description: "Portable plugin description.",
    license: "MIT",
    name: directory,
    version: "1.0.0",
    ...overrides.portable
  };
  const claudeManifest = {
    ...manifest,
    ...overrides.claude
  };
  mkdirSync(path.join(pluginRoot, ".claude-plugin"), { recursive: true });
  writeFileSync(
    path.join(pluginRoot, "plugin.json"),
    `${JSON.stringify(manifest, null, 2)}\n`
  );
  writeFileSync(
    path.join(pluginRoot, ".claude-plugin/plugin.json"),
    `${JSON.stringify(claudeManifest, null, 2)}\n`
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
  ).rejects.toThrow('Unsupported marketplace source "npm"');
  await expect(
    createCodexMarketplace(
      catalog([localEntry("helper"), localEntry("helper")]),
      root
    )
  ).rejects.toThrow("Duplicate marketplace plugin name: helper");
  await expect(
    createCodexMarketplace(
      catalog([localEntry("first"), localEntry("second", "./plugins/first")]),
      root
    )
  ).rejects.toThrow("Duplicate local marketplace source path");
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
  ).rejects.toThrow("Duplicate local marketplace plugin directory");
});

test("rejects package identity and metadata disagreement", async () => {
  const root = makeRoot();
  writePlugin(root, "helper", { portable: { name: "different" } });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow("Source marketplace name for helper does not match");
  writePlugin(root, "helper", {
    claude: { description: "Different package description." }
  });
  await expect(
    createCodexMarketplace(catalog([localEntry("helper")]), root)
  ).rejects.toThrow(
    "Portable manifest description for helper does not match Claude manifest"
  );
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), license: "Apache-2.0" }]),
      root
    )
  ).rejects.toThrow(
    "Source marketplace license for helper does not match portable manifest"
  );
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), version: "2.0.0" }]),
      root
    )
  ).rejects.toThrow("Source marketplace version for helper does not match");
  await expect(
    createCodexMarketplace(
      catalog([{ ...localEntry("helper"), author: { name: "Other" } }]),
      root
    )
  ).rejects.toThrow("Source marketplace author name for helper does not match");
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
  ).rejects.toThrow("resolves outside the repository root");
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
  await expect(syncCodexMarketplace(source, root, true)).rejects.toThrow(
    ".agents/plugins/marketplace.json is out of date"
  );
  expect(readFileSync(outputPath, "utf-8")).toBe(drift);
});
