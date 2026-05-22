# Design

Document cross-cutting design guidance, layering rules, dependency-direction constraints, and review criteria that apply across the repository.

## Purpose

Capture durable design decisions that span multiple domains: layering constraints, dependency direction rules, naming conventions, and review criteria that reviewers and agents apply to every change.

## When To Update

Update when a layering rule changes, when a recurring review comment exposes an undocumented invariant, or when a domain-spanning decision needs a citable reference under `docs/design-docs/`.

## Required Evidence

- Link to the implementation file or commit where the rule is enforced (lint, type, or runtime check).
- Link to the related entry under `docs/design-docs/` when a deeper decision record exists.
