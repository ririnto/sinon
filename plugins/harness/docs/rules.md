# Engineering Rules

This document is the single canonical source of implementation, design, and review rules for the `implement` and `review` skills.
Skills own their procedures and reference this document for the rules themselves.
A rule stated here is not restated in a skill.
Change both consumers together when ownership of a rule moves.

## Instruction Priority And Discovery

The user's instructions take precedence over skill guidance, repository documents, and general defaults.
If guidance conflicts with the user's intent, prioritize the user, then the active host's policy, then repository instructions, then skills.

Read the target repository's root instruction file before editing.
One repository may carry several.
Follow the one the active host selects, and treat repository-level pointers (`@AGENTS.md` includes) as part of the file.
If no root instruction file exists, fall back to README and contribution documents.

## Plans And Execution State

Write a self-contained plan with the outcome, scope, affected files, and proof before implementation.
Keep execution handoffs and progress in agent context unless the target repository defines an approved planning surface.
Do not create execution-plan records, plan directories, or scratch status reports inside the target repository.

## Default Git Workflow

Run substantive repository changes through this default sequence: task, self-contained plan, implementation, appropriate native proof, proportional diff review, and Git integration.
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
Prefer an explicit fix over a read-only check when the defect is already confirmed, and prefer a read-only check when the change must not alter the tree.
Run existing checks directly instead of wrapping them in new scripts.
Split independent checks into parallel `run-p` children, and use `run-s` only for a real remaining dependency between steps.
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

## Code Rules Common To All Languages

Document every externally exposed declaration in English using the language's multiline documentation syntax.
Keep documentation at declaration level and state the contract or reason instead of restating the identifier.
Keep function bodies free of blank lines.
Allow blank lines between functions and tests.
Put no inline comments inside function bodies.
Move explanations that must survive to the relevant declaration documentation.

Inline a single-use local only when evaluation count, evaluation order, exception timing, mutable snapshots, closure capture, and overload or receiver resolution stay identical.
Retain the binding and state the concrete reason when any of them would change.

Replace an intermediate guard return with an inverted condition that encloses the trailing work when the two forms behave identically.
Keep a guard return when inversion would change cleanup, exception timing, loop or caller control flow, or a returned value, or when the guard expresses validation failure or an early exit that inversion would obscure.

Compare expected strings with full equality.
Parse structured output (JSON, HTML, URLs, headers, event streams), and compare exact fields or elements instead of substring, prefix, or suffix matching.
Use membership assertions only when membership itself is the observable contract.

## YAML Authoring

Use the `.yaml` extension for every maintained YAML file unless the consuming host or tool requires `.yml` (for example `.gitlab-ci.yml` at a GitLab repository root or `.custom-gcl.yml` for golangci-lint module plugins).
Write sequences in block style.
Keep a flow sequence only for an explicit empty sequence (`key: []`), because block style cannot express an empty sequence without turning it into null.
Do not convert flow sequences inside string payloads, PromQL, GitHub expressions, or quoted scalars; they are not YAML lists.
Do not rely on YAML 1.1 truthy coercion (`on`, `off`, `yes`, `no` as booleans); quote such scalars.
Do not quote a scalar when its parsed type and value already match the consumer's need.
Use double quotes only when the actual consumer requires the exact string type or value, such as a YAML 1.1 scalar that would coerce, or a version-like or numeric-key scalar that must stay a string.

## Language Documents

The package-local language documents own visibility, documentation syntax and tags, binding syntax, control-flow syntax, lambdas, extensions, recursion limits, and raw strings.
Read the document matching the language of the file under change:

- `languages/java.md`
- `languages/kotlin.md`
- `languages/typescript.md`
- `languages/javascript.md`
- `languages/python.md`
- `languages/go.md`
- `languages/rust.md`
- `languages/shell.md`

The package-local tool documents own detection, native configuration, merge rules, commands, CI catalogs, and limitations.
Read the tool document matching each selected profile:

- `tools/bun.md`
- `tools/gradle.md`
- `tools/maven.md`
- `tools/uv.md`
- `tools/go.md`
- `tools/rust.md`
- `tools/shell.md`

The installer preserves these references in the installed copy.
The Kotlin tool integration also copies its complete native module when the Kotlin profile is selected.
Its real `RuleSetProviderV3` service descriptor must be available on the ktlint runtime classpath.

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
