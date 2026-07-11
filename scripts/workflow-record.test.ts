// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import {
  referenceForHost,
  selectReviewHost,
  selectReviewRecord
} from "./workflow-record.js";

test("dual host records require explicit user or policy authority", () => {
  const blocked = selectReviewRecord({
    dualAuthority: "none",
    evidence: { remoteCandidates: ["github", "gitlab"] },
    mode: "dual"
  });
  expect(blocked.value).toBeUndefined();
  const dual = selectReviewRecord({
    dualAuthority: "repository-policy",
    evidence: { remoteCandidates: ["github", "gitlab"] },
    mode: "dual"
  });
  expect(dual.value).toEqual({ hosts: ["github", "gitlab"], mode: "dual" });
  const primary = selectReviewRecord({
    dualAuthority: "none",
    evidence: { explicit: "github", remoteCandidates: ["github", "gitlab"] },
    mode: "primary"
  });
  expect(primary.value).toEqual({ host: "github", mode: "primary" });
});

test("ambiguous GitHub and GitLab remotes block host routing", () => {
  const result = selectReviewHost({
    remoteCandidates: ["github", "gitlab"]
  });
  expect(result.value).toBeUndefined();
  expect(result.blockers[0]).toContain("focused host choice");
});

test("explicit host choice wins and loads one reference", () => {
  const result = selectReviewHost({
    explicit: "gitlab",
    remoteCandidates: ["github", "gitlab"]
  });
  expect(result).toEqual({ blockers: [], value: "gitlab" });
  expect(referenceForHost(result.value ?? "local")).toBe(
    "references/gitlab.md"
  );
  expect(referenceForHost("local")).toBeUndefined();
});
