# Frontend

## Purpose

`FRONTEND.md` captures conventions for the project's *exposed surface*.
That means every interface through which a user or another system observes or drives this project.
Web UI, mobile UI, CLI, public HTTP/gRPC API, webhook out, public SDK, and integration endpoints all count.
The goal is that an agent can reason about how the project is exercised from outside the repository:

- which surfaces exist
- which design-system primitives or contract primitives are canonical
- where surface boundaries fall
- which accessibility / ergonomics floor every change preserves

This file covers more than frontend-side applications.
A project with no web UI still has surfaces (CLI, API, SDK) and MUST document them here.
A project with multiple surfaces (web + mobile + API) MUST document each one, plus the shared conventions across surfaces.

## Exposed Surfaces

Delete rows below that do not apply to this project.
Replace placeholders with real entry points, package names, and source paths.

| Surface | Entry point | Canonical client persona | Contract source of truth |
| --- | --- | --- | --- |
| Web UI | `https://app.example.com/` | browser-facing end user | `apps/web/` |
| Mobile UI | `com.example.app` / `com.example.app` | mobile end user | `apps/mobile/` |
| Public HTTP API | `https://api.app.example.com/v1` | external integrator | `apis/public/openapi.yaml` |
| CLI | `example-cli` | power user, operator | `apps/cli/` |
| Outbound webhooks | `POST https://customer.example.com/webhook` | customer-integrating system | `events/webhook-catalog.md` |
| Public SDK | `npm:example-sdk`, `pypi:example-sdk` | external integrator | `sdk/` |

## Surface Legibility

- Mark stable interactive and test-indexable UI elements with `data-testid` attributes.
  - Headless drivers and agent frameworks can then locate state without fragile selectors.
- Use canonical state markers on UI containers so agents detect state transitions deterministically.
  - Examples: `data-state="loading"`, `data-state="error"`, and `data-state="empty"`.
- Mirror structured log lines for surface-level events into the dev console and central log stream.
  - Examples include request received, validation rejected, and response sent.
  - The same query language should work for UI debugging and API debugging.
- For non-UI surfaces, publish a machine-readable surface contract.
  - Covers CLI, API, and SDK surfaces.
  - Examples include OpenAPI, GraphQL schema, typed SDK definition, and `--help` output schema.
  - Treat that contract as the source of truth for changes.

## Design System and Contract Primitives

- Canonical design system for UI:
  - component library: `packages/design-system/`
  - design tokens: `packages/design-system/tokens.json`
  - icon set: `packages/design-system/icons/`
- Canonical contract primitives:
  - API error schema: `apis/public/openapi.yaml#/components/schemas/Error`
  - pagination envelope: the same OpenAPI schema
  - CLI exit-code table: `docs/cli/exit-codes.md`
  - webhook signature scheme: `HMAC-SHA256 over body with header X-Signature`
- Forbid ad-hoc parallel implementations.
  - A new color, error envelope, or CLI flag pattern MUST be added to the canonical set before use.

## Surface Boundaries

- Separate *surface-shape* layers from *business-logic* layers.
  - UI: presentational components versus containers.
  - API: HTTP handlers versus service code.
  - CLI: command parsers versus domain operations.
  - File-path conventions or lints enforce the split.
- Keep public surfaces (browser, mobile, external API) distinct from internal-only surfaces (admin tools, internal RPC).
  - Each MUST sit in its own directory and follow the access-control rules in `docs/SECURITY.md`.
- Public surfaces follow the project's compatibility policy.
  - HTTP API: dated URL prefix, such as `/v1`.
  - SDKs and CLI: semver.
  - Deprecations: six-month sunset window with sunset header or SDK warning.

## Accessibility and Ergonomics Floor

- UI surfaces:
  - Every interactive element MUST be keyboard-reachable.
  - Focus order MUST match visual flow.
  - Interactive non-button elements MUST carry appropriate ARIA roles.
  - Meaningful images MUST have alt text.
  - Color contrast MUST meet WCAG AA: 4.5:1 for text and 3:1 for graphics.
  - `:focus-visible` styles MUST be preserved.
- API / CLI / SDK surfaces:
  - Every error response MUST include an actionable message and a stable error code.
  - CLI commands MUST support `--help` and a non-interactive flag.
  - SDK methods MUST be documented with type signatures.
  - Webhook payloads MUST carry a stable event identifier so consumers can deduplicate.
- Localization:
  - Every user-visible string MUST go through the project's i18n layer when a surface supports multiple languages.
  - Literal strings in surface code are rejected by lint.

## When To Update

- When a new exposed surface is added or an existing surface is retired.
- When a design-system primitive or contract primitive lands in the canonical source of truth.
- When a surface-boundary rule changes or a file-path convention shifts.
- When the accessibility / ergonomics floor is raised.
  - Examples: the project upgrades the targeted WCAG conformance level, or the API adopts a new error envelope.
- When a new headless driver, test-indexing convention, or surface-contract format (OpenAPI, GraphQL SDL) is adopted.

## Required Evidence

- For each exposed surface: link to the entry-point file, surface-contract file, or runbook that documents how the surface is operated.
  - Surface-contract examples: OpenAPI, SDL, or SDK index.
- For each canonical primitive: cite the design-system file, contract-schema file, or shared utility module that supplies it.
- For each surface-boundary rule: link to the lint rule, file-path convention, or structural test that enforces it.
- For each accessibility / ergonomics invariant: cite the validation source.
  - Examples: axe-core rule, custom lint, contract test, or manual-review checklist.
- Link to related entries under `docs/design-docs/` when a deeper decision exists.
