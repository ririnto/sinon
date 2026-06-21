# Architecture

`ARCHITECTURE.md` maps this repository's domains, boundaries, data flow, and validation surfaces.

## Role

- Name the major system domains and their owners.
- Record dependency direction across domains.
- Tie architecture changes to validation evidence.

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

## When This File Updates

- A domain, dependency direction, or integration changes.
- A validation surface is added, removed, or replaced.
- A design document changes a system boundary.
