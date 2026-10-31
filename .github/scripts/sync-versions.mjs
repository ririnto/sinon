import { readFile, writeFile } from "node:fs/promises";
import path from "node:path";

import { parseDocument } from "yaml";

const root = path.resolve(import.meta.dirname, "../..");
const write = process.argv.includes("--write");
const manifest = JSON.parse(
  await readFile(path.join(root, "package.json"), "utf-8")
);
const major = /^\^(?<major>\d+)\.\d+\.\d+$/u.exec(manifest.engines.node)?.groups
  ?.major;
if (!major) {
  throw new Error("Node engine must specify a caret range with a full version");
}
const workspace = parseDocument(
  await readFile(path.join(root, "pnpm-workspace.yaml"), "utf-8")
);
const files = [
  "plugins/harness/tooling/pnpm/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/frontend/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/react/pnpm-workspace.fragment.yaml",
  "plugins/harness/tooling/pnpm/api-docs.pnpm-workspace.fragment.yaml"
];
const snapshots = await Promise.all(
  files.map(async (file) => {
    const target = path.join(root, file);
    const original = await readFile(target, "utf-8");
    const document = parseDocument(original);
    const snapshot = document.get("catalog", true);
    const catalog = file.includes("api-docs")
      ? workspace.getIn(["catalogs", "api-docs"], true)
      : workspace.get("catalog", true);
    for (const { key } of snapshot.items) {
      const name = String(key.value);
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
const runtime = JSON.parse(originalRuntime);
runtime.engines.node = manifest.engines.node;
snapshots.push({
  file: runtimeFile,
  original: originalRuntime,
  target: runtimePath,
  updated: `${JSON.stringify(runtime, null, 2)}\n`
});
const ciSnapshots = await Promise.all(
  [
    ["plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml", "gitlab"],
    ["plugins/harness/ci/github/pnpm.yaml", "github"]
  ].map(async ([file, type]) => {
    const target = path.join(root, file);
    const original = await readFile(target, "utf-8");
    const document = parseDocument(original);
    let scalar;
    let value;
    if (type === "gitlab") {
      scalar = document.getIn(["pnpm", "image"], true);
      value = `node:${major}`;
    } else {
      const steps = document.getIn(["jobs", "pnpm", "steps"], true);
      const step = steps.items.find((item) =>
        String(item.get("uses")).startsWith("actions/setup-node@")
      );
      if (!step) {
        throw new Error("Missing actions/setup-node step");
      }
      scalar = step.getIn(["with", "node-version"], true);
      value = Number(major);
    }
    if (!scalar?.range) {
      throw new Error(`Missing Node runtime in ${file}`);
    }
    const updated =
      scalar.value === value
        ? original
        : `${original.slice(0, scalar.range[0])}${value}${original.slice(scalar.range[1])}`;
    return { file, original, target, updated };
  })
);
snapshots.push(...ciSnapshots);
const changes = snapshots.filter(
  ({ original, updated }) => original !== updated
);
if (write) {
  await Promise.all(
    changes.map(({ target, updated }) => writeFile(target, updated))
  );
} else if (changes.length > 0) {
  for (const { file } of changes) {
    process.stderr.write(`${file} is out of sync\n`);
  }
  process.exitCode = 1;
}
