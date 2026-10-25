import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test, vi } from "vitest";

import { main } from "../skills/spec-driven-development/scripts/sdd/cli.js";
import { generateMermaid } from "../skills/spec-driven-development/scripts/sdd/graph.js";
import { repositoryPaths } from "./test-support/paths.js";

test("generateMermaid preserves linked fixture output", () => {
  expect(generateMermaid(repositoryPaths.sddFixtureRoot)).toBe(
    [
      "flowchart TD",
      '  domain_ingest_SPEC_md["Domain Ingest"]',
      '  domain_SPEC_md["Domain Capability"]',
      "  domain_SPEC_md --> domain_ingest_SPEC_md"
    ].join("\n")
  );
});

test("diagram generation keeps colliding path IDs distinct", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-graph-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const specRoot = path.join(directory, "spec");
  const callerPath = path.join(specRoot, "domain", "foo-bar", "SPEC.md");
  const calleePath = path.join(specRoot, "domain", "foo_bar", "SPEC.md");
  mkdirSync(path.dirname(callerPath), { recursive: true });
  mkdirSync(path.dirname(calleePath), { recursive: true });
  writeFileSync(
    callerPath,
    '---\ntitle: Hyphen Caller\ndescription: Example.\nlast_updated: "2026-10-23"\nstatus: implemented\ncall:\n  - ../foo_bar/SPEC.md\n---\n'
  );
  writeFileSync(
    calleePath,
    '---\ntitle: Underscore Callee\ndescription: Example.\nlast_updated: "2026-10-23"\nstatus: implemented\ncall: []\n---\n'
  );
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  onTestFinished(() => {
    vi.restoreAllMocks();
  });

  expect(main(["validate", directory])).toBe(0);
  expect(errors).not.toHaveBeenCalled();
  output.mockClear();
  expect(main(["generate-diagram", specRoot])).toBe(0);

  const diagram = String(output.mock.calls[0]?.[0] ?? "");
  const callerId = diagram.match(
    /^\s+(?<id>[A-Za-z0-9_]+)\["Hyphen Caller"\]$/mu
  )?.groups?.id;
  const calleeId = diagram.match(
    /^\s+(?<id>[A-Za-z0-9_]+)\["Underscore Callee"\]$/mu
  )?.groups?.id;
  expect(callerId).toBeDefined();
  expect(calleeId).toBeDefined();
  expect(callerId).not.toBe(calleeId);
  expect(diagram.split("\n").filter((line) => line.includes("-->"))).toEqual([
    `  ${callerId} --> ${calleeId}`
  ]);
  expect(errors).not.toHaveBeenCalled();
});

test.each([
  [[], 1],
  [["--help"], 0],
  [["validate", "--unknown"], 1],
  [["unknown-command"], 1]
] as const)("main reports the command outcome for %j", (args, expected) => {
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  onTestFinished(() => {
    vi.restoreAllMocks();
  });
  expect(main(args)).toBe(expected);
  expect(output).not.toHaveBeenCalled();
  expect(errors).toHaveBeenCalled();
});

test("list-tags preserves lexical ordering and counts", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-tags-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  writeFileSync(
    path.join(directory, "SPEC.md"),
    '---\ntag: [z, Z, alpha, "2", "10", z]\n---\n'
  );
  const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
  onTestFinished(() => output.mockRestore());
  expect(main(["list-tags", directory, "--count"])).toBe(0);
  expect(output.mock.calls).toEqual([
    ["10\t1"],
    ["2\t1"],
    ["Z\t1"],
    ["alpha\t1"],
    ["z\t2"]
  ]);
});

test.each([
  ["mixed document kinds", [], ["domain", "ingest", "research"]],
  ["SPEC documents only", ["--kind", "spec"], ["domain", "ingest"]]
] as const)(
  "list-tags lists %s without errors",
  (_description, options, expected) => {
    const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });

    expect(
      main(["list-tags", repositoryPaths.sddFixtureRoot, ...options])
    ).toBe(0);
    expect(output.mock.calls.map(([line]) => line)).toEqual(expected);
    expect(errors).not.toHaveBeenCalled();
  }
);

test.each([
  ["mixed document kinds", []],
  ["SPEC documents only", ["--kind", "spec"]]
] as const)(
  "list-frontmatter finds the known inbound SPEC caller for %s without errors",
  (_description, options) => {
    const output = vi.spyOn(console, "log").mockImplementation(vi.fn());
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });

    expect(
      main([
        "list-frontmatter",
        repositoryPaths.sddFixtureRoot,
        "--inbound-of",
        "spec/domain/ingest/SPEC.md",
        ...options
      ])
    ).toBe(0);
    expect(output.mock.calls).toEqual([
      [
        [
          "target\tsource\traw_link",
          `spec/domain/ingest/SPEC.md\t${path.join(repositoryPaths.sddFixtureRoot, "domain", "SPEC.md")}\t./ingest/SPEC.md`
        ].join("\n")
      ]
    ]);
    expect(errors).not.toHaveBeenCalled();
  }
);

test.each([
  ["empty", "---\n---\n\n# Changelog\n", "Empty YAML frontmatter"],
  [
    "unterminated",
    "---\ntitle: Missing closing delimiter\n",
    "Unterminated YAML frontmatter"
  ]
] as const)(
  "list-tags still rejects %s frontmatter",
  (_kind, content, diagnostic) => {
    const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-empty-yaml-"));
    onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
    const changelogPath = path.join(directory, "CHANGELOG.md");
    writeFileSync(changelogPath, content);
    const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
    onTestFinished(() => {
      vi.restoreAllMocks();
    });

    expect(main(["list-tags", directory])).toBe(1);
    expect(errors.mock.calls.flat().join("\n")).toContain(
      `${changelogPath}]: ${diagnostic}`
    );
  }
);

test("validate reports an invalid status and exits nonzero", () => {
  const directory = mkdtempSync(path.join(tmpdir(), "sinon-sdd-status-cli-"));
  onTestFinished(() => rmSync(directory, { force: true, recursive: true }));
  const specDirectory = path.join(directory, "spec", "domain");
  mkdirSync(specDirectory, { recursive: true });
  const file = path.join(specDirectory, "SPEC.md");
  writeFileSync(
    file,
    '---\ntitle: Status Example\ndescription: Example.\nlast_updated: "2026-10-23"\nstatus: unknown\ncall: []\n---\n'
  );
  const errors = vi.spyOn(console, "error").mockImplementation(vi.fn());
  onTestFinished(() => {
    vi.restoreAllMocks();
  });
  expect(main(["validate", directory])).toBe(1);
  expect(errors.mock.calls.flat().join("\n")).toContain(file);
  expect(errors.mock.calls.flat().join("\n")).toContain("status");
});
