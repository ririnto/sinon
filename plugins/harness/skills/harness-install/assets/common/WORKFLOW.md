# Workflow

Document the project's day-to-day workflow for human and agent contributors: how work enters the repository, how it is reviewed, how it ships, and where authoritative state lives.

## Purpose

Give agents a single page that defines repository workflow: branch naming, commit conventions, review gates, deployment promotion, and runbook entry points.
This document is the procedural counterpart of `CLAUDE.md` (the repository contract) and `ARCHITECTURE.md` (the system shape).

## When To Update

Update when an entry point changes (branching, commit form, review gate, release cadence, on-call hand-off), when a new automation lands, or when a workflow rule is enforced by lint or CI for the first time.

## Required Evidence

- Cite the CI config, Git hook, or automation script that enforces each step.
- Link to runbooks, dashboards, or on-call rotations referenced from this document.
