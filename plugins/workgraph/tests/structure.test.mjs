import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import test from "node:test";

import {
  parseFrontmatter,
  pluginRoot,
  readJson,
  readText
} from "./helpers.mjs";

const expectedSkills = [
  "artifact-authoring",
  "instruction-authoring",
  "minimal-implementation",
  "orchestration",
  "session-core"
];

const expectedReferences = {
  "instruction-authoring": ["agent-skills-format.md"],
  orchestration: [
    "completion.md",
    "delegation-selection.md",
    "payload-contracts.md",
    "recovery.md"
  ]
};

const readReferenceListing = async (skillName) => {
  const referencesDir = path.resolve(
    pluginRoot,
    "skills",
    skillName,
    "references"
  );
  try {
    const entries = await readdir(referencesDir, { withFileTypes: true });
    return entries
      .filter((entry) => entry.isFile())
      .map((entry) => entry.name)
      .toSorted();
  } catch (error) {
    if (error.code !== "ENOENT") {
      throw error;
    }
    return [];
  }
};

test("plugin manifest fields are valid, marketplace version matches format, and workgraph has one marketplace entry", async () => {
  const manifest = await readJson(".claude-plugin/plugin.json");
  const marketplace = JSON.parse(
    await readFile(
      path.resolve(pluginRoot, "../../.claude-plugin/marketplace.json"),
      "utf-8"
    )
  );
  const workgraphEntries = marketplace.plugins.filter(
    ({ name }) => name === "workgraph"
  );
  assert.equal(manifest.name, "workgraph");
  assert.equal(typeof manifest.description, "string");
  assert.match(marketplace.version, /^\d{4}\.\d{2}\.\d{2}\.\d{2}$/u);
  assert.equal(workgraphEntries.length, 1);
  assert.equal(workgraphEntries[0].source, "./plugins/workgraph");
});

test("worker result and Giver source provenance are structured", async () => {
  const payloadContracts = await readText(
    "skills/orchestration/references/payload-contracts.md"
  );
  const notices = await readText("THIRD_PARTY_NOTICES.md");
  const results = [
    ...payloadContracts.matchAll(/```json\n(?<json>\{[\s\S]*?\})\n```/gu)
  ]
    .map((match) => JSON.parse(match.groups.json))
    .filter((result) => Object.hasOwn(result, "Status"));
  assert.equal(results.length, 2);
  const [workerResult, verifierResult] = results;
  assert.deepEqual(workerResult, {
    Blockers: [],
    Breaking: [],
    Decisions: [],
    EvidenceRefs: [],
    Files: [],
    Signatures: [],
    Status: "COMPLETED | BLOCKED | FAILED | UNKNOWN",
    Summary: ""
  });
  assert.deepEqual(verifierResult, {
    Blockers: [],
    Breaking: [],
    Decisions: [],
    EvidenceRefs: [],
    Files: [],
    FindingsOrDispositions: [],
    Signatures: [],
    Status: "COMPLETED | BLOCKED | FAILED | UNKNOWN",
    Summary: ""
  });
  const giverVersionMatch = notices.match(
    /^- Source version: `(?<version>[^`]+)`$/mu
  );
  assert.ok(giverVersionMatch, "Giver source version is required");
  assert.match(giverVersionMatch.groups.version, /^v\d+(?:\.\d+)*$/u);
  assert.match(notices, /^- Project: `sng2c\/giver-architecture`$/mu);
  assert.match(notices, /^- Reference file: `giver-principles\.md`$/mu);
  assert.match(
    notices,
    /^- Source: `https:\/\/github\.com\/sng2c\/giver-architecture`$/mu
  );
});

test("root distribution files are present and non-empty", async () => {
  const files = [
    "README.md",
    "LICENSE",
    "THIRD_PARTY_NOTICES.md",
    ".gitignore"
  ];
  const contents = await Promise.all(files.map((file) => readText(file)));
  for (const [index, content] of contents.entries()) {
    assert.ok(content.trim().length > 0, `${files[index]} must not be empty`);
  }
});

test("skill catalog is exact and Agent Skills frontmatter is valid", async () => {
  const entries = await readdir(path.resolve(pluginRoot, "skills"), {
    withFileTypes: true
  });
  const skills = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .toSorted();
  assert.deepEqual(skills, expectedSkills);
  const markdowns = await Promise.all(
    skills.map((skillName) => readText(`skills/${skillName}/SKILL.md`))
  );
  for (const [index, markdown] of markdowns.entries()) {
    const skillName = skills[index];
    const { fields, body } = parseFrontmatter(markdown);
    assert.equal(fields.name, skillName);
    assert.doesNotMatch(fields.name, /^workgraph-/u);
    assert.match(fields.name, /^(?!-)(?!.*--)[a-z0-9-]{1,64}(?<!-)$/u);
    assert.equal(typeof fields.description, "string");
    assert.ok(markdown.split(/\r?\n/u).length < 500);
    assert.ok(body.trim().length > 0);
    assert.doesNotMatch(body, /(?:\.\.\/|\/SKILL\.md|skills\/)/u);
  }
});

test("references are one level deep, exact, and self-contained", async () => {
  const listings = await Promise.all(
    expectedSkills.map((skillName) => readReferenceListing(skillName))
  );
  const referencePairs = [];
  for (const [index, actual] of listings.entries()) {
    const skillName = expectedSkills[index];
    assert.deepEqual(actual, expectedReferences[skillName] ?? []);
    for (const referenceName of actual) {
      referencePairs.push([skillName, referenceName]);
    }
  }
  const referenceMarkdowns = await Promise.all(
    referencePairs.map(([skillName, referenceName]) =>
      readText(`skills/${skillName}/references/${referenceName}`)
    )
  );
  for (const markdown of referenceMarkdowns) {
    assert.ok(markdown.trim().length > 0);
    assert.doesNotMatch(markdown, /\[[^\]]+\]\([^)]*\.md(?:#[^)]*)?\)/iu);
    assert.doesNotMatch(
      markdown,
      /(?:\.\.\/|references\/|\/SKILL\.md|skills\/)/u
    );
  }
});

test("skill file links stay within the owning references directory", async () => {
  const markdowns = await Promise.all(
    expectedSkills.map((skillName) => readText(`skills/${skillName}/SKILL.md`))
  );
  for (const markdown of markdowns) {
    const links = [
      ...markdown.matchAll(/\[[^\]]+\]\((?<target>[^)]+)\)/gu)
    ].map((match) => match.groups.target);
    for (const link of links) {
      assert.match(link, /^references\/[a-z0-9-]+\.md$/u);
    }
  }
});
