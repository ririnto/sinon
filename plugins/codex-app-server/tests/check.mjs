import { spawnSync } from "node:child_process";
import { cp, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const temporary = await mkdtemp(path.join(tmpdir(), "sinon-mcp-tests-"));
try {
  await Promise.all(
    ["package.json", "server.mjs", "tests/server.test.mjs"].map((file) =>
      cp(path.join(root, file), path.join(temporary, file), { recursive: true })
    )
  );
  const installed = spawnSync(
    "npm",
    ["install", "--ignore-scripts", "--no-audit", "--no-fund"],
    {
      cwd: temporary,
      env: {
        ...process.env,
        NODE_USE_SYSTEM_CA: "1",
        npm_config_cache: path.join(temporary, "npm-cache")
      },
      stdio: "inherit"
    }
  );
  if (installed.status !== 0) {
    throw new Error(
      `Runtime dependency installation failed: ${installed.status}`
    );
  }
  process.exitCode =
    spawnSync(process.execPath, ["--test", "tests/server.test.mjs"], {
      cwd: temporary,
      stdio: "inherit"
    }).status ?? 1;
} finally {
  await rm(temporary, { force: true, recursive: true });
}
