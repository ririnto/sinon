import { readFileSync } from "node:fs";
import path from "node:path";

import { expect, test } from "vitest";

import claudeManifest from "../.claude-plugin/plugin.json" with { type: "json" };
import codexManifest from "../.codex-plugin/plugin.json" with { type: "json" };

test("Claude Code and Codex declare the documented tlaplus stdio launch", () => {
  const expectedServer = {
    type: "stdio",
    command: "npx",
    args: ["--yes", "--ignore-scripts", "tlaplus-mcp@1.0.3"]
  };
  expect(
    claudeManifest.mcpServers.tlaplus,
    "Claude Code must declare the documented tlaplus launch"
  ).toMatchObject(expectedServer);
  const codexConfig: unknown = JSON.parse(
    readFileSync(
      path.join(import.meta.dirname, "..", codexManifest.mcpServers),
      "utf-8"
    )
  );
  expect(
    codexConfig,
    "Codex must declare the documented tlaplus launch"
  ).toMatchObject({
    mcpServers: {
      tlaplus: expectedServer
    }
  });
});
