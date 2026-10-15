import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test } from "vitest";

import { runUltracite } from "../../tests/ultracite-fixture.js";

const findings = (result: ReturnType<typeof runUltracite>) =>
  result.diagnostics.filter(({ code }) =>
    code?.includes("no-floating-promises")
  );

test("Ultracite executes type-aware promise diagnostics without rewriting void", async () => {
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
        "void returned().catch(() => {});",
        "void 42;"
      ].join("\n")
    );
    const configuration = {
      options: { typeAware: true },
      rules: {
        "typescript/no-floating-promises": ["error", { ignoreVoid: false }]
      }
    };
    await writeFile(
      path.join(directory, ".oxlintrc.json"),
      JSON.stringify(configuration)
    );
    const fixed = runUltracite(directory, "sample.ts", "fix");
    expect(findings(fixed)).toHaveLength(2);
    const text = await readFile(path.join(directory, "sample.ts"), "utf-8");
    expect(text).toContain("void work();");
    expect(text).toContain("void work().catch(");
    expect(text).toContain("void 42;");
    expect(findings(runUltracite(directory, "sample.ts"))).toHaveLength(2);
    expect(
      findings(
        runUltracite(
          directory,
          "sample.ts",
          "check",
          fileURLToPath(new URL("oxlint.config.ts", import.meta.url)),
          ["--type-aware"]
        )
      )
    ).toHaveLength(2);
    await writeFile(
      path.join(directory, ".oxlintrc.json"),
      JSON.stringify({ ...configuration, options: { typeAware: false } })
    );
    expect(findings(runUltracite(directory, "sample.ts"))).toHaveLength(0);
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 30_000);
