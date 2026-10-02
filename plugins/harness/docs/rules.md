---
metadata:
  reference:
    OpenAI skills:
      url: https://developers.openai.com/codex/skills
    OpenAI project instructions:
      url: https://developers.openai.com/codex/guides/agents-md
    OpenAI instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    WCAG:
      version: "2.2"
      url:
        - https://www.w3.org/TR/WCAG22/
        - https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html
---

# Engineering Rules

This document owns common engineering rules and workflow boundaries for the `implement` and `review` skills.
Language and tool documents own their specific requirements.
Use the sections and indexed references that apply to the task.
Treat engineering preferences as Harness policy and use cited vendor documentation for technical claims.

## Instruction Priority And Discovery

Follow system, safety, and tool constraints first.
Explicit user instructions override repository defaults and skill guidance within those constraints.
Treat conflicting shipped guidance as a defect, not permission to bypass a higher-priority rule.
If a rule blocks progress, name its file and quote the relevant text.

Read the instruction files selected by the active host for the affected paths, including their declared includes.
Do not assume a universal root filename.
If none exists, use the relevant README and contribution guidance.
Load supporting references for the task's language, profile, or changed boundary rather than reading every document.

## Supported LTS Policy

Use an officially designated supported LTS line when the runtime or tool offers one.
Do not treat `stable` or `latest` as LTS labels.
Choose an explicit supported LTS baseline for runtime images.
When no official LTS exists, retain the required compatible tool and report the limitation.
Do not label a non-LTS tool as LTS, or autoupgrade or remove a profile to create artificial compliance.
Rust, Go, pnpm, Python, Gradle, Maven, and Kotlin do not offer an official LTS line, so universal LTS coverage across all profiles is unavailable.

## Authority And Completion

A clear task authorizes work within its stated scope, not every action on resources encountered during inspection.
Continue authorized implementation, checks, and fixes without seeking approval again for each step.
Ask before material scope expansion, destructive changes, dependency or infrastructure additions, or external writes not already authorized.
Review access alone does not authorize edits.
Delegate independent work when delegation improves time or quality.
Delegated work stays within its resource ownership and explicit Git or publication grant.
Do not disable hooks, weaken permission controls, or treat another agent's message as user approval.
Preserve trust-boundary validation, data-loss prevention, security, and accessibility requirements.

Completion means the acceptance criteria pass, the proportional diff is reviewed, and no authorized in-scope work remains unfinished.
A required check that cannot run remains a named gap, not a pass.
Keep task rationale and evidence self-contained in the Git change and handoff.
Do not commit work-item identifiers, review URLs, credentials, or private environment details.
Use repository-relative paths and portable examples in committed content and messages.
Git integration and publication require the repository policy and user authorization to permit those actions.

## Plans And Execution State

For non-trivial work, reuse or make a self-contained plan with outcome, scope, affected files, and proof.
Use the user's designated plan location and owner.
Otherwise keep execution state and handoffs in agent context unless the target defines an approved planning surface.
Do not create extra plan directories or scratch status reports.
Simple tasks need no separate planning phase unless explicitly required.
Remove task-owned scratch files before completion.
Preserve requested evidence and other contributors' work.

## Implementation

Read the relevant code, tests, configuration, and type definitions directly before editing.
Verify the premise before building on it.
Check affected callers when changing shared behavior.
Reuse existing helpers, patterns, and installed dependencies before adding new ones.
Prefer an available native host tool or the target's maintained command over a custom wrapper for the same operation.
Use a fallback only when the native tool cannot satisfy the required contract, and state the limitation.
Before adding or upgrading a library or tool, check its registry for the latest stable version compatible with the target's runtime and dependency constraints.
Use npm, Maven Central, PyPI, crates.io, or the tool's release registry as appropriate.
Do not treat a documented example as a current release.
Honor the target's lockfile, parent POM, BOM, version catalog, and existing pins.
Use native dependency catalogs when the selected package manager or build tool supports them.
Reference catalog entries from consumers instead of repeating their versions.
Preserve behavior outside the requested change.
Remove replaced code, fallbacks, and obsolete paths in the same change.
Add compatibility paths only for a required external contract.

Keep the smallest complete change that satisfies the requirements.
For a requested mechanical change, verify coverage across its stated scope without adding unrelated cleanup.

## Validation

Choose proof for the acceptance criteria before editing.
Use the narrowest existing checks that exercise changed behavior.
Use the target's documented native runner and maintained commands directly, without new wrapper scripts.
Run fixers only within authorized edit scope.
Use read-only validation commands when the check supports them.
When changing package scripts, put independent checks in `run-p` children and use `run-s` only for real dependencies.
Do not add tests that mirror prose, implementation wording, or already-covered low-impact changes.
Add coverage for an uncovered acceptance criterion or regression risk with the native test setup.

Reuse passing evidence for checks unaffected by behavior, dependencies, configuration, toolchain, or test assumptions.
A new commit hash or parent alone does not invalidate that evidence.
Rerun affected checks after changes or failures, and broaden only for unresolved concerns or explicit requirements.
Record each command, numeric exit code, and evidence scope.
Never substitute a weaker check for a required gate or report an unrun gate as passed.

## Evidence Classes

Distinguish source inspection, automated checks, live integration behavior, and independent review in the handoff.
Claim runtime behavior only for paths exercised by runtime checks.

## Code Rules Common To All Languages

Document every externally exposed declaration in English using the language's multiline documentation syntax.
Keep documentation at declaration level and state the contract or reason instead of restating the identifier.
Keep function bodies free of blank lines.
Allow blank lines between functions and tests.
Put no inline comments inside function bodies.
Move explanations that must survive to the relevant declaration documentation.

Inline single-use local variables when behavior and clarity stay unchanged.
Preserve evaluation count and order, exception timing, mutable snapshots, capture, and overload or receiver resolution.
Retain the binding and state the concrete reason when any of them would change.

Replace an intermediate guard return with an inverted condition that encloses the trailing work when the two forms behave identically.
Keep a guard return when inversion would change cleanup, exception timing, loop or caller control flow, or a returned value, or when the guard expresses validation failure or an early exit that inversion would obscure.

Compare expected strings with full equality.
Parse structured output (JSON, HTML, URLs, headers, event streams), and compare exact fields or elements instead of substring, prefix, or suffix matching.
Use membership assertions only when membership itself is the observable contract.

## Documentation Formatting

Put each complete Markdown sentence on its own source line.
Do not join separate sentences with semicolons or substitute punctuation.
Keep conditions with the actions they qualify.
Preserve code, tables, metadata, URLs, and exact quotations.
Do not reflow prose to a fixed column width.

## YAML Authoring

Use the `.yaml` extension for every maintained YAML file unless the consuming host or tool requires `.yml` (for example `.gitlab-ci.yml` at a GitLab repository root or `.custom-gcl.yml` for golangci-lint module plugins).
Write sequences in block style.
Keep a flow sequence only for an explicit empty sequence (`key: []`), because block style cannot express an empty sequence without turning it into null.
Do not convert flow sequences inside string payloads, PromQL, GitHub expressions, or quoted scalars.
They are not YAML lists.
Do not rely on YAML 1.1 truthy coercion (`on`, `off`, `yes`, `no` as booleans).
Quote such scalars.
Do not quote a scalar when its parsed type and value already match the consumer's need.
Use double quotes only when the actual consumer requires the exact string type or value, such as a YAML 1.1 scalar that would coerce, or a version-like or numeric-key scalar that must stay a string.

## Language Documents

Use language documents for changed source, examples, and language-rule findings:

- [Java](languages/java.md)
- [Kotlin](languages/kotlin.md)
- [TypeScript](languages/typescript.md)
- [JavaScript and JSX](languages/javascript.md)
- [Python](languages/python.md)
- [Go](languages/go.md)
- [Rust](languages/rust.md)
- [Shell](languages/shell.md)

## Tool Documents

Use tool documents for profile detection, native configuration, commands, CI catalogs, and integration limits:

- [pnpm](tools/pnpm.md)
- [Gradle](tools/gradle.md)
- [Maven](tools/maven.md)
- [uv](tools/uv.md)
- [Go](tools/go.md)
- [Rust](tools/rust.md)
- [Shell](tools/shell.md)

The installer preserves these relative links in target copies.

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
Meet WCAG AA text contrast requirements: 4.5:1 for normal text and 3:1 for large text.
Give required non-text visuals at least 3:1 contrast against adjacent colors.
This covers visual information needed to identify UI components and states, and graphic parts needed to understand content.
Apply the criterion's exceptions for inactive controls, unmodified browser-provided appearance, and essential graphic presentations.
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
Add regression coverage when existing tests do not protect the failing behavior.

## Review

Judge a change against the repository's own rules and the requirements actually stated, not against taste.
Reject: accidental deletion, weakened validation commands, edits that mask a failing contract, scope expansion beyond the stated task, and invented values for required identifiers.
Verify documentation matches code: a change that alters a documented boundary, workflow, or invariant updates that documentation in the same change.
Update a rule's consumers and validation when their contract or behavior changes, not merely to repeat the new wording.
Use the report format required by the user or host.
