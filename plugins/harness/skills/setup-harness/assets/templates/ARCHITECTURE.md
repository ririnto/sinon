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
