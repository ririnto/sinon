---
title: Engineering Rules
metadata:
  reference:
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    OpenAI project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
    OpenAI instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    pstack project safety:
      version: 0.15.25
      url:
        - https://raw.githubusercontent.com/cursor/plugins/main/pstack/skills/correct/SKILL.md
        - https://raw.githubusercontent.com/cursor/plugins/main/pstack/skills/principle-make-operations-idempotent/SKILL.md
        - https://raw.githubusercontent.com/cursor/plugins/main/pstack/skills/principle-separate-before-serializing-shared-state/SKILL.md
    WCAG:
      version: "2.2"
      url:
        - https://www.w3.org/TR/WCAG22/
        - https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html
---

This document owns project-wide mandatory engineering rules for projects using Harness.
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
Honor the target's lockfile, parent POM, BOM, and version catalog.
Use native dependency catalogs when the selected package manager or build tool supports them.
Reference catalog entries from consumers instead of repeating their versions.
Preserve behavior outside the requested change.
Remove replaced code, fallbacks, and obsolete paths in the same change.
Add compatibility paths only for a required external contract.

Keep the smallest complete change that satisfies the requirements.
For a requested mechanical change, verify coverage across its stated scope without adding unrelated cleanup.

## Dependency Style

Use pnpm for Node-side dependency management and keep shared dependency versions in pnpm catalogs.
Use `^x.y.z` ranges for catalog package releases and `catalog:` references in consuming manifests.
Keep the pnpm package-manager coordinate as a version without an appended integrity hash.
Do not pin dependency coordinates with a version-plus-hash or commit hash in pnpm, Gradle, or Maven.
Use `gradle/libs.versions.toml` as the Gradle version source and Maven dependency management or a BOM for Maven.
Retain generated lockfile integrity fields and artifact-verification checksums because verification data is not a dependency coordinate.
Apply the supported LTS policy above before selecting or upgrading dependencies.
Range migration, hash removal, and LTS upgrades require compatibility checks because changing resolution can change executable code.
These choices remain policy text rather than automatic migrations or heuristic version diagnostics.

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

## Rule Rollout

Prefer built-in rules and supported configuration before custom checks.
Use AST-aware custom rules only in proven syntax contexts, with positive and negative fixtures covering violations and valid cases.
Offer automatic fixes only when evaluation order, side effects, and observable behavior remain unchanged.
Verify fix convergence with the target formatter.
Stage new rules through read-only reports on representative source before enabling mandatory enforcement.
Keep items with side-effect risk as policy text and record the risk instead of adding diagnostics or fixes.
Never weaken configuration to make a rollout pass.

## Evidence Classes

Distinguish source inspection, automated checks, live integration behavior, and independent review in the handoff.
Claim runtime behavior only for paths exercised by runtime checks.

## Code Rules Common To All Languages

Use meaningful named regex groups and name-based access whenever extracting captures, including the full match.
Do not use ordinal access such as `match[0]`, `match[1]`, capture destructuring, or numbered subgroup calls.
Use named replacement references instead of positional capture references.
Keep ordinary array indexing separate from regex capture access.
Prefer an existing parser or literal operation when it expresses the extraction more clearly.
For engines without named groups, use a clearer native alternative or report the unsupported requirement.
Do not invent named-group support, add artificial wrappers, or add packages solely for this convention.
Preserve optional groups, selected matches, flags, Unicode, offsets, iteration, replacements, negative cases, evaluation order, and cleanup.
Exclude third-party, vendored, and generated code from this convention's migration scope.

Document every externally exposed declaration in English using the language's multiline documentation syntax.
Keep documentation at declaration level and state the contract or reason instead of restating the identifier.
Keep function bodies free of blank lines.
Separate sibling functions, tests, and lifecycle registrations with one blank line.
The test-registration spacing diagnostic recognizes only imported framework bindings in proven registration scopes.
Unresolved globals, unknown registration factories, intervening comments, and ambiguous callbacks remain review-only because treating ordinary execution as registration can misclassify code.
Ban comments except declaration documentation and required tool directives, licenses, and suppressions.
Do not add a suppression to make a rule pass.
Never delete comments automatically because directives and legal text can affect behavior or obligations.
The AST-aware checks diagnose only their proven placement contexts and preserve required directives and licenses.
Move explanations that must survive to the relevant declaration documentation.

Subject to configured lint, inline variables used only once and keep single-use logic inline.
Do not extract or retain helpers for hypothetical future reuse or test convenience.
Do not count test references as production reuse.
Preserve behavior, evaluation count and order, side effects, resource lifetime, and required production interfaces.
Preserve exception timing, mutable snapshots, capture, and overload or receiver resolution.
Retain bindings or helpers only for required production interfaces, preserved semantics, or configured lint.
State the concrete reason for each retained binding or helper.
Do not suppress, weaken, or reconfigure lint to satisfy these preferences.

Change production code only for a real product requirement or an actual defect.
Never change production code to accommodate tests, expose internals, add test hooks, or manufacture test seams.
Adapt tests to production interfaces and behavior.

Prefer `await` over `void` for asynchronous work when the caller should wait for completion.
The TypeScript profile uses the built-in type-aware unhandled-promise diagnostic with `ignoreVoid: false` through Ultracite.
A promise handled by `catch` or an equivalent rejection handler remains valid without adding `await`.
No rule requires replacing every `void` with `await`, and no automatic rewrite performs that conversion.
Awaiting can change timing, callback signatures, cleanup, cancellation, background execution, and error propagation, so that conversion remains policy text and requires semantic review.

Replace an intermediate guard return with an inverted condition that encloses the trailing work when the two forms behave identically.
Prefer condition inversion over mid-function `return`, `continue`, and `break`, and use a plain `if`/`else if`/`else` chain for repeated guards.
Active lint rules and formatter output take precedence over this preference.
Early-exit preferences have no lint enforcement or automatic fixes because syntactic inversion can change cleanup, cancellation, exception, or return behavior.
Keep a guard return when inversion would change cleanup, exception timing, loop or caller control flow, or a returned value, or when the guard expresses validation failure or an early exit that inversion would obscure.

Compare expected strings with full equality.
Parse structured output (JSON, HTML, URLs, headers, event streams), and compare exact fields or elements instead of substring, prefix, or suffix matching.
Use membership assertions only when membership itself is the observable contract.

## Documentation Formatting

Use a body H1 for ordinary Markdown titles.
For documents that rely on frontmatter, use `title:` there and omit the duplicate body H1.
Do not add frontmatter only to replace a body H1.

Put each complete Markdown sentence on its own source line.
Do not join separate sentences with semicolons or substitute punctuation.
Keep conditions with the actions they qualify.
Preserve code, metadata, URLs, and exact quotations.
Keep each table cell to one sentence and use separate rows for independent requirements.
Avoid em dashes in authored prose.
Use skill names instead of relative links to another `SKILL.md` file.
The Micromark checks report supported prose and destination patterns without rewriting text, resolving files, or invoking skills.
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
- The [Kotlin rules](languages/kotlin.md) include `harness:kotest-test-assertions`.
- [TypeScript](languages/typescript.md)
- [JavaScript and JSX](languages/javascript.md)
- [Python](languages/python.md)
- [Go](languages/go.md)
- [Rust](languages/rust.md)
- [Shell](languages/shell.md)

## Tool Documents

Use tool documents for profile detection, native configuration, commands, CI catalogs, and integration limits:

- [Project baseline](tools/baseline.md)
- [Frontend](tools/frontend.md)
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

## State And Retry Safety

Design retryable project commands to converge after repeated execution or partial failure.
Preserve intentional repeated effects and require an explicit recovery contract where convergence is impossible.
Prefer separate mutable state for concurrent operations that do not require a shared object.
Serialize required shared writes with the target's supported concurrency primitives.
These choices require semantic review because retries and serialization can change effects, ordering, cancellation, and durability.

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
