---
name: event-taxonomy
description: >-
  Concrete event-taxonomy choices for stable Sentry triage in shared services.
---

# Sentry Event Taxonomy

## Recommended Label Map

Use this label map consistently across services:

| Label | Value |
| --- | --- |
| `service` | owning service name |
| `team` | on-call team |
| `tier` | `frontend`, `backend`, `queue`, `batch` |
| `environment` | `prod`, `stage`, `dev` |
| `release` | commit tag |

## Default Routing Rule

- `severity=critical` and `environment=prod`: immediate on-call notification
- `severity=warning` and `release` changed in last 24h: one-hour reminder cadence
- `user-facing` flag present: route to product incident channel

## Sample Playbook Step

For a regression spike: isolate by `fingerprint`, compare `first_seen` age, then narrow by `environment` and `release`.
