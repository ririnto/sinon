// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import path from "node:path";

import { WORKFLOW_POLICY } from "./workflow-policy-contract.js";
import { parseWorkflowPolicyDocument } from "./workflow-policy-document.js";

const workflowPath = path.join(
  path.resolve(import.meta.dirname, ".."),
  "plugins/harness/skills/harness-install/assets/common/WORKFLOW.md"
);

const policyDocument = (): string =>
  `\`\`\`workflow-policy
${JSON.stringify({
  completion: {
    blockedReport: "blocked-report",
    requires: [
      "fan-in",
      "review-clearance",
      "focused-validation",
      "integrated-validation",
      "required-evidence",
      "selected-record-updated",
      "root-publication-authority"
    ]
  },
  documentEdits: {
    manualFallback: "recorded-manual-fallback",
    requires: ["stop-slop", "semantic-line-breaks"]
  },
  failure: {
    discardRetryRequires: [
      "discarded",
      "changed-remediation",
      "fresh-replacement-identity"
    ],
    failedCycles: "prior-retryable-failures",
    phases: ["exploration", "implementation", "review", "validation"],
    retryBudget: "two-failed-cycles"
  },
  records: { addenda: "install-all", selection: "per-work-item" },
  review: {
    lowDispositions: ["accepted", "deferred"],
    sourceFixRequires: "fresh-rereview"
  },
  root: {
    backgroundCliFallback: {
      admission: "native-delegation-unavailable",
      authority: "root-only",
      leafLimit: "one-bounded-leaf",
      leafProhibitions: [
        "no-delegation",
        "no-integration",
        "no-commit",
        "no-publication"
      ],
      record: "recorded",
      unavailableHandoff: "eligible-root-or-human"
    },
    opusSol: {
      delegates: ["implementation", "validation", "review"],
      directExploration: "bounded-read-only",
      requiredFor: [
        "unresolved-ambiguity",
        "security-sensitive-decision",
        "contradictory-fan-in",
        "high-risk-publication"
      ]
    },
    terra: {
      delegates: ["exploration", "implementation", "validation", "review"],
      requires: ["bounded-work", "executable-plan"]
    },
    unavailable: {
      handoffOwner: "eligible-root-or-human",
      requiredCapability: "opus-sol-medium"
    }
  },
  schemaVersion: 1,
  validation: {
    executor: "validation-executor",
    phases: ["focused", "integrated"]
  }
})}
\`\`\``;

test("parses the sole canonical workflow policy document structurally", async () => {
  // Given
  const document = await Bun.file(workflowPath).text();

  // When
  const result = parseWorkflowPolicyDocument(document);

  // Then
  expect(result.blockers).toEqual([]);
  expect(result.value?.validation.executor).toBe("validation-executor");
  expect(result.value?.completion.requires).toContain("review-clearance");
  expect(result.value).toBe(WORKFLOW_POLICY);
});

test("parses the canonical workflow policy document with CRLF line endings", async () => {
  // Given
  const canonicalDocument = await Bun.file(workflowPath).text();
  const crlfDocument = canonicalDocument.replaceAll("\n", "\r\n");

  // When
  const result = parseWorkflowPolicyDocument(crlfDocument);

  // Then
  expect(result).toEqual({ blockers: [], value: WORKFLOW_POLICY });
});

test("policy applies failed-leaf recovery to every delegable phase", async () => {
  // Given
  const document = await Bun.file(workflowPath).text();

  // When
  const result = parseWorkflowPolicyDocument(document);

  // Then
  expect(result.value?.failure).toEqual({
    discardRetryRequires: [
      "discarded",
      "changed-remediation",
      "fresh-replacement-identity"
    ],
    failedCycles: "prior-retryable-failures",
    phases: ["exploration", "implementation", "review", "validation"],
    retryBudget: "two-failed-cycles"
  });
});

test("requires exact background CLI fallback vocabulary", () => {
  // Given
  const document = policyDocument();

  // When
  const result = parseWorkflowPolicyDocument(document);

  // Then
  expect(result.blockers).toEqual([]);
  expect(result.value?.root).toMatchObject({
    backgroundCliFallback: {
      admission: "native-delegation-unavailable",
      authority: "root-only",
      leafLimit: "one-bounded-leaf",
      leafProhibitions: [
        "no-delegation",
        "no-integration",
        "no-commit",
        "no-publication"
      ],
      record: "recorded",
      unavailableHandoff: "eligible-root-or-human"
    }
  });
});

test("rejects malformed policy JSON and duplicate policy blocks", () => {
  // Given
  const malformed = "```workflow-policy\n{\n```";
  const duplicate =
    "```workflow-policy\n{}\n```\n\n```workflow-policy\n{}\n```";

  // When
  const malformedResult = parseWorkflowPolicyDocument(malformed);
  const duplicateResult = parseWorkflowPolicyDocument(duplicate);

  // Then
  expect(malformedResult.blockers).toContain("workflow-policy-json-invalid");
  expect(duplicateResult.blockers).toContain(
    "workflow-policy-block-count-invalid"
  );
});

test("rejects policy objects with unknown or missing keys", () => {
  // Given
  const unknown =
    '```workflow-policy\n{"schemaVersion":1,"unexpected":true}\n```';
  const missing = '```workflow-policy\n{"schemaVersion":1}\n```';

  // When
  const unknownResult = parseWorkflowPolicyDocument(unknown);
  const missingResult = parseWorkflowPolicyDocument(missing);

  // Then
  expect(unknownResult.blockers).toContain("workflow-policy-keys-invalid");
  expect(missingResult.blockers).toContain("workflow-policy-keys-invalid");
});

test("rejects invalid identifiers and duplicate validation phases", () => {
  // Given
  const invalidIdentifier = policyDocument()
    .replace("validation-executor", "invalid executor")
    .replace('["focused","integrated"]', '["focused","focused"]');

  // When
  const result = parseWorkflowPolicyDocument(invalidIdentifier);

  // Then
  expect(result.blockers).toContain("workflow-policy-identifier-invalid");
  expect(result.blockers).toContain(
    "workflow-policy-validation-phases-invalid"
  );
});

test("rejects unsupported policy versions and vocabulary values", () => {
  // Given
  const unsupportedVersion = policyDocument().replace(
    '"schemaVersion":1',
    '"schemaVersion":2'
  );
  const unsupportedVocabulary = policyDocument().replace(
    '"validation-executor"',
    '"alternate-executor"'
  );

  // When
  const versionResult = parseWorkflowPolicyDocument(unsupportedVersion);
  const vocabularyResult = parseWorkflowPolicyDocument(unsupportedVocabulary);

  // Then
  expect(versionResult.blockers).toContain(
    "workflow-policy-version-unsupported"
  );
  expect(vocabularyResult.blockers).toContain(
    "workflow-policy-vocabulary-unsupported"
  );
});

test("rejects values outside the runtime root capability contract", () => {
  // Given
  const unsupportedDelegate = policyDocument().replace(
    '"exploration","implementation","validation","review"',
    '"exploration","implementation","validation","publication"'
  );

  // When
  const result = parseWorkflowPolicyDocument(unsupportedDelegate);

  // Then
  expect(result.blockers).toContain("workflow-policy-vocabulary-unsupported");
});
