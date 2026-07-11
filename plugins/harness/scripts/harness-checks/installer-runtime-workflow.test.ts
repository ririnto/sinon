// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import { readFile } from "node:fs/promises";
import path from "node:path";

import { renderCandidateContent } from "../../skills/harness-install/scripts/install-harness/content.js";
import { requiredSrc } from "../../skills/harness-install/scripts/install-harness/paths.js";
import { buildPlan } from "../../skills/harness-install/scripts/install-harness/planning.js";
import type {
  CiHost,
  InstallCandidate,
  InstallerConfig
} from "../../skills/harness-install/scripts/install-harness/types.js";

const ciHosts = ["github", "gitlab", "both", "none"] as const;
const workflowDestinations = [
  "WORKFLOW.md",
  "WORKFLOW.github.md",
  "WORKFLOW.gitlab.md",
  "WORKFLOW.none.md"
] as const;
const workflowDestinationSet = new Set<string>(workflowDestinations);

const configFor = (ciHost: CiHost): InstallerConfig => ({
  action: "install",
  activateHooks: false,
  ciHost,
  force: false,
  mode: "bun",
  selectedPath: null,
  targetRoot: path.resolve(import.meta.dirname, "..", "..", "..")
});

const workflowCandidates = (
  candidates: readonly InstallCandidate[]
): readonly InstallCandidate[] =>
  candidates.filter((candidate) => workflowDestinationSet.has(candidate.dst));

for (const ciHost of ciHosts) {
  test(`workflow assets stay independent when ci host is ${ciHost}`, async () => {
    const candidates = workflowCandidates(await buildPlan(configFor(ciHost)));

    expect(candidates.map((candidate) => candidate.dst).toSorted()).toEqual(
      workflowDestinations.toSorted()
    );
    expect(new Set(candidates.map((candidate) => candidate.dst)).size).toBe(
      workflowDestinations.length
    );

    const renderedContents = await Promise.all(
      candidates.map(async (candidate) => ({
        expected: await readFile(requiredSrc(candidate), "utf-8"),
        rendered: await renderCandidateContent(candidate)
      }))
    );
    for (const renderedContent of renderedContents) {
      expect(renderedContent.rendered).toBe(renderedContent.expected);
    }
  });
}
