import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

import { isMap, isScalar, isSeq, parseDocument } from "yaml";

const root = path.resolve(import.meta.dirname, "../..");
const write = process.argv.includes("--write");
/** @type {unknown} */
const manifest = JSON.parse(
  await readFile(path.join(root, "package.json"), "utf-8")
);
if (
  typeof manifest !== "object" ||
  manifest === null ||
  !("engines" in manifest) ||
  typeof manifest.engines !== "object" ||
  manifest.engines === null ||
  !("node" in manifest.engines) ||
  typeof manifest.engines.node !== "string"
) {
  throw new TypeError("Expected a Node runtime declaration");
}
const major = /^\^(?<major>\d+)\.\d+\.\d+$/u.exec(manifest.engines.node)?.groups
  ?.major;
if (major === undefined || major === "") {
  throw new Error("Node engine must specify a caret range with a full version");
}
const workspace = parseDocument(
  await readFile(path.join(root, "pnpm-workspace.yaml"), "utf-8")
);
const files = [
  "plugins/harness/tooling/pnpm/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/frontend/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/react/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/pnpm/api-docs.pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/pnpm/vue.pnpm-workspace.fragment.yaml"
];
const snapshots = await Promise.all(
  files.map(async (file) => {
    const target = path.join(root, file);
    const original = await readFile(target, "utf-8");
    const document = parseDocument(original);
    const snapshot = document.get("catalog", true);
    const catalogName = ["api-docs", "vue"].find((name) => file.includes(name));
    const catalog =
      catalogName === undefined
        ? workspace.get("catalog", true)
        : workspace.getIn(["catalogs", catalogName], true);
    if (!isMap(snapshot) || !isMap(catalog)) {
      throw new TypeError("Expected catalog mappings");
    }
    for (const { key } of snapshot.items) {
      if (!isScalar(key) || typeof key.value !== "string") {
        throw new TypeError("Expected catalog package name");
      }
      const name = key.value;
      const version = catalog.get(name);
      if (typeof version !== "string") {
        throw new TypeError(`Missing root catalog version for ${name}`);
      }
      snapshot.set(name, version);
    }
    return { file, original, target, updated: String(document) };
  })
);
const runtimeFile = "plugins/harness/tooling/pnpm/runtime.fragment.json";
const runtimePath = path.join(root, runtimeFile);
const originalRuntime = await readFile(runtimePath, "utf-8");
/** @type {unknown} */
const runtime = JSON.parse(originalRuntime);
if (
  typeof runtime !== "object" ||
  runtime === null ||
  !("engines" in runtime) ||
  typeof runtime.engines !== "object" ||
  runtime.engines === null
) {
  throw new TypeError("Expected runtime settings");
}
const updatedRuntime = {
  ...runtime,
  engines: { ...runtime.engines, node: manifest.engines.node }
};
const runtimeSnapshot = {
  file: runtimeFile,
  original: originalRuntime,
  target: runtimePath,
  updated: `${JSON.stringify(updatedRuntime, null, 2)}\n`
};
const ciSnapshots = await Promise.all(
  [
    ["plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml", "gitlab"],
    ["plugins/harness/ci/github/pnpm.yaml", "github"]
  ].map(async ([file, type]) => {
    const target = path.join(root, file);
    const original = await readFile(target, "utf-8");
    const document = parseDocument(original);
    const steps = document.getIn(["jobs", "pnpm", "steps"], true);
    const step = isSeq(steps)
      ? steps.items.find((item) => {
          if (!isMap(item)) {
            return false;
          }
          const uses = item.get("uses");
          return (
            typeof uses === "string" && uses.startsWith("actions/setup-node@")
          );
        })
      : null;
    const nodeVersion = isMap(step)
      ? step.getIn(["with", "node-version"], true)
      : null;
    const scalar =
      type === "gitlab" ? document.getIn(["pnpm", "image"], true) : nodeVersion;
    const value = type === "gitlab" ? `node:${major}` : Number(major);
    if (!isScalar(scalar) || scalar.range === undefined) {
      throw new Error(`Missing Node runtime in ${file}`);
    }
    const updated =
      scalar.value === value
        ? original
        : `${original.slice(0, scalar.range[0])}${value}${original.slice(scalar.range[1])}`;
    return { file, original, target, updated };
  })
);
const changes = [...snapshots, runtimeSnapshot, ...ciSnapshots].filter(
  ({ original, updated }) => original !== updated
);
if (write) {
  await Promise.all(
    changes.map(async ({ target, updated }) => {
      await writeFile(target, updated);
    })
  );
} else if (changes.length > 0) {
  for (const { file } of changes) {
    process.stderr.write(`${file} is out of sync\n`);
  }
  process.exitCode = 1;
}
