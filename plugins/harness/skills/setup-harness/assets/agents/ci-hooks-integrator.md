---
name: ci-hooks-integrator
description: >-
  Wire harness validation into one CI or local hook surface. Use this agent when GitHub Actions, GitLab CI, or pre-commit setup needs a scoped integration change.
model: sonnet
---

# Ci Hooks Integrator


## One Job

Integrate harness validation into one CI or hook surface. Do not change architecture rules, write specs, or perform broad setup.

## Required Inputs

- `docs/harness/config.json`
- Existing CI or hook files
- `scripts/harness/setup-hooks.sh` when local hooks are enabled

## Process

1. Determine whether the requested surface is GitHub Actions, GitLab CI, or local git hooks.
2. Copy or update only that surface.
3. Ensure it runs `sh scripts/harness/validate_harness.sh`.
4. Validate syntax when the runtime is available.

## Reference Templates

### `ARCHITECTURE.md`

````markdown
# Architecture

Describe the actual repository architecture for {project-name}: source roots, module boundaries, dependency direction, runtime contracts, and validation commands.

## Overview

{high-level description of the system architecture, deployment model, and key runtime contracts.}

## Source Roots

| Root | Purpose | Owner |
| --- | --- | --- |
| `{source-root}` | {purpose, runtime responsibility, and entry points} | {owner} |
| `{test-root}` | {test scope and fixture ownership} | {owner} |

## Boundaries

| Boundary | Allowed Dependencies | Forbidden Dependencies |
| --- | --- | --- |
| {module or layer boundary} | {allowed imports, calls, or data flow} | {forbidden imports, calls, or data flow} |
| {external integration boundary} | {allowed client, adapter, or DTO usage} | {forbidden direct dependency or credential flow} |

## Data Flow

{description of how data moves through the system, from entry points to persistence and external services.}

## Key Decisions

- {decision, date, owner, and rationale}
- {decision, trade-off accepted, and revisit condition}

## Validation

```bash
{architecture validation command}
{type-check command}
{dependency-check command}
```
````

### `CLAUDE.md`

````markdown
# CLAUDE.md

Repository-level instruction map for {project-name}. Keep detailed guidance in the referenced docs and update those docs when implementation reality changes.

## Start Here

- Read `ARCHITECTURE.md` before changing source structure or dependency direction.
- Read `docs/harness/guardrails.md` and `docs/harness/readiness.md` before changing validation, CI, hooks, or repository policy.
- Read `docs/product-specs/` before changing product behavior.
- Read `docs/design-docs/` and topic docs under `docs/` before changing design, frontend, reliability, quality, or security behavior.
- Read `docs/exec-plans/active/` before continuing planned work.
- Read `docs/references/` only when a referenced technology applies.

## Project

{project-name} is {one-sentence description of the project purpose and scope}.

## Tech Stack

- Language: {language and minimum supported version}
- Runtime: {runtime, deployment target, or hosted platform}
- Package manager: {package-manager and lockfile policy}
- Framework: {framework and major version}

## Commands

```bash
{install-command}
{build-command}
{test-command}
{lint-command}
{format-command}
```

## Conventions

- {convention: naming, module boundaries, or dependency direction}
- {convention: required tests or evidence before merge}
- {convention: branching, release, or incident workflow}

## Owners

| Area | Owner |
| --- | --- |
| {area} | {owner} |
````

### `docs/.gitignore`

````text
# Ignore generated and temporary files for {project-name}

{generated-docs-temp-dir}/
{local-harness-temp-dir}/
*.tmp
*.generated.tmp
````

### `docs/DESIGN.md`

````markdown
# Design

Document product design principles, interaction patterns, and visual constraints that agents should preserve for {project-name}.

## Principles

- {design principle}
- {design principle}
- {design principle}

## Interaction Patterns

{description of core interaction patterns: navigation, input, feedback, and state transitions.}

## Visual Constraints

- {visual constraint: color, typography, spacing, or layout rule}
- {visual constraint}

## Component Conventions

{description of shared component patterns, naming, and composition rules.}

## Accessibility

{accessibility requirements and conformance target.}
````

### `docs/FRONTEND.md`

````markdown
# Frontend

Document frontend architecture, UI conventions, styling boundaries, routing, state, and validation commands for {project-name}.

## Architecture

{description of the frontend architecture: framework, rendering model, and key patterns.}

## Routing

| Route | Page | Auth Required |
| --- | --- | --- |
| `{path}` | {page-name} | {yes/no} |

## State Management

{description of state management approach: global vs. local, server vs. client, and data-fetching patterns.}

## Styling

- {styling approach: CSS modules, Tailwind, styled-components, etc.}
- {styling convention or constraint}

## Component Structure

| Directory | Purpose |
| --- | --- |
| `{path}` | {purpose} |

## Validation

```bash
{frontend lint command}
{frontend type-check command}
{frontend build command}
{frontend test command}
```
````

### `docs/PLANS.md`

````markdown
# Plans

Use this file as the index for active and completed execution plans for {project-name}.

## Active Plans

| Plan | Owner | Status | Target Date |
| --- | --- | --- | --- |
| `{docs/exec-plans/active/{plan-name}.md}` | {owner} | {draft/active/blocked} | {date} |

## Completed Plans

| Plan | Owner | Completed |
| --- | --- | --- |
| `{docs/exec-plans/completed/{plan-name}.md}` | {owner} | {date} |

## Plan Directories

- `exec-plans/active/` holds in-progress execution plans.
- `exec-plans/completed/` holds finished execution plans.
````

### `docs/PRODUCT_SENSE.md`

````markdown
# Product Sense

Record product judgment, customer context, and decision heuristics that should guide implementation for {project-name}.

## Target Users

{description of primary and secondary user personas and their goals.}

## Value Proposition

{one or two sentences on what makes this product valuable to its users.}

## Decision Heuristics

- {heuristic: when to choose approach A over approach B}
- {heuristic: prioritization rule for features or fixes}

## Customer Context

{key context about the customer environment, constraints, or workflows that shape product decisions.}

## Out of Scope

- {explicitly out-of-scope feature or behavior}
- {explicitly out-of-scope feature or behavior}
````

### `docs/QUALITY_SCORE.md`

````markdown
# Quality Score

Track repository quality signals, current grade, and ratchet criteria for {project-name}.

## Current Grade

{A/B/C/D/F or repository-specific quality grade with date and owner}

## Signals

| Signal | Status | Evidence |
| --- | --- | --- |
| {test, lint, type, security, docs, or reliability signal} | {pass/fail/warn} | `{command, CI run, or report path}` |
| {harness validation signal} | {pass/fail/warn} | `sh scripts/harness/validate_harness.sh` |

## Grade Criteria

- {measurable condition required before increasing the grade}
- {regression that immediately lowers or blocks the grade}

## History

| Date | Grade | Change |
| --- | --- | --- |
| {date} | {grade} | {what changed} |
````

### `docs/RELIABILITY.md`

````markdown
# Reliability

Document operational assumptions, failure modes, SLOs, recovery paths, and reliability validation for {project-name}.

## Operational Assumptions

- {assumption about infrastructure, hosting, or runtime environment}
- {assumption about dependencies or external services}

## SLOs

| Signal | Target | Measurement |
| --- | --- | --- |
| {signal} | {target} | {measurement method} |

## Failure Modes

| Failure | Impact | Mitigation |
| --- | --- | --- |
| {failure-mode} | {impact} | {mitigation strategy} |

## Recovery Paths

{description of recovery procedures: retry logic, fallback behavior, and manual intervention steps.}

## Reliability Validation

```bash
{reliability test command}
{load test or stress test command}
{health-check command}
```
````

### `docs/SECURITY.md`

````markdown
# Security

Document trust boundaries, sensitive data, authentication, authorization, dependency policy, and security validation for {project-name}.

## Trust Boundaries

{description of trust boundaries between client, server, third-party services, and internal systems.}

## Authentication

{authentication mechanism, token format, session management, and expiry policy.}

## Authorization

{authorization model, role definitions, and permission boundaries.}

## Sensitive Data

| Data | Storage | Encryption | Retention |
| --- | --- | --- | --- |
| {data-type} | {storage-location} | {encryption-method} | {retention-policy} |

## Dependency Policy

- {dependency security rule}
- {dependency security rule}

## Security Validation

```bash
{security audit command}
{dependency vulnerability scan command}
```
````

### `docs/design-docs/core-beliefs.md`

````markdown
# Core Beliefs

Capture product and engineering beliefs that should remain stable across feature work for {project-name}.

## Beliefs

1. {belief: a stable product or engineering conviction}
2. {belief: a stable product or engineering conviction}
3. {belief: a stable product or engineering conviction}

## Implications

{description of how these beliefs shape day-to-day implementation decisions.}

## Review Triggers

- {trigger: event or condition that should prompt a belief review}
- {trigger: event or condition that should prompt a belief review}
````

### `docs/design-docs/index.md`

````markdown
# Design Docs Index

List durable design documents and the decisions they own for {project-name}.

| Document | Status | Owner | Last Reviewed |
| --- | --- | --- | --- |
| `core-beliefs.md` | active | {owner} | {date} |
````

### `docs/exec-plans/active/.gitkeep`

````text

````

### `docs/exec-plans/completed/.gitkeep`

````text

````

### `docs/exec-plans/tech-debt-tracker.md`

````markdown
# Tech Debt Tracker

Track accepted follow-up work that should not be hidden in chat history for {project-name}.

| ID | Debt | Owner | Source | Target Resolution |
| --- | --- | --- | --- | --- |
| {id} | {summary} | {owner} | `{path}` | {date or condition} |
````

### `docs/generated/db-schema.md`

````markdown
# Database Schema

Generated documentation for the {project-name} database schema or persisted data shape. Keep this file only when the repository has a repeatable generation command.

## Provenance

| Field | Value |
| --- | --- |
| Source | `{schema source path}` |
| Generated by | `{command}` |
| Generated at | {date} |

## Schema Summary

{generated schema summary}

## Tables

| Table | Purpose | Key Columns |
| --- | --- | --- |
| {table-name} | {purpose} | {columns} |

## Regeneration

```bash
{command to regenerate this document}
```
````

### `docs/harness/guardrails.md`

````markdown
# Harness Guardrails

Document the repository-specific rules that harness checks and human reviewers enforce for {project-name}.

## Scope

- Repository: `{repository-name}`
- Primary runtime: {runtime or deployment target}
- Harness owner: {team or person responsible for guardrail updates}
- Review cadence: {weekly, per release, or per architecture change}

## Required Practices

| Area | Rule | Evidence |
| --- | --- | --- |
| Architecture | {boundary or dependency rule that must hold} | `{command or doc path}` |
| Testing | {minimum validation required before merge} | `{test command}` |
| Security | {secret handling, dependency, or auth rule} | `{audit command or policy path}` |
| Documentation | {doc that must change with behavior} | `{doc path}` |

## Blocked Patterns

- {disallowed pattern and why it is unsafe for this repository}
- {disallowed shortcut that previously caused drift or incidents}

## Exceptions

| Exception | Owner | Expiry | Tracking |
| --- | --- | --- | --- |
| {temporary exception} | {owner} | {date or release} | `{issue or plan path}` |

## Validation

```bash
{primary harness validation command}
{architecture or dependency guard command}
```
````

### `docs/harness/known-violations.md`

````markdown
# Known Violations

Track accepted harness violations for {project-name}. Each entry needs an owner, mitigation, and removal condition.

## Active Violations

| ID | Rule | Location | Impact | Owner | Removal Condition |
| --- | --- | --- | --- | --- | --- |
| {violation-id} | {guardrail or validator rule} | `{path or command}` | {risk if left unresolved} | {owner} | {date, release, or evidence} |

## Accepted Risk

- {risk statement tied to an active violation and why it is acceptable temporarily}
- {monitoring, manual review, or mitigation that reduces the risk}

## Resolved Violations

| ID | Resolved By | Evidence | Date |
| --- | --- | --- | --- |
| {violation-id} | {change or decision} | `{command, PR, or log}` | {date} |

## Review Cadence

Review this file {cadence} and remove entries as soon as the referenced violation is fixed or the exception expires.
````

### `docs/harness/readiness.md`

````markdown
# Harness Readiness

Track whether {project-name} is ready to move from advisory harness checks to enforced CI or hook gates.

## Current Status

| Field | Value |
| --- | --- |
| Current stage | {0, 1, 2, 3, or 4} |
| Target stage | {target stage} |
| Enforcement level | {warn or error} |
| Decision owner | {owner} |
| Last reviewed | {date} |

## Readiness Checklist

- [ ] `docs/harness/config.json` matches the installed file tree.
- [ ] Required docs are present and customized for this repository.
- [ ] Required commands run locally without missing-tool failures.
- [ ] Known violations are recorded in `docs/harness/known-violations.md`.
- [ ] CI or hook enforcement has an identified rollback owner.

## Validation Evidence

| Check | Command | Result | Evidence |
| --- | --- | --- | --- |
| Harness config | `sh scripts/harness/validate_harness.sh` | {pass/warn/fail} | `{log, CI run, or date}` |
| Build | `{build command}` | {pass/warn/fail} | `{log, CI run, or date}` |
| Tests | `{test command}` | {pass/warn/fail} | `{log, CI run, or date}` |

## Next Gate

{Describe the next readiness decision, required owner approval, and the exact config change that would enable enforcement.}
````

### `docs/harness/updates.md`

````markdown
# Harness Updates

Record harness config, validation, and scaffold changes for {project-name} so future agents can distinguish intentional customization from drift.

## Update Log

| Date | Change | Reason | Owner | Validation |
| --- | --- | --- | --- | --- |
| {date} | {config, script, doc, CI, or hook change} | {why the harness changed} | {owner} | `{command or evidence}` |

## Pending Updates

| Update | Blocker | Owner | Target |
| --- | --- | --- | --- |
| {needed harness change} | {missing decision, tool, or cleanup} | {owner} | {date or release} |

## Compatibility Notes

- {local tool version, CI image, shell, or runtime constraint that affects harness checks}
- {target-owned customization that should be preserved on reinstall}

## Regeneration Notes

When refreshing harness files, preserve target-owned edits unless the owner explicitly chooses replacement. Record each copied file, skipped conflict, and validation result here.
````

### `docs/product-specs/index.md`

````markdown
# Product Specs Index

List durable product, feature, and system specifications for {project-name}.

| Spec | Status | Owner | Validation |
| --- | --- | --- | --- |
| `new-user-onboarding.md` | draft | {owner} | `{command}` |
````

### `docs/product-specs/new-user-onboarding.md`

````markdown
# New User Onboarding

## Goal

{Describe the onboarding outcome for {project-name} and why it matters.}

## Target User

{description of the new user persona and their starting context.}

## Requirements

1. {Requirement with observable behavior.}
2. {Requirement with acceptance criteria.}
3. {Requirement with validation evidence.}

## Non-Goals

- {Out-of-scope behavior.}

## Success Metrics

| Metric | Target | Measurement |
| --- | --- | --- |
| {metric} | {target} | {measurement method} |

## Validation

```bash
{command}
```
````

### `docs/references/design-system-reference-llms.txt`

````text
# Design System Reference

Paste or generate design-system guidance for agents here when {project-name} has a design system. Include component names, tokens, layout rules, accessibility constraints, and examples.

## Tokens

| Token | Value | Usage |
| --- | --- | --- |
| {token-name} | {value} | {usage context} |

## Components

| Component | Purpose | Props |
| --- | --- | --- |
| {component-name} | {purpose} | {key props} |

## Layout Rules

- {layout rule or constraint}
- {layout rule or constraint}

## Accessibility

{accessibility constraints and ARIA requirements.}
````

### `docs/references/nixpacks-llms.txt`

````text
# Nixpacks Reference

Paste or generate Nixpacks deployment/build guidance for agents here when {project-name} uses Nixpacks.

## Build Configuration

{nixpacks build configuration or nixpacks.toml summary.}

## Phases

| Phase | Purpose | Customization |
| --- | --- | --- |
| {phase} | {purpose} | {customization notes} |

## Environment Variables

| Variable | Purpose |
| --- | --- |
| {variable} | {purpose} |

## Deployment

```bash
{deployment command}
```
````

### `docs/references/uv-llms.txt`

````text
# uv Reference

Paste or generate uv command and Python environment guidance for agents here when {project-name} uses uv.

## Setup

```bash
{uv install or sync command}
```

## Commands

```bash
{common uv run command}
{common uv run command}
```

## Python Version

{python-version}
````
