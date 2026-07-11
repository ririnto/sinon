import { expect, test } from "bun:test";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

test("manifest parsing preserves optional Codex counterpart fields", async () => {
  const directory = await mkdtemp(
    path.join(tmpdir(), "sinon-routing-manifest-")
  );
  const manifestPath = path.join(directory, "agent-routing-manifest.json");
  try {
    await writeFile(
      manifestPath,
      `${JSON.stringify({
        agents: [
          {
            access: "router",
            claudeEffort: "medium",
            claudeModel: "sonnet",
            claudePath: "plugins/example/agents/router.md",
            name: "example-router",
            topology: "leaf"
          }
        ],
        schemaVersion: 1
      })}\n`,
      "utf-8"
    );
    const { parseAgentRoutingManifest } =
      await import("./agent-routing-manifest.js");
    const errors: string[] = [];

    const manifest = parseAgentRoutingManifest(manifestPath, errors);

    expect(errors).toEqual([]);
    expect(manifest?.agents[0]?.access).toBe("router");
    expect(manifest?.agents[0]?.codexPath).toBeUndefined();
    expect(manifest?.agents[0]?.name).toBe("example-router");
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
});
