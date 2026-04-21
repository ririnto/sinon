---
title: "Domain Capability"
description: >-
  Defines the domain-level capability and delegates ingestion behavior to the
  ingest sub-capability.
last_updated: "2026-03-06"
status: implemented
tag:
  - domain
call:
  - "./ingest/SPEC.md"
metadata: {}
---

## Necessity

The domain capability exists so reviewers can see the user-facing goal before looking at lower-level specs.

## Role

This SPEC owns the domain capability boundary and delegates ingestion behavior to the ingest SPEC.
It does not repeat ingestion-specific rules.

## Overview

The capability accepts a domain request, validates it, and coordinates the ingestion flow that produces a stable outcome.

## Functional Requirements

### Domain Capability Availability

The system SHALL expose a domain capability that accepts a valid request and returns a documented outcome.
Verification example:
Given a valid request, the ingest flow returns a success result.

## Scenarios

### Normal Flow

The caller submits a valid domain request.
The system routes the work to the ingest capability and returns a success outcome.

### Alternative Flow

The caller submits a valid request with optional attributes.
The system preserves the same invariants and returns a success outcome with the optional behavior applied.

### Error Flow

The caller submits an invalid request.
The system rejects the request, emits no partial side effects, and returns a documented error outcome.

## Key Entities

- `DomainRequest`:
  the user-provided input that triggers the capability.
- `DomainResult`:
  the stable outcome returned after validation and ingest processing.

## Constraints

- Validation MUST happen before side effects.
- The domain capability MUST rely on the linked ingest capability for ingest behavior.
