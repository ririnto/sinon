import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test } from "vitest";

import { runUltracite } from "../../tests/ultracite-fixture.js";

const findings = (result: ReturnType<typeof runUltracite>) =>
  result.diagnostics.filter(
    ({ code }) => code?.includes("no-floating-promises") === true
  );

test("Harness promise policy reports unhandled work without rewriting void", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-promises-"));
  try {
    await writeFile(
      path.join(directory, "tsconfig.json"),
      JSON.stringify({
        compilerOptions: { strict: true, target: "ES2022" },
        include: ["*.ts"]
      })
    );
    await writeFile(
      path.join(directory, "sample.ts"),
      [
        "async function work(): Promise<void> {}",
        "work();",
        "void work();",
        "void work().catch(() => {});",
        "async function handled() { await work(); }",
        "function returned() { return work(); }",
        "void handled().catch(() => {});",
        "void returned().catch(() => {});"
      ].join("\n")
    );
    await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
    const configuration = fileURLToPath(
      new URL("oxlint.config.ts", import.meta.url)
    );
    expect(
      findings(
        runUltracite(directory, "sample.ts", "fix", configuration, [
          "--type-aware"
        ])
      )
    ).toHaveLength(2);
    const text = await readFile(path.join(directory, "sample.ts"), "utf-8");
    expect(text).toContain("void work();");
    expect(text).toContain("void work().catch(");
    expect(
      findings(
        runUltracite(directory, "sample.ts", "check", configuration, [
          "--type-aware"
        ])
      )
    ).toHaveLength(2);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);
