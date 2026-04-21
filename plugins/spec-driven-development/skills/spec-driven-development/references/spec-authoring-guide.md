---
title: SPEC Authoring Guide
description: >-
  Detailed instructions for writing SPEC.md, including what to write in each
  section, when to write it in the workflow, and how to keep it reviewable.
---

## Purpose

Open this guide only when `../SKILL.md` is already clear and you need section-by-section `SPEC.md` authoring depth.
Each section SHOULD be written at the correct workflow timing so requirements remain testable.

## SPEC Placement and Naming

### Canonical Placement

SPECs MUST be placed at `spec/domain/<ownership-path>/SPEC.md` where `<ownership-path>` reflects the owning capability boundary.

Parent capability SPECs at `spec/domain/SPEC.md` are valid when ownership of child capability differs. Nested hierarchies MUST remain valid under this rule.

### Ownership-Oriented Naming

The `<ownership-path>` component MUST reflect actual ownership boundaries.

Naming MUST be ownership-oriented and MUST NOT use the following patterns:

- topic names: generic descriptors that do not identify an owner
- policy names: rule collections without an owning implementation boundary
- umbrella names: groupings that aggregate multiple independent capabilities
- audit names: review activities rather than capability boundaries
- repository-improvement names: meta-work that belongs outside `spec/`

SPEC content MUST describe intended behavior, boundaries, and constraints. Requirements MUST NOT describe current implementation. Requirements MUST NOT be derived from inspecting existing code.
SPEC content SHOULD remain implementation-agnostic by default. Authors SHOULD avoid prescribing language, framework, library, or code-style policy unless the user explicitly requests those constraints or verified external constraints make them necessary.

## Table of Contents

- [Workflow Timing](#workflow-timing)
- [Frontmatter](#frontmatter)
- [Section-by-Section Authoring](#section-by-section-authoring)
- [Status Lifecycle Updates](#status-lifecycle-updates)
- [Quality Gates](#quality-gates)
- [Common Mistakes](#common-mistakes)

## Workflow Timing

1. During SPEC Setup, create the initial `SPEC.md` skeleton and fill frontmatter, Necessity, Role, and Overview.
2. Before Spec Review, complete Functional Requirements, Scenarios, Key Entities, and Constraints.
3. During Implementation, update `status` and `last_updated` as work progresses.
4. During Implementation Review, verify each requirement is implemented or justified and finalize lifecycle status.

## Frontmatter

Required fields:

- `title`: Concise capability name.
- `description`: Scope summary in 1-3 lines.
- `last_updated`: ISO 8601 calendar date (`YYYY-MM-DD`).
- `status`: One of `draft`, `review`, `approved`, `wip`, `implemented`, `deprecated`, `superseded`, `removed`.
- `call`: Outbound SPEC calls only. `[]` MUST be used when there are no outbound calls. Each entry MUST resolve to an existing `SPEC.md`. String entries MUST be relative paths.

Optional fields:

- `tag`
- `owner`
- `priority`
- `metadata`

Custom fields guidance:

- Extension keys MUST be placed under `metadata`.
- Arbitrary top-level frontmatter keys MUST NOT be introduced.
- Stable key names in `metadata` SHOULD be preferred (for example, `metadata.risk_level`).

Frontmatter SHOULD be written first in `draft` status.
`last_updated` SHOULD be refreshed for every meaningful content change.

## Section-by-Section Authoring

### Necessity

When to write: At initial draft.

How to write:

- The section SHOULD explain the problem and impact if unsolved.
- The section SHOULD define why this capability belongs in this SPEC.
- Scope SHOULD be bounded to one capability.

### Role

When to write: At initial draft, after Necessity.

How to write:

- The section SHOULD describe this component's responsibility in the system.
- The section SHOULD clarify boundaries with neighboring specs.
- The section SHOULD state what this SPEC does not own.
- When delegating to child capability where ownership differs, the section MUST explicitly state what is delegated.

### Overview

When to write: Early draft, before detailed requirements.

How to write:

- The section SHOULD summarize behavior and key concepts.
- The section SHOULD remain implementation-agnostic by default. Explicitly requested or materially required constraints MAY still be called out when they affect reviewer understanding.
- The section SHOULD serve as a quick-read summary for reviewers.

### Functional Requirements

When to write: After scope is stable, before linking and review.

How to write:

- Requirements SHOULD be split into requirement subsections.
- Verifiable language with objective acceptance criteria SHOULD be used.
- Requirements SHOULD define observable outcomes, not internal details.
- Each requirement SHOULD be exercised by at least one scenario.
- Requirements MUST be derived from intended behavior and constraints, not from inspecting current implementation.
- Requirement acceptance criteria MUST NOT describe how the code currently works.

### Scenarios

When to write: After initial requirements.

How to write:

- The section SHOULD include `Normal Flow`, `Alternative Flow`, and `Error Flow`.
- Each flow SHOULD map to one or more requirements.
- Requirement references SHOULD be explicit when reviewers might otherwise guess.
- Failure triggers and expected system behavior SHOULD be included.

### Key Entities

When to write: When requirements introduce domain objects.

How to write:

- Entities and their purpose SHOULD be listed.
- Critical fields and invariants SHOULD be included.
- Constraints affecting validation or lifecycle behavior SHOULD be included.

### Constraints

When to write: Before review, after scenarios are complete.

How to write:

- Domain, security, performance, compliance, interoperability, and operational constraints SHOULD be recorded when they materially affect intended behavior.
- Constraints SHOULD describe externally meaningful limits, guarantees, and prohibitions.
- Constraints SHOULD avoid unnecessary language, framework, library, or code-style policy. Such constraints MAY be included when they are explicitly requested or materially required.
- Hard constraints SHOULD be stated as normative statements.
- Open-ended wording SHOULD NOT be used when behavior MUST be deterministic.

## Status Lifecycle Updates

1. Status MUST be set to `draft` when first authored.
2. Status MUST be set to `review` when ready for formal review.
3. Status MUST be set to `approved` when review passes and implementation can start.
4. Status MUST be set to `wip` at implementation start.
5. Status MUST be set to `implemented` when implementation and verification are complete.
6. Status SHOULD be set to `deprecated` when still active but retirement is planned.
7. Status SHOULD be set to `superseded` when replaced by another active SPEC.
8. Status MUST be set to `removed` when retired and inactive.

## Quality Gates

Before marking `review` or higher:

- In authored `SPEC.md` content, TODO markers MUST NOT remain.
- In authored `SPEC.md` content, unresolved placeholders MUST NOT remain.
- In authored `SPEC.md` content, template scaffolding instruction lines from `assets/templates/SPEC.md` MUST be replaced (SPEC scaffolding fingerprint checks MUST pass).
- Manual numbered headings (`## 1. Something` form) MUST NOT be used. The validator enforces this rule on SPEC.md, RESEARCH.md, and CONTRACT.md.
- `"${SKILL_ROOT}/scripts/verify-spec.sh" ./spec` MUST be run, and failures MUST be resolved.
- If the consuming repository already uses markdownlint, maintainers MAY run `npx -y markdownlint-cli2 <touched-markdown-files>`.

## Common Mistakes

- Adding code-location annotations (file paths, line numbers, function names) to spec content. Specs describe intended behavior, not current source layout.
- Writing ambiguous requirements that cannot be tested.
- Duplicating ownership already defined by another SPEC.
- Linking to non-SPEC files in frontmatter `call`.
- Forgetting to update frontmatter `call` after dependency changes.
- Advancing status without updating `last_updated`.
- Using manual numbered headings (`## 1. Something` form); the validator rejects these in SPEC.md, RESEARCH.md, and CONTRACT.md.
