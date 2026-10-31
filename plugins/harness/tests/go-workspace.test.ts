import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

const script = path.resolve(
  import.meta.dirname,
  "../tooling/go/scripts/go-check.sh"
);

const runGoCheck = (
  scriptPath: string,
  root: string,
  check: string,
  env: NodeJS.ProcessEnv
) =>
  spawnSync("sh", [scriptPath, check], {
    cwd: root,
    encoding: "utf-8",
    env
  });

test.each(["lint", "format", "nilaway", "test"] as const)(
  "runs %s in every workspace module, including paths with spaces",
  async (check) => {
    const root = await mkdtemp(path.join(tmpdir(), "go workspace "));
    onTestFinished(() => rm(root, { force: true, recursive: true }));
    const bin = path.join(root, "bin");
    const first = path.join(root, "first module");
    const second = path.join(root, "second module");
    const trace = path.join(root, "trace");
    await Promise.all([bin, first, second].map((dir) => mkdir(dir)));
    const fakeGo = `#!/usr/bin/env sh
if [ "$1" = list ] && [ "$GOWORK" = off ]; then
  printf 'example.com/%s\\n' "$(basename "$PWD" | cut -d ' ' -f 1)"
elif [ "$1" = list ]; then
  printf '%s\\n' "$FIRST" "$SECOND"
else
  printf '%s|go|%s\\n' "$PWD" "$*" >> "$TRACE"
  if [ "$FAIL_AT" = "$PWD" ]; then exit 7; fi
fi
`;
    const fakeTool = `#!/usr/bin/env sh
printf '%s|%s|%s\\n' "$PWD" "$(basename "$0")" "$*" >> "$TRACE"
if [ "$FAIL_AT" = "$PWD" ]; then exit 7; fi
`;
    await Promise.all(
      (
        [
          ["go", fakeGo],
          ["golangci-lint", fakeTool],
          ["nilaway", fakeTool]
        ] as const
      ).map(([name, contents]) =>
        writeFile(path.join(bin, name), contents, { mode: 0o755 })
      )
    );
    const env = {
      ...process.env,
      FIRST: first,
      PATH: `${bin}${path.delimiter}${process.env.PATH}`,
      SECOND: second,
      TRACE: trace
    };
    const expected = {
      format: ["golangci-lint", "fmt --diff ./..."],
      lint: ["golangci-lint", "run ./..."],
      nilaway: ["nilaway", "-include-pkgs=example.com/first ./..."],
      test: ["go", "test -race -shuffle=on -count=1 ./..."]
    }[check];
    expect(runGoCheck(script, root, check, env).status).toBe(0);
    const actual = await readFile(trace, "utf-8");
    expect(actual).toContain(`${first}|${expected?.join("|")}\n`);
    expect(actual).toContain(
      `${second}|${expected?.join("|").replace("example.com/first", "example.com/second")}\n`
    );
    expect(
      runGoCheck(script, root, check, { ...env, FAIL_AT: first }).status
    ).toBe(7);
  }
);
