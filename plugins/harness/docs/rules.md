# Engineering Rules

This document is the single canonical source of implementation, design, and review rules for the `implement` and `review` skills.
Skills own their procedures and reference this document for the rules themselves.
A rule stated here is not restated in a skill.
Change both consumers together when ownership of a rule moves.

## Instruction Priority And Discovery

The user's instructions take precedence over skill guidance, repository documents, and general defaults.
If guidance conflicts with the user's intent, prioritize the user, then the active host's policy, then repository instructions, then skills.

Read the target repository's root instruction file before editing.
Do not assume a universal filename: discover the file the active host actually loads (`AGENTS.md`, `CLAUDE.md`, or another host-specific file).
One repository may carry several.
Follow the one the active host selects, and treat repository-level pointers (`@AGENTS.md` includes) as part of the file.
If no root instruction file exists, fall back to README and contribution documents.

## Plans And Execution State

Plans belong to GitHub or GitLab issues, not to files on disk.
Record the outcome, scope, files, and proof of a planned change in an issue before implementation.
Keep execution handoffs and progress in agent context.
Do not create execution-plan records, plan directories, or scratch status reports inside the target repository.

## Default Git Workflow

Run repository changes through this default sequence: issue creation, planning, implementation, pull-request creation, review, and merge.
A simple task may omit or combine an intermediate phase when the result does not need it.
Explicitly required validation, review, approval, and safety conditions remain binding in every case.
Keep the full sequence for substantive changes.

Temporary working material stays in git-untracked locations outside tracked documents.
Remove scratch files before completion.

## Implementation

Read the relevant code, tests, configuration, and type definitions directly before editing.
Verify the premise before building on it.
Fix the root cause: before changing a shared function, check every caller and fix the shared path rather than patching one caller.
Reuse existing helpers, patterns, and installed dependencies before adding new ones.
Preserve behavior outside the requested change.
Remove replaced code, fallbacks, and obsolete paths in the same change.
No legacy compatibility surfaces.

Keep the smallest complete change that satisfies the requirements.
Set invariants for what must not vary and leave implementation detail to the implementer's judgment.
A mechanical change applied across a repository is one reviewable change with a residual check: verify zero remaining violations before completion.

## Validation

Choose the proof before editing and run the narrowest existing checks that exercise the changed behavior.
Do not write tests for reversible, low-impact changes that mirror the implementation.
A new test protects one acceptance criterion or regression risk and does not duplicate existing coverage of the same behavior.
Use the repository's native test runner and maintained toolchain commands.
The target repository's own instruction files own the canonical check commands.

Do not repeat a validation that already passed on the same tree, commands, and toolchain when only a commit or hash changed.
Check whether the new head changes the scope of existing evidence, and rerun only the affected checks.
Broaden testing only for new changes, failures, or unresolved concerns.
Record the command and its numeric exit code.
A gate that cannot run stays an explicit named gap in the report.
It is never treated as success and never substituted with a weaker check.

## Evidence Classes

Separate what a claim is proven by: source inspection, automated checks, live integration behavior, and independent review are different evidence classes.
A unit test proves the unit.
Only a live check proves the runtime path.
Every verdict names the evidence class that supports it.

## Language And Binding Rules

Prefer immutable bindings (`val`, `const`, `readonly`) for values that do not need reassignment.
A binding prevents reassignment only.
It does not make the object or collection immutable, so keep the two concepts distinct.
When converting mutable state to immutable bindings, preserve evaluation order, evaluation count, exception timing, mutable snapshots, and closure capture.
Apply conversion at the smallest scope where the reason is visible.
Use no mechanical global conversion and no unconditional deep-freeze abstraction.

Pass an existing function reference instead of a wrapper unless adaptation is needed.
Where the language provides it, prefer tail recursion over a loop when the recursive call is in real tail position and semantics and readability hold.

Use raw multi-line strings for regular expressions and JSON fixtures where the language supports them.
Raw strings remove quote and backslash escaping while preserving interpolation.
Know whether the trailing newline before the closing delimiter is part of the value, and match the exact target.
Compare expected strings with full equality.
Parse structured output (JSON, HTML, URLs, headers, event streams), and compare exact fields or elements instead of substring, prefix, or suffix matching.
Use membership assertions only when membership itself is the observable contract.

## Domain Boundaries And Design Direction

Separate surface-shape layers from business-logic layers: presentational components versus containers, HTTP handlers versus service code, command parsers versus domain operations.
Cross-domain access goes through documented APIs, providers, or events.
Shared utilities stay free of business policy.
Parse external data at the boundary instead of validating it in every consumer.
Enforce invariants where data enters.
Reject silent error swallowing: every caught failure translates into a typed result, a surfaced message, or a recorded gap.

Prefer legibility over cleverness.
Prefer reversible operations, especially for shared state.
Default to explicit feedback over silent operations.
Error messages name the constraint and the next safe action.
Use plain language and one term for one concept.

## Frontend And Accessible Interfaces

These rules apply to every exposed surface a user or another system drives: web UI, CLI, public API, and SDK.
Apply each rule to the surfaces it fits.
Visual and focus rules do not apply to API and SDK surfaces.
Mark stable interactive and test-indexable elements so headless drivers locate state without fragile selectors.
Keep every interactive element keyboard-reachable, focus order matching visual flow, and focus styles visible.
Interactive non-button elements carry appropriate roles.
Meaningful images have alt text.
Color contrast meets WCAG AA (4.5:1 text, 3:1 graphics).
Every error response carries an actionable message and a stable error code.
CLI commands support `--help` and a non-interactive flag.
Route user-visible strings through the i18n layer when the surface supports multiple languages.
Do not introduce ad-hoc parallel primitives: a new color, error envelope, or flag pattern joins the canonical set before use.

## Test Quality

Select the lightest layer that can verify the behavior.
Pure logic gets unit tests.
Integration tests prove boundaries.
End-to-end tests stay limited to system-critical journeys that lighter layers cannot prove.
Tests assert observable behavior, not implementation wording or file layout.
A failing behavior gets exactly one protecting test.
Do not add tests that repeat the same story as existing coverage.

## Review

Judge a change against the repository's own rules and the requirements actually stated, not against taste.
Reject: accidental deletion, weakened validation commands, edits that mask a failing contract, scope expansion beyond the stated task, and invented values for required identifiers.
Verify documentation matches code: a change that alters a documented boundary, workflow, or invariant updates that documentation in the same change.
Whoever changes a rule updates the rule, its consumers, and its validation surface together.
Output fields in a report are recommended choices.
The dispatch, host, or user requirement wins over any fixed schema.
