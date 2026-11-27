import { execFileSync, spawnSync } from "node:child_process";
import { mkdir, mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

const root = path.resolve(import.meta.dirname, "..");
const checker = path.join(
  root,
  ".claude/skills/formal-verification/scripts/check-layout.ts"
);

test.each([
  { loader: "await import", comment: "", specifier: "pele", status: 1 },
  { loader: "await import", comment: "", specifier: "node:fs", status: 0 },
  {
    loader: "await import",
    comment: " /* note */",
    specifier: "pele",
    status: 1
  },
  {
    loader: "await import",
    comment: " /* note */",
    specifier: "node:fs",
    status: 0
  },
  { loader: "require", comment: "", specifier: "pele", status: 1 },
  { loader: "require", comment: "", specifier: "node:fs", status: 0 },
  { loader: "import", comment: "", specifier: "pele/$foo", status: 1 },
  { loader: "import", comment: "", specifier: `pele/\${name}`, status: 0 }
])(
  "classifies a literal template specifier of $loader($specifier$comment)",
  async ({ comment, loader, specifier, status }) => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-formal-layout-")
    );
    onTestFinished(async () => {
      await rm(directory, { recursive: true, force: true });
    });
    const objects = path.join(directory, "objects");
    await mkdir(objects);
    const gitObjects = execFileSync(
      "git",
      ["rev-parse", "--path-format=absolute", "--git-path", "objects"],
      { cwd: root, encoding: "utf-8" }
    ).trim();
    const options = {
      cwd: root,
      encoding: "utf-8",
      env: {
        ...process.env,
        GIT_ALTERNATE_OBJECT_DIRECTORIES: gitObjects,
        GIT_INDEX_FILE: path.join(directory, "index"),
        GIT_OBJECT_DIRECTORY: objects
      }
    } as const;
    execFileSync("git", ["read-tree", "--empty"], options);
    const entry = "scripts/template-import.ts";
    const source = [loader, "(", "`", specifier, "`", comment, ");\n"].join("");
    const blob = execFileSync("git", ["hash-object", "-w", "--stdin"], {
      ...options,
      input: source
    }).trim();
    execFileSync(
      "git",
      ["update-index", "--add", "--cacheinfo", "100644", blob, entry],
      options
    );
    const tree = execFileSync("git", ["write-tree"], options).trim();
    for (const args of [[checker], [checker, tree]]) {
      const result = spawnSync(process.execPath, args, options);
      expect(result.error).toBeUndefined();
      expect(result.status, result.stderr).toBe(status);
      expect(result.stderr.includes(entry)).toBe(status === 1);
    }
  }
);
