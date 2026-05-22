# Reliability

## Purpose

RELIABILITY.md captures the reliability contract: the SLOs the system targets, the failure modes the team accepts and how each is mitigated, the recovery procedures, and where to look in observability tooling. An agent loading this file should be able to judge whether a change preserves the reliability bar.

## SLOs

| SLI | SLO | Window | Burn alert | Owner |
| --- | --- | --- | --- | --- |
| API availability | 99.9% | 30d | Firing after 30m at 6x error rate | on-call |

Replace the example row with SLIs and SLO targets your team commits to.

## Failure Modes

- **Network partition**: API calls time out. Mitigated via circuit breaker with exponential backoff and fallback cache.
- **Dependency outage**: Downstream service unavailable. Mitigated via graceful degradation and cached responses.
- **Deploy-time regression**: New code path causes elevated latency. Mitigated via canary deploy and P99 latency alert.

## Recovery Procedures

- **API high error rate**: see `docs/runbooks/api-errors.md`
- **Database connection exhaustion**: see `docs/runbooks/db-connections.md`

## Observability

- **Structured logs**: schema `{timestamp, level, request_id, service, error}`. Query via LogQL: `{service="api"} | json`.
- **Metrics**: namespace `myapp_*`, track latency, error rate, and throughput. Query via PromQL: `rate(myapp_requests_total[5m])`.
- **Traces**: propagate `X-Trace-ID` header. Query via TraceQL: search by `trace_id` in tracing backend.
- **Dashboard**: system health available at `https://monitoring.internal/dash/system-health`.

## When To Update

- When an SLO is added, retired, or has its target changed.
- When a new failure mode is observed.
- When a recovery procedure is added or replaced.
- When an observability surface is added or renamed.

## Required Evidence

- **SLO targets**: cite the dashboard URL, alert rule definition, or postmortem that supports each entry.
- **Failure modes**: link to the incident report or design document that justified the mitigation strategy.
- **Recovery procedures**: link to the runbook file used for each recovery procedure.
- **Observability**: cite the schema definition or dashboard URL for each observability surface.
