import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const [selectedModel, ...args] = process.argv.slice(2);
const topics = new Map([
  ["ChildLifecycle", "child-lifecycle"],
  ["DownloadLifecycle", "download-lifecycle"]
]);
const cwd = process.env.INIT_CWD;
const tlc = fileURLToPath(
  new URL("../node_modules/tla-chaos-loop/tools/tla2tools.jar", import.meta.url)
);

const models =
  selectedModel === undefined ? [...topics.keys()] : [selectedModel];
const results = await Promise.allSettled(
  models.map(async (model) => {
    const topic = topics.get(model);
    if (topic === undefined) {
      throw new Error("Choose ChildLifecycle or DownloadLifecycle.");
    }
    const base = fileURLToPath(
      new URL(`../assets/${topic}/${model}`, import.meta.url)
    );
    const metadata = mkdtempSync(path.join(tmpdir(), "sinon-tlc-"));
    try {
      const child = spawn(
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
      const outcome: unknown[] = await once(child, "close");
      const [status] = outcome;
      return typeof status === "number" ? status : 1;
    } finally {
      rmSync(metadata, { force: true, recursive: true });
    }
  })
);
for (const result of results) {
  if (result.status === "rejected") {
    throw result.reason;
  }
  if (process.exitCode === undefined && result.value !== 0) {
    process.exitCode = result.value;
  }
}
