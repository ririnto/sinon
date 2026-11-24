import { readFileSync } from "node:fs";
import path from "node:path";

import { expect, test } from "vitest";

import claudeManifest from "../.claude-plugin/plugin.json" with { type: "json" };
import codexManifest from "../.codex-plugin/plugin.json" with { type: "json" };

test("tlaplus-mcp selects the same tlaplus stdio launch on Claude Code and Codex", () => {
  const claudeServer = claudeManifest.mcpServers.tlaplus;
  expect(
    claudeServer,
    "Claude Code must declare the tlaplus stdio server"
  ).toHaveProperty("type", "stdio");
  expect(claudeServer.command).toMatch(/\S/u);
  expect(claudeServer.args).toBeInstanceOf(Array);

  const codexConfig: unknown = JSON.parse(
    readFileSync(
      path.join(import.meta.dirname, "..", codexManifest.mcpServers),
      "utf-8"
    )
  );
  expect(
    codexConfig,
    "Codex must match the Claude Code tlaplus stdio command and arguments"
  ).toMatchObject({
    mcpServers: {
      tlaplus: {
        type: "stdio",
        command: claudeServer.command,
        args: claudeServer.args
      }
    }
  });
});
