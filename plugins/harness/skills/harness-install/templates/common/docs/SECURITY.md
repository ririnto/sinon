# Security

Document the threat model, authentication and authorization boundaries, secret handling, and supply-chain controls.

## Purpose

Tell agents which surfaces are trust boundaries and which are not, so changes near auth, input parsing, or external dependencies receive the right scrutiny.

## When To Update

Update when a new external dependency is added, when an authentication path changes, when a secret-store policy shifts, or when an incident exposes a missing control.

## Required Evidence

- Cite the implementation file or middleware that enforces the boundary.
- Link the policy doc or external compliance reference when one exists.
