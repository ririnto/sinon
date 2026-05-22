# Frontend

Document the project's user-facing surfaces — UI screens, CLI commands, public APIs, agent response formats, embeddable widgets, webhooks, and any other shape through which users (human or agent) interact with the system. The name "Frontend" is retained for familiarity, but the scope is every user-visible surface, not only the web UI.

## Purpose

Capture the rules an agent needs to extend the user-facing surface safely. Even when the project has no web UI, this document still applies to the shape of CLI flags, JSON response schemas, error message formats, agent reply structures, and any other contact point exposed to users.

For projects with a web UI, this includes component composition, data-fetching shape, design-token sources, and accessibility minimums. For projects with a CLI or API surface, this includes argument conventions, response and error shape, versioning, and backward-compatibility rules.

## When To Update

Update when a new component pattern, command flag, response shape, accessibility expectation, error contract, or design-system migration changes the surface contract.

## Required Evidence

- Link to the canonical implementation (component, route handler, CLI command, schema definition) that demonstrates the rule.
- Link to the design-system, API spec, or CLI usage reference when one exists.
