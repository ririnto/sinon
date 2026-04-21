---
title: Review Checklist
---

Review output MUST record the review type, the reviewed artifact scope, and each applicable item as `pass`, `fail`, or `n/a` with rationale.

## Spec Review

### Frontmatter

- [ ] title field exists and is non-empty
- [ ] description field exists (multiline allowed via `>-`)
- [ ] last_updated field exists (ISO 8601 calendar date, `YYYY-MM-DD`)
- [ ] status field is one of: draft, review, approved, wip, implemented, deprecated, superseded, removed
- [ ] call field exists (empty list allowed)
- [ ] Custom frontmatter fields, if any, are nested under `metadata`

### Content

- [ ] Necessity section explains the need; it states why this spec is needed
- [ ] Role section describes the system role; it defines component responsibility
- [ ] Overview section summarizes scope; it highlights key concepts
- [ ] Functional Requirements are verifiable; acceptance is testable
- [ ] Every requirement is covered by one or more scenarios
- [ ] Scenarios include Normal, Alternative, and Error flows; each flow is clear
- [ ] SPEC content stays implementation-agnostic by default and does not introduce unnecessary language, framework, library, or code-style constraints unless they are explicitly requested or materially required
- [ ] In authored `SPEC.md`, template scaffolding instruction lines are replaced (SPEC scaffolding fingerprint checks pass)

### Canonical Placement

- [ ] SPEC.md is placed at `spec/domain/<ownership-path>/SPEC.md` where `<ownership-path>` reflects the owning capability boundary
- [ ] RESEARCH.md exists only under `spec/research/{framework|library|topic}/{name}/` when external behavior affects the SPEC

### Ownership and Authoring Flow

- [ ] Ownership boundary aligns to a capability boundary, not to documentation categories or project phases
- [ ] SPEC naming avoids topic, policy, umbrella, audit, and repository-improvement paths unless they map to a real owning capability
- [ ] Requirements are authored by flow `spec -> code`
- [ ] No requirements are reverse-derived from current implementation

### Research

- [ ] RESEARCH.md exists when external behavior or version-sensitive behavior affects the SPEC
- [ ] If RESEARCH.md exists, frontmatter captures the investigated subject and version
- [ ] If RESEARCH.md exists, it documents framework/library/topic behavior and is not project comparison, repository audits, implementation planning, migration sequencing, or task management
- [ ] If RESEARCH.md exists, findings are current enough for this review
- [ ] In authored `RESEARCH.md`, scaffold `TODO:` markers are resolved (no unresolved `TODO:` markers remain in non-fenced content)

### Scope and Ownership

- [ ] SPEC scope is focused and owns a single capability
- [ ] No duplicated requirements from other specs
- [ ] When overlap exists, the spec links to the owning SPEC instead of repeating content

### Linking

- [ ] Frontmatter `call` field exists (`call: []` allowed)
- [ ] Frontmatter `call` is SPEC-to-SPEC only (each entry resolves to an existing `SPEC.md`)
- [ ] String `call` entries use relative paths
- [ ] No reverse-direction points (backlinks / "called by" lists) are maintained in spec bodies
- [ ] Deprecated link-maintenance sections are absent (for example, `Link Maintenance`)
- [ ] Inbound references are queryable from frontmatter call (`./scripts/list-frontmatter.sh ./spec --inbound-of spec/domain/ingest/SPEC.md`)
- [ ] No broken links

### Contract

- [ ] When OpenAPI-based HTTP endpoint contracts are used: openapi.yaml exists in the same directory as SPEC.md
- [ ] When OpenAPI-based HTTP endpoint contracts are used: operations with `requestBody` define request schemas with required fields and types
- [ ] When OpenAPI-based HTTP endpoint contracts are used: each status code either defines response content schema(s) or is an intentional no-body response (`1xx`, `204`, `205`, `304`, or `HEAD`)
- [ ] When OpenAPI-based HTTP endpoint contracts are used: OpenAPI covers scenarios from SPEC.md
- [ ] When CONTRACT.md is used: it exists in the same directory as SPEC.md
- [ ] When CONTRACT.md is used: frontmatter parses as YAML and includes title, description, and last_updated
- [ ] When CONTRACT.md is used: last_updated is an ISO 8601 calendar date (`YYYY-MM-DD`)
- [ ] When CONTRACT.md is used: Contract Units are used (function/file/interface/class as applicable)
- [ ] When CONTRACT.md is used: multiple units are used in one document when ownership and lifecycle are shared
- [ ] When CONTRACT.md is used: scenario mapping is included for each documented unit
- [ ] When CONTRACT.md is used: examples are mapped by unit and scenario
- [ ] When CONTRACT.md is used: at least one success-case example input/output data is included
- [ ] When CONTRACT.md is used: validation rules are documented for inputs, outputs, and invariants
- [ ] When CONTRACT.md is used: error mapping is included for expected failure outcomes
- [ ] When CONTRACT.md is used: relevant edge cases are covered
- [ ] When CONTRACT.md is used: semantic constraints for invariants, idempotency, concurrency, and async behavior are validated when applicable
- [ ] When CONTRACT.md is used: it is consistent with SPEC.md Functional Requirements and Scenarios
- [ ] When CONTRACT.md is used: no unresolved `TODO:` markers are present in non-fenced content
- [ ] When CONTRACT.md is used: no unresolved template placeholders (for example, `{{token}}`) are present in non-fenced content
- [ ] When CONTRACT.md is used: no inline `markdownlint-*` directives are present

### Formatting

- [ ] If the consuming repository already uses markdownlint, maintainers MAY run `npx -y markdownlint-cli2 <touched-markdown-files>`
- [ ] No unnecessary bold emphasis
- [ ] Headings used for emphasis instead
- [ ] Quote rules followed; use `'` for single and `"` for double quotes
- [ ] Table separators use the `| --- | --- | --- |` style with exactly 3 hyphens per cell
- [ ] A single space appears on both sides of cell content between pipe delimiters (for example, `| value |`)
- [ ] When adjacent rows share the same first-column value, subsequent rows leave the first column empty (multi-row format)
- [ ] When multiple items exist in one logical cell, each item is listed on a separate row with a `-` prefix
- [ ] Table content is not omitted or summarized away

## Implementation Review

### Requirements Coverage

- [ ] Every Functional Requirement has corresponding implementation
- [ ] Unrealized requirements have documented justification in SPEC.md
- [ ] All Scenarios are testable

### Spec Sync

- [ ] SPEC.md status updated appropriately
- [ ] SPEC.md last_updated date refreshed
- [ ] If RESEARCH.md exists, it remains synchronized with the implemented state
- [ ] `spec/CHANGELOG.md` entries keep latest date first
- [ ] `spec/CHANGELOG.md` includes only behavior changes, configuration additions or changes, and contract changes
- [ ] `spec/CHANGELOG.md` excludes research, initialization, planning, and future-reservation content
- [ ] Frontmatter `call` updated if dependencies changed
- [ ] `"${SKILL_ROOT}/scripts/verify-spec.sh" ./spec` re-run after the final spec sync
- [ ] If the consuming repository already uses markdownlint, maintainers MAY re-run `npx -y markdownlint-cli2 <touched-markdown-files>` after the final Markdown sync
