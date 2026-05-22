# Reliability

Document SLOs, on-call posture, incident runbook locations, and observability invariants.

## Purpose

State the reliability contract the service offers users and the operating signals agents and humans use to confirm it. Agents MUST treat SLO violations and missing runbooks as blockers when changing affected code paths.

## When To Update

Update when an SLO target shifts, when a runbook is added or retired, when an incident reveals an observability gap, or when on-call rotation policy changes.

## Required Evidence

- Cite the dashboard, alert, or postmortem that justifies the entry.
- Link the runbook file directly so agents do not need to search for it.
