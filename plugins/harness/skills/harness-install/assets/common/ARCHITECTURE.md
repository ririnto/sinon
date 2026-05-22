# Architecture

## Purpose

ARCHITECTURE.md is the top-level map of domains, package layering, data flow, and validation surfaces. See matklad's architecture-document convention: it is one line. An agent loading this file should know where each business domain lives, how data flows between domains, and which validation surfaces gate changes.

## Domain Map

- {{Identity}}: User authentication, session management, role-based access control, and tenant isolation.
- {{Catalog}}: Core business entity management — product listings, inventory, metadata, and pricing tiers.
- {{Notifications}}: Outbound email delivery, webhook dispatches, and event subscriptions.

## Package Layering

Refer to `docs/DESIGN.md` for the canonical layering pattern within each domain. The model organizes code by horizontal layer: presentation (API surface), business logic, data access, and storage. Dependencies flow downward; cross-domain dependencies use provider injection or event dispatch.

```mermaid
---
title: Package layering (per-domain horizontal layers + cross-domain providers)
---
%%{init: {'theme': 'dark', 'primaryColor': '#0d0d0d', 'primaryTextColor': '#FFFFFF', 'primaryBorderColor': '#FFFFFF', 'lineColor': '#FFFFFF', 'clusterBkg': '#1a1a1a', 'clusterBorder': '#FFFFFF'}}%%
flowchart LR
    Identity(([Identity]))
    Catalog(([Catalog]))
    Notifications(([Notifications]))
    Shared[Shared utilities]
    
    Identity --> Shared
    Catalog --> Shared
    Notifications --> Shared
    
    Catalog -.via IdentityProvider.-> Identity
    Notifications -.via EventBus.-> Catalog
```

## Data Flow

- Request path: HTTP/{{protocol}} entry → handler → service → repository → datastore.
- Event path: domain event → message bus → projection handler → read model.

## External Integrations

- Identity provider: OAuth/OIDC to `https://{{idp-host}}` — user sign-in, token validation, and claims mapping via REST.
- Payment gateway: {{payment-provider}} REST API — card tokenization, charge processing, and subscription lifecycle.
- Telemetry backend: OpenTelemetry OTLP export to `{{otlp-endpoint}}` — distributed traces, metrics, and error reporting via gRPC.

## Validation Surfaces

- Harness validator: enforces repository structure, documentation presence, and agent skill invariants per `docs/harness/manifest.json`.
- Lint rules: enforces style, naming, and module boundary rules in the repository lint config.
- Structural tests: enforces package layer ordering and cross-domain dependency rules under `tests/structure/`.
- CI workflow: enforces full build, test, and integration pass at `.github/workflows/harness.yml` before merge.

## When To Update

- When a new domain is added.
- When a layering rule changes.
- When an external integration is added or replaced.
- When a new validation surface is introduced.

## Required Evidence

- Link to entries under `docs/design-docs/`, `docs/product-specs/`, or `docs/exec-plans/` when those documents are affected.
- Cite the validator config or CI job for each validation surface.
