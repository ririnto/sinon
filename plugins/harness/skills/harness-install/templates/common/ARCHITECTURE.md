# Architecture

## System Context

Document the product boundary, external systems, users, and integration contracts.

## Components

Document major runtime components and the responsibilities each component owns.

## Data Flow

Document important data paths, persistence boundaries, event flows, and generated artifact sources.

Generated reference artifacts SHOULD be placed under `docs/generated/` when they are derived from authoritative project sources. The directory MAY remain empty (with `.gitkeep`) when no generation step exists yet; do not add fake placeholder files.

Generated artifacts SHOULD record their source command, source inputs, freshness, and regeneration trigger.

## Validation Surface

Document the commands, CI jobs, hooks, and review gates that protect architecture invariants.

## Change Policy

Architecture changes SHOULD be paired with updates to `docs/design-docs/`, `docs/product-specs/`, or `docs/exec-plans/` when those documents are affected.

Harness changes MAY be paired with architecture changes when the repository's development stage changes the required context, validation surface, or generated artifact inventory.
