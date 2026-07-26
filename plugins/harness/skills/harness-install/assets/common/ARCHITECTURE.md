# Architecture

`ARCHITECTURE.md` maps this repository's domains, boundaries, data flow, and validation surfaces.
It records current structure separately from approved target or planned structure.

## Role

- Name the major system domains and their owners.
- Record dependency direction across domains.
- Tie architecture changes to validation evidence.

## Implementation Status

Use this table to distinguish shipped behavior from approved target direction.

| State | Architecture work |
| --- | --- |
| Current | `<implemented domains, boundaries, and runtime behavior>` |
| Target or planned | `<approved changes that are not implemented yet>` |

Do not describe target or planned work as current behavior.

## Domain Map

Replace the example row with this repository's real domains.

| Domain | Owns | Depends on |
| --- | --- | --- |
| `<domain>` | `<capability, data, or user workflow>` | `<internal or external dependency>` |

## Boundary Rules

- Code inside a domain owns its public API, data model, and validation rules.
- Cross-domain calls go through documented APIs, providers, or events.
- Shared utilities stay free of business policy.

## Data Flow

Document each primary flow as source, transformation, storage, and observable output.

| Flow | Source | Processing | Storage | Output |
| --- | --- | --- | --- | --- |
| `<flow>` | `<entry point>` | `<service or job>` | `<store>` | `<response, event, metric, or artifact>` |

## External Integrations

| Integration | Purpose | Boundary | Validation |
| --- | --- | --- | --- |
| `<service>` | `<capability>` | `<protocol, queue, file, or API>` | `<test, contract, or smoke check>` |

## Validation Surfaces

- Native stack validation checks code style and repository health.
- CI repeats the stack validation command before integration or release.
- Review checks architecture drift when boundaries, data flow, or integration contracts change.

## Verification Boundaries

Architecture changes require evidence at the boundary they alter.

| Boundary | Required evidence |
| --- | --- |
| `<domain or boundary>` | `<focused test, contract check, smoke check, or review evidence>` |

The complete change MUST pass the canonical repository checks in `CONTRIBUTING.md` before handoff.

## When This File Updates

- A domain, dependency direction, or integration changes.
- A validation surface is added, removed, or replaced.
- A design document changes a system boundary.

## Maintenance Rule

Every architecture-changing logical unit MUST update this file in the same unit.
Keep implemented behavior and future work distinct, remove superseded transitional language, and reject documentation that no longer matches the code or declared boundaries.
