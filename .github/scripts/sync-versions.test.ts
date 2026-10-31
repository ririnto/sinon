import { spawnSync } from "node:child_process";
import {
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";
import { parse } from "yaml";

const root = path.resolve(import.meta.dirname, "../..");

test("version snapshots detect drift, update, and remain self-contained", async () => {
  const fixture = await mkdtemp(path.join(tmpdir(), "sinon-version-sync-"));
  onTestFinished(() => rm(fixture, { force: true, recursive: true }));
  await mkdir(path.join(fixture, ".github/scripts"), { recursive: true });
  await cp(
    path.join(root, ".github/scripts/sync-versions.mjs"),
    path.join(fixture, ".github/scripts/sync-versions.mjs")
  );
  await symlink(
    path.join(root, "node_modules"),
    path.join(fixture, "node_modules"),
    "dir"
  );
  await writeFile(
    path.join(fixture, "package.json"),
    JSON.stringify({ engines: { node: "^26.1.0" } })
  );
  await writeFile(
    path.join(fixture, "pnpm-workspace.yaml"),
    'catalog:\n  example: "^2.0.0"\ncatalogs:\n  api-docs:\n    example: "^3.0.0"\n  vue:\n    example: "^4.0.0"\n'
  );
  await Promise.all(
    [
      "plugins/harness/tooling/pnpm",
      "plugins/harness/tooling/frontend",
      "plugins/harness/tooling/react"
    ].map(async (directory) => {
      await mkdir(path.join(fixture, directory), { recursive: true });
      await writeFile(
        path.join(fixture, directory, "pnpm-workspace.fragment.yaml"),
        'catalog:\n  example: "^1.0.0"\n'
      );
    })
  );
  await writeFile(
    path.join(
      fixture,
      "plugins/harness/tooling/pnpm/api-docs.pnpm-workspace.fragment.yaml"
    ),
    'catalog:\n  example: "^1.0.0"\n'
  );
  await writeFile(
    path.join(
      fixture,
      "plugins/harness/tooling/pnpm/vue.pnpm-workspace.fragment.yaml"
    ),
    'catalog:\n  example: "^1.0.0"\n'
  );
  await mkdir(path.join(fixture, "plugins/harness/ci/gitlab"), {
    recursive: true
  });
  await mkdir(path.join(fixture, "plugins/harness/ci/github"), {
    recursive: true
  });
  await writeFile(
    path.join(fixture, "plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml"),
    "pnpm:\n  image: node:24\n"
  );
  await writeFile(
    path.join(fixture, "plugins/harness/ci/github/pnpm.yaml"),
    "jobs:\n  pnpm:\n    steps:\n      - uses: actions/setup-node@v7\n        with:\n          node-version: 24\n"
  );
  await writeFile(
    path.join(fixture, "plugins/harness/tooling/pnpm/runtime.fragment.json"),
    JSON.stringify({ engines: { node: "^24.0.0" }, private: true })
  );
  const script = path.join(fixture, ".github/scripts/sync-versions.mjs");
  expect(spawnSync(process.execPath, [script]).status).toBe(1);
  expect(spawnSync(process.execPath, [script, "--write"]).status).toBe(0);
  expect(spawnSync(process.execPath, [script]).status).toBe(0);
  const runtime = JSON.parse(
    await readFile(
      path.join(fixture, "plugins/harness/tooling/pnpm/runtime.fragment.json"),
      "utf-8"
    )
  );
  const workspace = parse(
    await readFile(
      path.join(
        fixture,
        "plugins/harness/tooling/frontend/pnpm-workspace.fragment.yaml"
      ),
      "utf-8"
    )
  );
  expect(runtime.engines.node).toBe("^26.1.0");
  expect(workspace.catalog.example).toBe("^2.0.0");
  const docsCatalog = parse(
    await readFile(
      path.join(
        fixture,
        "plugins/harness/tooling/pnpm/api-docs.pnpm-workspace.fragment.yaml"
      ),
      "utf-8"
    )
  );
  expect(docsCatalog.catalog.example).toBe("^3.0.0");
  const vueCatalog = parse(
    await readFile(
      path.join(
        fixture,
        "plugins/harness/tooling/pnpm/vue.pnpm-workspace.fragment.yaml"
      ),
      "utf-8"
    )
  );
  expect(vueCatalog.catalog.example).toBe("^4.0.0");
  const ci = parse(
    await readFile(
      path.join(fixture, "plugins/harness/ci/gitlab/pnpm.gitlab-ci.yaml"),
      "utf-8"
    )
  );
  expect(ci.pnpm.image).toBe("node:26");
  const github = parse(
    await readFile(
      path.join(fixture, "plugins/harness/ci/github/pnpm.yaml"),
      "utf-8"
    )
  );
  expect(github.jobs.pnpm.steps[0].with["node-version"]).toBe(26);
});
