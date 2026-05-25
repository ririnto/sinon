# Frontend

## Purpose

FRONTEND.md captures conventions for the project's *exposed surface* — every interface through which a user or another system observes or drives this project. Web UI, mobile UI, CLI, public HTTP/gRPC API, webhook out, public SDK, and integration endpoints all count. The goal is that an agent can reason about how the project is exercised from outside the repository: which surfaces exist, which design-system primitives or contract primitives are canonical, where surface boundaries fall, and the accessibility / ergonomics floor every change preserves.

This is not only about a frontend-side application. A project with no web UI still has surfaces (CLI, API, SDK) and MUST document them here. A project with multiple surfaces (web + mobile + API) MUST document each one, plus the shared conventions across surfaces.

## Exposed Surfaces

Delete rows below that do not apply to this project. Replace placeholders with real entry points, package names, and source paths.

| Surface | Entry point | Canonical client persona | Contract source of truth |
| --- | --- | --- | --- |
| Web UI | `https://app.example.com/` | browser-facing end user | `apps/web/` |
| Mobile UI | `com.example.app` / `com.example.app` | mobile end user | `apps/mobile/` |
| Public HTTP API | `https://api.app.example.com/v1` | external integrator | `apis/public/openapi.yaml` |
| CLI | `example-cli` | power user, operator | `apps/cli/` |
| Outbound webhooks | `POST https://customer.example.com/webhook` | customer-integrating system | `events/webhook-catalog.md` |
| Public SDK | `npm:example-sdk`, `pypi:example-sdk` | external integrator | `sdk/` |

## Surface Legibility

- Mark stable interactive and test-indexable UI elements with `data-testid` attributes so headless drivers (Chrome DevTools MCP, Playwright, agent harnesses) can locate state without fragile selectors.
- Use canonical state markers (`data-state="loading"`, `data-state="error"`, `data-state="empty"`) on UI containers so agents detect state transitions deterministically.
- Mirror structured log lines for surface-level events (request received, validation rejected, response sent) into the dev console and the central log stream so the same query language works for UI debugging and API debugging.
- For non-UI surfaces (CLI, API, SDK), publish a machine-readable surface contract (OpenAPI / GraphQL schema / typed SDK definition / `--help` output schema) and treat that contract as the source of truth for changes.

## Design System and Contract Primitives

- Canonical design system for UI: `packages/design-system/` (component library), `packages/design-system/tokens.json` (design tokens), `packages/design-system/icons/` (icon set).
- Canonical contract primitives: API error schema at `apis/public/openapi.yaml#/components/schemas/Error`, pagination envelope in the same schema, CLI exit-code table at `docs/cli/exit-codes.md`, webhook signature scheme `HMAC-SHA256 over body with header X-Signature`.
- Forbid ad-hoc parallel implementations: a new color, a new error envelope, or a new CLI flag pattern MUST be added to the canonical set before being used anywhere.

## Surface Boundaries

- Separate *surface-shape* layers from *business-logic* layers: presentational components vs. containers (UI); HTTP handlers vs. service code (API); CLI command parsers vs. domain operations (CLI). File-path conventions or lints enforce the split.
- Keep public surfaces (browser, mobile, external API) distinct from internal-only surfaces (admin tools, internal RPC). Each MUST sit in its own directory and follow the access-control rules in `docs/SECURITY.md`.
- Public surfaces follow the project's compatibility policy: dated URL prefix (e.g. `/v1`) for the HTTP API, semver for SDKs and CLI, six-month sunset window with sunset header / SDK warning.

## Accessibility and Ergonomics Floor

- UI surfaces: every interactive element MUST be keyboard-reachable; focus order MUST match visual flow; interactive non-button elements MUST carry appropriate ARIA roles; meaningful images MUST have alt text; color contrast MUST meet WCAG AA (4.5:1 text, 3:1 graphics); `:focus-visible` styles MUST be preserved.
- API / CLI / SDK surfaces: every error response MUST include an actionable message and a stable error code; CLI commands MUST support `--help` and a non-interactive flag; SDK methods MUST be documented with type signatures; webhook payloads MUST carry a stable event identifier so consumers can deduplicate.
- Localization: when a surface supports multiple languages, every user-visible string MUST go through the project's i18n layer; literal strings in surface code are rejected by lint.

## When To Update

- When a new exposed surface is added or an existing surface is retired.
- When a design-system primitive or contract primitive lands in the canonical source of truth.
- When a surface-boundary rule changes or a file-path convention shifts.
- When the accessibility / ergonomics floor is raised (for example, the project upgrades the targeted WCAG conformance level, or the API adopts a new error envelope).
- When a new headless driver, test-indexing convention, or surface-contract format (OpenAPI, GraphQL SDL) is adopted.

## Required Evidence

- For each exposed surface: link to the entry-point file, surface-contract file (OpenAPI / SDL / SDK index), or runbook that documents how the surface is operated.
- For each canonical primitive: cite the design-system file, contract-schema file, or shared utility module that supplies it.
- For each surface-boundary rule: link to the lint rule, file-path convention, or structural test that enforces it.
- For each accessibility / ergonomics invariant: cite the axe-core rule, custom lint, contract test, or manual-review checklist that validates it.
- Link to related entries under `docs/design-docs/` when a deeper decision exists.
