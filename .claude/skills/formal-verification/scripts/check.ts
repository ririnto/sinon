import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const [model, ...args] = process.argv.slice(2);
const topics = new Map([
  ["ChildLifecycle", "child-lifecycle"],
  ["DownloadLifecycle", "download-lifecycle"]
]);
const topic = model === undefined ? undefined : topics.get(model);
if (topic === undefined) {
  throw new Error("Choose ChildLifecycle or DownloadLifecycle.");
}
const cwd = process.env.INIT_CWD;

const base = fileURLToPath(
  new URL(`../assets/${topic}/${model}`, import.meta.url)
);
const tlc = fileURLToPath(
  new URL("../node_modules/tla-chaos-loop/tools/tla2tools.jar", import.meta.url)
);
const metadata = mkdtempSync(path.join(tmpdir(), "sinon-tlc-"));
try {
  const result = spawnSync(
    "java",
    [
      "-XX:+UseParallelGC",
      "-cp",
      tlc,
      "tlc2.TLC",
      "-workers",
      "1",
      "-metadir",
      metadata,
      "-config",
      `${base}.cfg`,
      `${base}.tla`,
      ...args
    ],
    { cwd: cwd === "" ? undefined : cwd, stdio: "inherit" }
  );
  if (result.error) {
    throw result.error;
  }
  process.exitCode = result.status ?? 1;
} finally {
  rmSync(metadata, { force: true, recursive: true });
}
