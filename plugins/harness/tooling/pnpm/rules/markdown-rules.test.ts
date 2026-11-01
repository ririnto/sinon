import { spawnSync } from "node:child_process";
import {
  mkdtemp,
  mkdir,
  readFile,
  rm,
  symlink,
  writeFile
} from "node:fs/promises";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import path from "node:path";

import { applyFixes } from "markdownlint";
import type { Rule } from "markdownlint";
import { lint } from "markdownlint/sync";
import { describe, expect, test } from "vitest";

import noEmDash from "./no-em-dash.js";
import noRelativeSkillLinks from "./no-relative-skill-links.js";
import sensitiveContent from "./no-sensitive-content.mjs";
import tableSeparators from "./table-separators.js";

interface Violation {
  readonly detail: string | null;
  readonly lineNumber: number | null;
  readonly range: number[] | null;
}

const violations = (
  rule: Rule,
  input: string,
  name = "input"
): { fixed: string; found: Violation[] } => {
  const [ruleName] = rule.names;
  const errors =
    lint({
      config: { default: false, [ruleName ?? ""]: true },
      customRules: [rule],
      strings: { [name]: input }
    })[name] ?? [];
  return {
    fixed: applyFixes(input, errors),
    found: errors.map((error) => ({
      detail: error.errorDetail,
      lineNumber: error.lineNumber,
      range: error.errorRange
    }))
  };
};

const separatorDetail = (cell: number): string =>
  `Separator cell ${cell} must use exactly ---.`;

describe("sensitive-content semantics", () => {
  test("preserves the security identity and raw-line parser", () => {
    expect(sensitiveContent.names).toEqual(["sensitive-content"]);
    expect(sensitiveContent.tags).toEqual(["security"]);
    expect(sensitiveContent.parser).toBe("none");
  });

  test.each([
    ["Telegram", `123456:${"synthetic_control_".repeat(2)}`],
    ...["p", "o", "u", "s", "r"].map((kind) => [
      `GitHub ${kind}`,
      `gh${kind}_${"synthetic_control_".repeat(2)}`
    ]),
    ["GitHub fine-grained", `github_pat_${"synthetic_control_".repeat(3)}`],
    ...["AKIA", "ASIA"].map((prefix) => [
      `AWS ${prefix}`,
      `${prefix}${"A".repeat(16)}`
    ]),
    ["Google", `AIza${"a".repeat(35)}`],
    ...["b", "a", "p", "r", "s"].map((kind) => [
      `Slack ${kind}`,
      `xox${kind}-synthetic-control`
    ]),
    ["Bearer", `bEaReR ${"synthetic-control".repeat(2)}`],
    ["secret-key prefix", `sk-${"synthetic_control_".repeat(2)}`],
    ...[
      "TOKEN",
      "API_KEY",
      "API-KEY",
      "SECRET",
      "PASSWORD",
      "PRIVATE_KEY",
      "PRIVATE-KEY",
      "ACCESS_KEY",
      "ACCESS-KEY",
      "service_token_value"
    ].map((key) => [
      `assignment ${key}`,
      `${key} = "${"synthetic_control_".repeat(2)}"`
    ]),
    ["colon assignment", `token: '${"synthetic_control_".repeat(2)}'`],
    ["minimum assignment length", `TOKEN=${"z".repeat(20)}`],
    ["macOS home", "/Users/test-control/project"],
    ["Linux home", "/home/test-control/project"],
    ["Windows home", "C:\\Users\\test-control\\project"],
    ["case-insensitive Windows home", "c:\\users\\test-control\\project"],
    ["quoted home", '"/Users/test-control/project"'],
    ["inline-code home", "`/home/test-control/project`"],
    ["parenthesized home", "(/Users/test-control/project)"],
    ["Shared prefix boundary", "/Users/Shared-control/project"],
    ["Guest prefix boundary", "/Users/Guest-control/project"],
    ["shared exception case boundary", "/Users/shared/project"],
    ...["commit", "blob", "tree"].map((kind) => [
      `Git ${kind} URL`,
      `https://example.org/repo/${kind}/${"a".repeat(7)}`
    ]),
    ...[
      "hash",
      "git hash",
      "commit",
      "commit hash",
      "commit id",
      "revision",
      "file hash",
      "sha1",
      "sha-1",
      "sha256",
      "sha-256",
      "sha512",
      "sha-512",
      "md5"
    ].map((label) => [`hash label ${label}`, `${label}: ${"a".repeat(7)}`]),
    ["long labelled hash", `SHA-512=${"A".repeat(128)}`],
    ["bare SHA-1", "a".repeat(40)],
    ["bare SHA-256", "a".repeat(64)],
    ["credential despite placeholder word", `ghp_${"example_".repeat(3)}`],
    [
      "hash despite placeholder assignment",
      `TOKEN=example_${"z".repeat(20)} hash: ${"a".repeat(7)}`
    ]
  ])(
    "reports portable synthetic %s without fixes or value disclosure",
    (_name, value) => {
      const input = `${value}\n`;
      const errors =
        lint({
          config: { default: false, "sensitive-content": true },
          customRules: [sensitiveContent],
          strings: { input }
        }).input ?? [];
      expect(errors).toHaveLength(1);
      expect(errors).toEqual([
        expect.objectContaining({
          errorContext: null,
          errorDetail:
            "Remove credentials, local environment paths, and Git or file hashes",
          errorRange: null,
          fixInfo: null,
          lineNumber: 1,
          ruleNames: ["sensitive-content"]
        })
      ]);
      expect(JSON.stringify(errors)).not.toContain(value);
      expect(applyFixes(input, errors)).toBe(input);
    }
  );

  test.each([
    ["relative Unix path", "docs/project/file.md"],
    ["relative home segment", "./Users/test-control/project"],
    ["relative Windows path", "Users\\test-control\\project"],
    ["portable environment variable", "$HOME/project"],
    ["portable home shorthand", "~/project"],
    ["Shared home", "/Users/Shared/project"],
    ["Guest home", "/Users/Guest/project"],
    ["home prefix within a URL", "https://example.org/Users/synthetic-control"],
    ["incomplete home prefix", "/home/"],
    ["short assignment", `TOKEN=${"z".repeat(19)}`],
    ["unrecognized assignment", `MODE=${"z".repeat(20)}`],
    ...[
      "example",
      "sample",
      "replace",
      "placeholder",
      "changeme",
      "your_token",
      "your-token",
      "YOURTOKEN"
    ].map((placeholder) => [
      `assignment placeholder ${placeholder}`,
      `TOKEN=${placeholder}_${"z".repeat(20)}`
    ]),
    ["bracketed placeholder", "TOKEN=<portable-synthetic-token-value>"],
    ["short Telegram token", `123456:${"z".repeat(29)}`],
    ["short GitHub token", `ghp_${"z".repeat(19)}`],
    ["short AWS key", `AKIA${"A".repeat(15)}`],
    ["short Google key", `AIza${"z".repeat(34)}`],
    ["short Slack token", "xoxb-short"],
    ["short bearer", `Bearer ${"z".repeat(19)}`],
    ["short secret key", `sk-${"z".repeat(19)}`],
    ["named Git branch", "https://example.org/repo/tree/main"],
    ["short Git object ID", `commit ${"a".repeat(6)}`],
    ["unlabelled abbreviated hash", "a".repeat(7)],
    ["bare hash length boundary", "a".repeat(41)],
    ["non-hex identifier", "z".repeat(40)]
  ])("accepts portable %s", (_name, value) => {
    const input = `${value}\n`;
    expect(violations(sensitiveContent, input)).toEqual({
      fixed: input,
      found: []
    });
  });

  test("reports one diagnostic per raw line across Markdown contexts", () => {
    const input = [
      "Ordinary prose.",
      "path: /Users/test-control/project",
      "",
      "# /home/test-control/project",
      "> /Users/test-control/project",
      "```text",
      `/home/test-control/project TOKEN=${"z".repeat(20)} commit ${"a".repeat(7)}`,
      "```",
      "    /Users/test-control/project",
      "<div> /home/test-control/project </div>",
      ""
    ].join("\n");
    expect(violations(sensitiveContent, input)).toEqual({
      fixed: input,
      found: [2, 4, 5, 7, 9, 10].map((lineNumber) => ({
        detail:
          "Remove credentials, local environment paths, and Git or file hashes",
        lineNumber,
        range: null
      }))
    });
  });

  test("keeps other security checks beside placeholders", () => {
    const input = `TOKEN=example_${"z".repeat(20)} /Users/test-control/project\n`;
    expect(violations(sensitiveContent, input).found).toHaveLength(1);
  });

  test("preserves Markdownlint's source preprocessing", () => {
    const input =
      "---\npath: /Users/test-control/project\n---\n\n<!-- /home/test-control/project -->\n";
    expect(violations(sensitiveContent, input)).toEqual({
      fixed: input,
      found: []
    });
  });
});

describe("sensitive-content package loading", () => {
  test("resolves the package subpath to canonical native ESM", async () => {
    const resolved = createRequire(import.meta.url).resolve(
      "sinon/rules/no-sensitive-content"
    );
    expect(resolved).toBe(
      path.join(import.meta.dirname, "no-sensitive-content.mjs")
    );
    expect(await import(resolved)).toBe(
      await import("./no-sensitive-content.mjs")
    );
    expect(() =>
      createRequire(import.meta.url).resolve("sinon/rules/missing")
    ).toThrow();
  });

  test("CLI2 loads the subpath and fails on missing exports", async () => {
    const directory = await mkdtemp(
      path.join(tmpdir(), "sinon-sensitive-content-")
    );
    try {
      await mkdir(path.join(directory, "node_modules"));
      await symlink(
        path.resolve(import.meta.dirname, "../../../../.."),
        path.join(directory, "node_modules/sinon"),
        "dir"
      );
      const configPath = path.join(directory, ".markdownlint-cli2.jsonc");
      await writeFile(
        configPath,
        JSON.stringify({
          config: { default: false, "sensitive-content": true },
          customRules: ["sinon/rules/no-sensitive-content"]
        })
      );
      const inputPath = path.join(directory, "input.md");
      const input = "```text\n/Users/test-control/project\n```\n";
      await writeFile(inputPath, input);
      const markdownlintBin = path.join(
        path.dirname(
          createRequire(import.meta.url).resolve("markdownlint-cli2")
        ),
        "markdownlint-cli2-bin.mjs"
      );
      const result = spawnSync(
        process.execPath,
        [markdownlintBin, "input.md"],
        { cwd: directory, encoding: "utf-8", timeout: 15_000 }
      );
      expect(result.error).toBeUndefined();
      expect(result.status, result.stderr + result.stdout).toBe(1);
      expect(result.stderr).toContain("input.md:2 error sensitive-content");
      expect(result.stderr).not.toContain("/Users/test-control");
      expect(await readFile(inputPath, "utf-8")).toBe(input);
      await writeFile(
        inputPath,
        "Use /Users/Shared/project and docs/project.\n"
      );
      const valid = spawnSync(process.execPath, [markdownlintBin, "input.md"], {
        cwd: directory,
        encoding: "utf-8",
        timeout: 15_000
      });
      expect(valid.error).toBeUndefined();
      expect(valid.status, valid.stderr + valid.stdout).toBe(0);
      await writeFile(
        configPath,
        JSON.stringify({
          config: { default: false, "sensitive-content": true },
          customRules: ["sinon/rules/missing"]
        })
      );
      const missing = spawnSync(
        process.execPath,
        [markdownlintBin, "input.md"],
        { cwd: directory, encoding: "utf-8", timeout: 15_000 }
      );
      expect(missing.error).toBeUndefined();
      expect(missing.status).not.toBe(0);
      expect(missing.stderr).toContain(
        "Unable to import module 'sinon/rules/missing'"
      );
    } finally {
      await rm(directory, { force: true, recursive: true });
    }
  });
});

describe("docs/table-separators", () => {
  test("accepts exact separators and alignment markers", () => {
    expect(
      violations(
        tableSeparators,
        "| A | B | C | D |\n| --- | :--- | ---: | :---: |\n| 1 | 2 | 3 | 4 |\n"
      ).found
    ).toEqual([]);
  });

  test("reports short, long, and misaligned separator cells", () => {
    const { found, fixed } = violations(
      tableSeparators,
      "| A | B | C | D |\n| -- | ----- | :-: | ---: |\n| 1 | 2 | 3 | 4 |\n"
    );
    expect(found).toEqual([
      { detail: separatorDetail(1), lineNumber: 2, range: [3, 2] },
      { detail: separatorDetail(2), lineNumber: 2, range: [8, 5] },
      { detail: separatorDetail(3), lineNumber: 2, range: [17, 1] }
    ]);
    expect(fixed).toBe(
      "| A | B | C | D |\n| --- | --- | :---: | ---: |\n| 1 | 2 | 3 | 4 |\n"
    );
  });

  test("ignores data rows that only contain hyphens", () => {
    expect(
      violations(
        tableSeparators,
        "| A | B |\n| --- | --- |\n| - | - |\n| -- | ---- |\n"
      ).found
    ).toEqual([]);
  });

  test("ignores lines that look like tables inside fenced code", () => {
    expect(
      violations(tableSeparators, "```markdown\n| A |\n| - |\n```\n").found
    ).toEqual([]);
  });

  test("checks pipeless tables", () => {
    expect(
      violations(tableSeparators, "A | B\n-- | ---\n1 | 2\n").found
    ).toEqual([{ detail: separatorDetail(1), lineNumber: 2, range: [1, 2] }]);
  });

  test("checks tables nested in a blockquote", () => {
    expect(
      violations(tableSeparators, "> | A |\n> | - |\n> | 1 |\n").found
    ).toEqual([{ detail: separatorDetail(1), lineNumber: 2, range: [5, 1] }]);
  });
});

describe("authored Markdown diagnostics", () => {
  test.each([
    ["../other/%53KILL.md", 19],
    ["../other/SKILL%2Emd", 19],
    ["../other/SKILL%2emd", 19],
    ["../other/%53%4b%49%4c%4c%2e%6d%64", 33],
    ["../other/SKILL%2emd?view=1#intro", 32],
    ["<../other/SKILL%2emd>", 21],
    ["../other/SKILL.md?view=%0Afoo", 29],
    ["../other/SKILL.md?view=%0Dfoo", 29],
    ["../other/SKILL.md#%0Aintro", 26],
    ["../other/SKILL%2Emd?%0Aview=1", 29],
    ["h%74tps:/other/SKILL.md", 23],
    ["h%74tp:target/SKILL.md", 22]
  ] as const)(
    "reports encoded skill destination %s at its source range",
    (target, length) => {
      const input = `Use [skill](${target}).\n`;
      expect(violations(noRelativeSkillLinks, input)).toEqual({
        fixed: input,
        found: [{ detail: null, lineNumber: 1, range: [13, length] }]
      });
    }
  );

  test.each([
    ["../other/%53KILL.md", 19],
    ["../other/SKILL%2emd?view=%0Afoo", 31]
  ] as const)(
    "reports encoded reference destination %s at its source range",
    (target, length) => {
      const input = `Use [skill][s].\n\n[s]: ${target}\n`;
      expect(violations(noRelativeSkillLinks, input)).toEqual({
        fixed: input,
        found: [{ detail: null, lineNumber: 3, range: [6, length] }]
      });
    }
  );

  test.each([
    "Use [skill](https://example.org/SKILL%2emd).\n",
    "Use [skill](/other/SKILL%2emd).\n",
    "Use [skill](//example.org/SKILL%2emd).\n",
    "Use [guide](../guide%2emd).\n",
    "Use [guide](../other/page?next=../SKILL%2Emd).\n",
    "Use [guide](../other/page#../SKILL%2Emd).\n",
    "Use [guide](../other/page?next=../SKILL.md).\n",
    "Use [guide](../other/page#../SKILL.md).\n",
    "Use [skill](../other/SKILL%ZZ.md).\n",
    "Use [skill](../other/%2553KILL.md).\n",
    "Use [skill](../other%2fSKILL.md).\n",
    "Use [skill](../other/SKILL.md%3Fview=1).\n",
    "`[skill](../other/SKILL%2emd)`\n",
    '"[skill](../other/SKILL%2emd)"\n',
    "```md\n[skill](../other/SKILL%2emd)\n```\n"
  ])("preserves encoded non-target or protected syntax %s", (input) => {
    expect(violations(noRelativeSkillLinks, input)).toEqual({
      fixed: input,
      found: []
    });
  });

  test("keeps literal skill detection after a malformed prefix escape", () => {
    const input = "Use [skill](../%ZZ/SKILL.md).\n";
    expect(violations(noRelativeSkillLinks, input)).toEqual({
      fixed: input,
      found: [{ detail: null, lineNumber: 1, range: [13, 15] }]
    });
  });

  test.each([
    [noEmDash, "Keep prose \u2014 clear.\n", 1],
    [
      noEmDash,
      "`code \u2014 text` and [URL](https://example.org/a\u2014b).\n",
      0
    ],
    [noEmDash, '> Exact \u2014 quotation.\n\n"Literal \u2014 quote."\n', 0],
    [noEmDash, "---\ntitle: Literal \u2014 metadata\n---\n\nProse.\n", 0],
    [noEmDash, "```text\ncode \u2014 text\n```\n", 0],
    [noRelativeSkillLinks, "Use [skill](../other/SKILL.md).\n", 1],
    [noRelativeSkillLinks, "See [skill][s].\n\n[s]: ../other/SKILL.md\n", 1],
    [noRelativeSkillLinks, "Use [guide][g].\n\n[g]: ../guide.md\n", 0],
    [noRelativeSkillLinks, "Use [skill](https://example.org/SKILL.md).\n", 0],
    [
      noRelativeSkillLinks,
      '`[skill](../SKILL.md)`\n\n> [skill](../SKILL.md)\n\n"[skill](../SKILL.md)"\n',
      0
    ],
    [
      noRelativeSkillLinks,
      "---\ntitle: '[skill](../SKILL.md)'\n---\n\n```md\n[skill](../SKILL.md)\n```\n",
      0
    ]
  ] as const)("checks %s with protected syntax", (rule, input, count) => {
    const result = violations(rule, input);
    expect(result.found).toHaveLength(count);
    expect(result.fixed).toBe(input);
  });
});
