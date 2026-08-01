# Quality Checklist

Use this checklist before finalizing a generated `SPEC.md`.
Treat the checklist as a requirements-quality gate, not as an implementation test plan.
Fix failures directly when possible.
If revision requires human judgment, record a focused open question instead of guessing.

## Self-Sufficiency

- [ ] The spec does not contain "see `X.md`", "see `ARCHITECTURE.md`", "see `README.md`", "refer to docs/...", or any in-repo cross-doc routing.
- [ ] The only external references are formal standards (RFC numbers, external API specifications) or data sources that cannot be inlined; these are clearly named as external.
- [ ] An unfamiliar engineer or coding agent can read this file and understand what must be implemented without consulting other in-repo documents.
- [ ] Entity field tables include type, constraints, nullability, and usage; the reader does not need to consult the codebase.
- [ ] Configuration field tables include defaults, constraints, reload semantics, and environment variable indirection.
- [ ] Reference algorithms (when present) are detailed enough to implement from; they are not pointers to external docs.
- [ ] State transition rules name every state and every trigger; the reader can construct a state diagram from the text alone.
- [ ] Integration protocol details are sufficient for a developer to implement without external documentation (or external docs are referenced by name and chapter).
- [ ] No placeholders like "see [other component spec]" or "[to be detailed in integration guide]" inside core sections.

## Normative Discipline

- [ ] The Normative Language section is present and defines RFC 2119 keywords (MUST, MUST NOT, SHOULD, SHOULD NOT, MAY, REQUIRED, RECOMMENDED, OPTIONAL).
- [ ] The Normative Language section defines `Implementation-defined` and states that implementations MUST document the selected behavior.
- [ ] RFC 2119 keywords are used deliberately, not randomly.
- [ ] SHOULD versus MUST distinction is intentional; no SHOULD where MUST is intended, no MUST where the project will diverge.
- [ ] `Implementation-defined` is used consistently for contract surface the spec deliberately does not prescribe.
- [ ] Vague words ("fast", "robust", "intuitive", "secure", "scalable") either do not appear, or they appear only when quantified or measured (e.g., "fast (<2s)" or "secure (no data leakage)").

## Content Quality

- [ ] The spec has a clear title, status (draft / review / approved), creation date, updated date, and owner or responsible role.
- [ ] The summary section (or introduction) explains what is being specified and why it matters in one to three sentences.
- [ ] The Problem statement is present and precedes solution details and requirements.
- [ ] Goals section is present and describes outcomes, not implementation tasks.
- [ ] Non-Goals section is present, concrete, and useful for preventing scope creep.
- [ ] Scope section has explicit In-Scope and Out-of-Scope subsections.
- [ ] Users and Stakeholders section identifies primary actors and secondary stakeholders.
- [ ] When non-obvious context matters, the Validated Assumptions section is present.
      Every assumption comes from user-provided context, authorized source material, or explicit approval.
- [ ] Optional sections (Data Model, UI Behavior, Decision Log, Rollout, Risks, etc.) appear only when relevant and are not marked as `N/A` or `TBD`.
- [ ] No `N/A`, `TBD`, `[PLACEHOLDER]`, `[TO BE DETERMINED]`, or unresolved bracketed text remains in final output.

## Requirement Quality

- [ ] Every functional requirement has a stable ID such as `FR-001`, `FR-002`, etc.
- [ ] Every non-functional requirement has a category ID such as `NFR-PERF-001`, `NFR-SEC-001`, `NFR-REL-001`, etc.
- [ ] Requirements use RFC 2119 language deliberately (MUST, MUST NOT, SHOULD, SHOULD NOT, MAY, REQUIRED, RECOMMENDED, OPTIONAL).
- [ ] Each requirement describes one observable behavior or a single real constraint; no "and"-compound requirements.
- [ ] Requirements describe WHAT / WHY, not HOW, unless design is explicitly in scope.
- [ ] Requirements avoid implementation details (library names, algorithms, tool choices) unless the implementation is a mandated constraint.
- [ ] Requirements avoid vague words unless quantified or measured (e.g., "fast (<2s)", "secure (no cross-tenant exposure)").
- [ ] Requirements name limits, thresholds, units, states, and transitions when they affect behavior.
- [ ] No requirement silently embeds an unresolved assumption; assumptions are captured in Validated Assumptions or Open Questions.
- [ ] Requirements are testable or have a stated reason they cannot be scenario-tested.
- [ ] Functional requirements are grouped logically (by actor, workflow, or feature area).
- [ ] Non-functional requirements are grouped by category (Performance, Security, Reliability, Observability, Usability, Compatibility).

## Scenario and Acceptance Quality

- [ ] Every functional requirement has at least one Given/When/Then scenario or a stated reason it cannot be scenario-tested.
- [ ] Scenarios use the Given/When/Then format consistently.
- [ ] Negative case scenarios are included (invalid input, missing data, unauthorized access, rate limits, timeouts) when relevant.
- [ ] Boundary case scenarios are included (minimum, maximum, empty, zero, one, many, limits, timeouts) when relevant.
- [ ] Recovery and retry scenarios are included for transient failures when the system has retry logic.
- [ ] State-machine transition scenarios are present for every named state and trigger (when state machine exists).
- [ ] Acceptance criteria are testable and observable; they do not say "works correctly", "looks good", "handles errors" without specifics.
- [ ] Acceptance criteria are tied to requirement IDs when traceability is needed.
- [ ] Each acceptance criterion is independently testable without hidden context.
- [ ] Scenarios do not depend on context outside the spec.

## Non-Functional Quality

- [ ] When performance is relevant, performance requirements are present and include metric + measurement point + verification method.
- [ ] When security or privacy is relevant, security and privacy requirements are present and are NOT optional (they are MUST requirements).
- [ ] Reliability and availability requirements are present when the system has availability SLOs or recovery requirements.
- [ ] Observability requirements (logging, status, monitoring) are present when operational visibility is expected.
- [ ] Usability and accessibility requirements are present when user-facing and are specific (not "intuitive").
- [ ] Compatibility and version requirements are present when relevant.
- [ ] Included NFRs have metrics, thresholds, or verification methods; vague targets like "good performance" are not acceptable.
- [ ] Policy constraints (MUST / MUST NOT) are separated from quality thresholds (SHOULD / goals).
- [ ] Security, privacy, safety, or compliance blockers are treated as MUST requirements, not optional goals.

## System-Spec Quality (when applicable)

- [ ] System Overview includes Main Components, Abstraction Levels, and External Dependencies.
- [ ] Core Domain Model includes Entity definitions with field names, types, constraints, and stable identifier rules.
- [ ] Configuration Specification includes Resolution Pipeline, Dynamic Reload Semantics, Preflight Validation, and a Cheat Sheet summary.
- [ ] State Machine (when present) includes Named States, Lifecycle Phases, Transition Triggers and Rules, and Idempotency rules.
- [ ] Scheduling and Reconciliation (when present) includes Poll Loop, Candidate Selection, Concurrency Control, Retry/Backoff, and Reconciliation rules.
- [ ] Resource Management includes Workspace/Resource Layout, Lifecycle, Hooks, and Safety Invariants.
- [ ] Integration Protocol (when present) includes Launch Contract, Session Startup, Streaming, Emitted Events, Policy, Timeouts, and Error Mapping.
- [ ] External System Integration includes REQUIRED Operations, Query Semantics, Normalization, Error Handling, and Write Boundaries.
- [ ] Failure Model and Recovery is present and maps failure classes to recovery behavior.
- [ ] Security and Operational Safety includes Trust Boundary, Filesystem Safety, Secret Handling, Hook Safety, and Hardening.
- [ ] Every named state in the state machine appears in at least one transition rule.
- [ ] Every transition trigger is enumerated; no implicit transitions.
- [ ] Reference algorithms (when present) are language-agnostic pseudocode in fenced ` ```text ` blocks; they are detailed enough to implement.
- [ ] Conformance Matrix (when present) includes Core Conformance, Extension Conformance (if extensions exist), and Real Integration Profile.
- [ ] Conformance tests are bulleted per area; tests are specific and testable (not "works").
- [ ] Implementation Checklist (Definition of Done) is present and separates REQUIRED items from RECOMMENDED extensions.

## Success and Traceability

- [ ] Success criteria are measurable or have an explicit verification method; they are not vague aspirations.
- [ ] Success criteria include user, business, operational, or compliance outcomes.
- [ ] Every acceptance criterion traces to a functional requirement (AC-001-A ties to FR-001, etc.).
- [ ] Every functional requirement traces to at least one scenario or a stated reason it cannot be scenario-tested.
- [ ] Every non-functional requirement traces to a success criterion or a stated verification method.
- [ ] Source or evidence is captured for requirements when stakeholder input, external constraints, or existing behavior matters.
- [ ] Verification method is explicit for each requirement or acceptance criterion when testability is not obvious.
- [ ] Open questions are answerable and have an owner or decision point when needed.
- [ ] Open questions are specific, for example `[NEEDS CLARIFICATION: Should retry attempts cap at 3 or be unlimited?]`.

## AI-Agent Readiness

- [ ] The spec is self-contained enough for a future agent to use without guessing.
- [ ] The spec clearly separates WHAT/WHY from HOW unless design is explicitly in scope.
- [ ] The spec includes enough boundaries (limits, states, transitions) to prevent feature inflation during implementation.
- [ ] The spec does not instruct an agent to mine repository-local docs (`ARCHITECTURE.md`, `README.md`, `WORKFLOW.md`, etc.) unless the user explicitly authorized that task.
- [ ] The spec remains one standalone artifact; when it grows beyond ~10,000 words, large conditional details move to appendices inside the same `SPEC.md` and the main sections link to those internal headings.
- [ ] Ambiguities are resolved; no "implementation may choose" text left without explicit `Implementation-defined` marking.
- [ ] Scenarios are detailed enough for an agent to write test code from them.
- [ ] Reference algorithms are specific enough for an agent to implement from them.

## Failure Response

### When a checklist item fails

1. Attempt to fix the issue directly (rewrite the section, add missing scenario, clarify vague term).
2. If the fix requires human judgment, stop and record the issue as a focused open question instead of guessing.
3. Do not proceed to deliver the spec until all checklist failures are either fixed or explicitly captured as open questions.

### Example: missing scenario

- Failure: FR-001 has no scenarios and no reason stated for why it cannot be scenario-tested.
- Response: Either (a) add at least one scenario for FR-001, or (b) add a note: "FR-001 is a performance tuning requirement with no observable behavior change; verified by profiling, not scenario testing."

### Example: blocked decision

- Failure: The spec names "approval policy" as implementation-defined but does not state what approvals are required and when.
- Response: Record an open question: "What is the approval policy for this implementation? Should file operations require approval, or auto-approve within the workspace?"
