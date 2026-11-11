import { spawnSync } from "node:child_process";
import { mkdtemp, readFile, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { RuleTester } from "oxlint/plugins-dev";
import { describe, expect, test } from "vitest";

import plugin from "../../../../no-direct-use-effect.js";
import { configureBaseline } from "../../../scripts/configure.js";
import { runUltracite } from "../../../tests/ultracite-fixture.js";

const pluginSources = [
  new URL("../../../../no-direct-use-effect.ts", import.meta.url),
  new URL("../plugins/no-direct-use-effect.ts", import.meta.url)
] as const;
const cases = [
  ["named.ts", 'import { useEffect } from "react"; useEffect(() => {});', 1],
  [
    "alias.ts",
    'import { useEffect as mount } from "react"; mount(() => {});',
    1
  ],
  ["namespace.ts", 'import * as R from "react"; R.useEffect(() => {});', 1],
  ["default.ts", 'import R from "react"; R.useEffect(() => {});', 1],
  [
    "named-default.ts",
    'import { default as R } from "react"; R.useEffect(() => {});',
    1
  ],
  ["computed.ts", 'import * as R from "react"; R["useEffect"](() => {});', 1],
  [
    "string-import.ts",
    'import { "useEffect" as mount } from "react"; mount(() => {});',
    1
  ],
  [
    "optional.ts",
    'import { useEffect } from "react"; useEffect?.(() => {});',
    1
  ],
  ["optional-member.ts", 'import R from "react"; R?.useEffect?.(() => {});', 1],
  [
    "assertion.ts",
    'import { useEffect } from "react"; (useEffect as Function)(() => {});',
    1
  ],
  [
    "sequence.ts",
    'import { useEffect } from "react"; (0, useEffect)(() => {});',
    1
  ],
  [
    "const-alias.ts",
    'import { useEffect } from "react"; const mount = useEffect; mount(() => {});',
    1
  ],
  [
    "object-alias.ts",
    'import * as R from "react"; const framework = R; framework.useEffect(() => {});',
    1
  ],
  [
    "destructure.ts",
    'import R from "react"; const { useEffect: mount } = R; mount(() => {});',
    1
  ],
  ["require.ts", 'const R = require("react"); R.useEffect(() => {});', 1],
  [
    "require-destructure.ts",
    'const { useEffect: mount } = require("react"); mount(() => {});',
    1
  ],
  ["require-member.ts", 'require("react").useEffect(() => {});', 1],
  [
    "framework.ts",
    'import { useEffect } from "framework"; useEffect(() => {});',
    0
  ],
  [
    "framework-hook.ts",
    'import { useMount } from "framework"; useMount(() => {});',
    0
  ],
  [
    "preact.ts",
    'import { useEffect } from "preact/hooks"; useEffect(() => {});',
    0
  ],
  [
    "shadow.ts",
    'import { useEffect } from "react"; export function view(useEffect: Function) { useEffect(); }',
    0
  ],
  [
    "local.tsx",
    'import { useState } from "react"; const useEffect = () => {}; useEffect();',
    0
  ],
  ["unbound.tsx", "useEffect(() => {}); framework.useEffect(() => {});", 0],
  [
    "type-import.ts",
    'import type { useEffect } from "react"; useEffect(() => {});',
    0
  ],
  [
    "type-specifier.ts",
    'import { type useEffect } from "react"; useEffect(() => {});',
    0
  ],
  [
    "type-evidence.ts",
    'import type { ComponentType } from "react"; useEffect(() => {});',
    0
  ],
  [
    "reassigned.ts",
    'import { useEffect } from "react"; export function view(other: Function) { let mount = useEffect; mount = other; mount(); }',
    0
  ],
  [
    "shadow-require.ts",
    'export function view(require: Function) { const R = require("react"); R.useEffect(() => {}); }',
    0
  ],
  [
    "non-call.ts",
    'import { useEffect } from "react"; export const hook = useEffect;',
    0
  ]
] as const;

RuleTester.describe = (name, run) => {
  describe(name, run);
};
RuleTester.it = test;

new RuleTester({
  languageOptions: { env: { node: true }, parserOptions: { lang: "ts" } }
}).run(
  "react-effects/no-direct-use-effect",
  plugin.rules["no-direct-use-effect"],
  {
    invalid: cases
      .filter((fixture) => fixture[2] > 0)
      .map(([filename, code, count]) => ({
        code,
        errors: Array.from({ length: count }, () => ({
          messageId: "noDirectUseEffect"
        })),
        filename,
        output: null
      })),
    valid: cases
      .filter((fixture) => fixture[2] === 0)
      .map(([filename, code]) => ({ code, filename }))
  }
);

test("root and materialized configs activate the adopted React rules", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-react-compiler-"));
  try {
    await configureBaseline(directory, "node", "eligible");
    await writeFile(path.join(directory, ".oxlintrc.json"), "{}\n");
    await symlink(
      new URL("../../../../../node_modules", import.meta.url),
      path.join(directory, "node_modules"),
      "dir"
    );
    const compilerCases = [
      [
        "direct.tsx",
        'import { useEffect } from "react"; useEffect(() => {});',
        "react-effects(no-direct-use-effect)",
        'import { useMount } from "framework"; useMount(() => {});'
      ],
      [
        "render.tsx",
        'import { useState } from "react"; export function Counter() { const [count, setCount] = useState(0); setCount(count + 1); return <div>{count}</div>; }',
        "react-hooks-js(set-state-in-render)",
        'import { useState } from "react"; export function Counter() { const [count, setCount] = useState(0); return <button onClick={() => setCount(count + 1)}>{count}</button>; }'
      ],
      [
        "effect.tsx",
        'import { useEffect, useState } from "react"; export function Counter() { const [count, setCount] = useState(0); useEffect(() => { setCount(1); }, []); return <div>{count}</div>; }',
        "react-hooks-js(set-state-in-effect)",
        'import { useEffect, useState } from "react"; export function Counter() { const [count, setCount] = useState(0); useEffect(() => { const id = setInterval(() => setCount(c => c + 1), 1000); return () => clearInterval(id); }, []); return <div>{count}</div>; }'
      ],
      [
        "mutation.tsx",
        "export function Counter({ value }: { value: { count: number } }) { value.count += 1; return <div>{value.count}</div>; }",
        "react-hooks-js(immutability)",
        "export function Counter({ value }: { value: { count: number } }) { const local = { ...value }; local.count += 1; return <div>{local.count}</div>; }"
      ],
      [
        "random.tsx",
        "export function Counter() { return <div>{Math.random()}</div>; }",
        "react-hooks-js(purity)",
        "export function Counter() { return <div>{42}</div>; }"
      ],
      [
        "hooks.tsx",
        'import { useState } from "react"; export function Counter({ ready }: { ready: boolean }) { if (ready) { const [count] = useState(0); return <div>{count}</div>; } return null; }',
        "react-hooks-js(rules-of-hooks)",
        'import { useState } from "react"; export function Counter() { const [count] = useState(0); return <div>{count}</div>; }'
      ],
      [
        "spread.ts",
        "const items = [1, 2]; items.reduce((acc: number[], item) => [...acc, item], []);",
        "oxc(no-accumulating-spread)",
        "const items = [1, 2]; const copy = [...items]; copy.reduce((acc: number[], item) => { acc.push(item); return acc; }, []);"
      ]
    ] as const;
    await Promise.all(
      compilerCases.flatMap(([filename, text, , negative]) => [
        writeFile(path.join(directory, filename), `${text}\n`),
        writeFile(path.join(directory, `valid-${filename}`), `${negative}\n`)
      ])
    );
    await writeFile(
      path.join(directory, "valid.tsx"),
      'import { useState } from "react"; import { useMount } from "framework"; export function Counter() { const [count, setCount] = useState(0); useMount(() => {}); return <button onClick={() => setCount(count + 1)}>{count}</button>; }\n'
    );
    await writeFile(
      path.join(directory, "unadopted.tsx"),
      "export const labels = [<span />];\n"
    );
    for (const configuration of [
      path.resolve(import.meta.dirname, "../../../../../oxlint.config.ts"),
      path.join(directory, "oxlint.config.ts")
    ]) {
      const result = runUltracite(directory, ".", "check", configuration);
      expect(result.status).toBe(1);
      expect(
        result.diagnostics.filter(
          ({ code }) => code?.startsWith("react(") === true
        )
      ).toEqual([]);
      for (const [filename, , code] of compilerCases) {
        expect(
          result.diagnostics.some(
            (diagnostic) =>
              diagnostic.filename === filename && diagnostic.code === code
          ),
          `${filename}: ${code}`
        ).toBe(true);
        expect(
          result.diagnostics.filter(
            (diagnostic) =>
              diagnostic.filename === `valid-${filename}` &&
              diagnostic.code === code
          ),
          `valid-${filename}: ${code}`
        ).toEqual([]);
      }
      expect(
        result.diagnostics.filter(
          ({ filename, code }) =>
            filename === "valid.tsx" &&
            (code?.startsWith("react(") === true ||
              code?.startsWith("react-hooks-js(") === true ||
              code?.startsWith("react-effects(") === true ||
              code === "oxc(no-accumulating-spread)")
        )
      ).toEqual([]);
    }
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 60_000);

test("Harness materializes the React policy dependency and package-script execution path", async () => {
  const directory = await mkdtemp(path.join(tmpdir(), "sinon-react-install-"));
  try {
    await configureBaseline(directory, "react", "eligible");
    await symlink(
      new URL("../../../../../node_modules", import.meta.url),
      path.join(directory, "node_modules"),
      "dir"
    );
    expect(
      JSON.parse(await readFile(path.join(directory, "package.json"), "utf-8"))
    ).toMatchObject({
      devDependencies: {
        "@oxlint/plugins": "catalog:",
        "eslint-plugin-react-hooks": "catalog:",
        oxlint: "catalog:",
        ultracite: "catalog:"
      },
      scripts: {
        "check:ultracite":
          "ultracite check --type-aware --config oxlint.config.ts"
      }
    });
    await writeFile(
      path.join(directory, "sample.ts"),
      'import { useEffect } from "react"; useEffect(() => {});\n'
    );
    const result = spawnSync("pnpm", ["run", "check:ultracite"], {
      cwd: directory,
      encoding: "utf-8",
      timeout: 60_000
    });
    expect(result.error).toBeUndefined();
    expect(result.status).toBe(1);
    expect(`${result.stdout}\n${result.stderr}`).toContain(
      "react-effects(no-direct-use-effect)"
    );
  } finally {
    await rm(directory, { force: true, recursive: true });
  }
}, 90_000);

test("standalone and Harness copies preserve one canonical plugin without local imports", async () => {
  const [standalone, harness] = await Promise.all(
    pluginSources.map(async (source) => await readFile(source, "utf-8"))
  );
  expect(standalone).toBe(harness);
  expect(standalone).not.toMatch(/from\s*["']\./u);
  expect(plugin.meta.name).toBe("react-effects");
  expect(Object.keys(plugin.rules)).toEqual(["no-direct-use-effect"]);
  expect(plugin.rules["no-direct-use-effect"].meta).not.toHaveProperty(
    "fixable"
  );
});
