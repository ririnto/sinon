# Frontend

## Purpose

FRONTEND.md captures conventions for the project's *exposed surface* — every interface through which a user or another system observes or drives this project. Web UI, mobile UI, CLI, public HTTP/gRPC API, webhook out, public SDK, and integration endpoints all count. The goal is that an agent can reason about how the project is exercised from outside the repository: which surfaces exist, which design-system primitives or contract primitives are canonical, where surface boundaries fall, and the accessibility / ergonomics floor every change preserves.

This is not only about a frontend-side application. A project with no web UI still has surfaces (CLI, API, SDK) and MUST document them here. A project with multiple surfaces (web + mobile + API) MUST document each one, plus the shared conventions across surfaces.

## Exposed Surfaces

- List each surface this project exposes and one sentence describing its responsibility.
- Examples: `Web UI` (browser-facing application), `Mobile UI` (iOS/Android shells), `Public HTTP API` (REST/GraphQL endpoints consumed by external clients), `CLI` (command-line entry points), `Outbound webhooks` (events emitted to other systems), `Public SDK` (libraries published for external consumers).
- For each, name the entry point (URL pattern, binary name, package path) and the canonical client persona.

## Surface Legibility

- Mark stable interactive and test-indexable UI elements with `data-testid` attributes so headless drivers (Chrome DevTools MCP, Playwright, agent harnesses) can locate state without fragile selectors.
- Use canonical state markers (`data-state="loading"`, `data-state="error"`, `data-state="empty"`) on UI containers so agents detect state transitions deterministically.
- Mirror structured log lines for surface-level events (request received, validation rejected, response sent) into the dev console and the central log stream so the same query language works for UI debugging and API debugging.
- For non-UI surfaces (CLI, API, SDK), publish a machine-readable surface contract (OpenAPI / GraphQL schema / typed SDK definition / `--help` output schema) and treat that contract as the source of truth for changes.

## Design System and Contract Primitives

- Name the canonical design system for UI surfaces (component library path, design-token file, icon set) and require that all visual primitives come from it.
- Name the canonical contract primitives for non-UI surfaces (shared schema for API errors, standard envelope for paginated lists, conventional CLI exit codes, standard webhook signature scheme).
- Forbid ad-hoc parallel implementations: a new color, a new error envelope, or a new CLI flag pattern MUST be added to the canonical set before being used anywhere.

## Surface Boundaries

- Separate *surface-shape* layers from *business-logic* layers: presentational components vs. containers (UI); HTTP handlers vs. service code (API); CLI command parsers vs. domain operations (CLI). File-path conventions or lints enforce the split.
- Keep public surfaces (browser, mobile, external API) distinct from internal-only surfaces (admin tools, internal RPC). Each MUST sit in its own directory and follow the access-control rules in `docs/SECURITY.md`.
- Versioning rules: public surfaces MUST follow the project's stated compatibility policy (semver, dated API versions, deprecation windows); link to that policy from this file.

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
