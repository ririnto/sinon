---
title: Prometheus Alert Patterns Reference
description: "Open this when choosing between direct alerts, recording-rule-backed alerts, or reusable low-noise alert patterns is the blocker."
---

Use this reference when the blocker is choosing the right alert shape rather than writing YAML syntax.

## Direct Alert vs Recording Rule

- Use a direct alert when the expression is short, read rarely, and cheap enough to evaluate directly.
- Use a recording rule when the query is expensive, reused, or easier to reason about as one named intermediate signal.

Decision sketch:

```text
short and one-off expression -> direct alert
expensive or reused expression -> recording rule first, alert second
```

Use this when the blocker is deciding whether the alert should stay direct or split through one reusable recorded metric.

## When to Promote a Query into `record:`

Promote a query into a recording rule when one of these is true:

- The same expression is reused across alerts and dashboards.
- The expression contains expensive aggregation or subquery work that should not be recomputed everywhere.
- The alert becomes easier to read if it consumes one named intermediate metric.

Recording-rule split pattern:

```yaml
- record: job:http_requests:rate5m
  expr: >-
    round(sum by (job) (rate(http_requests_total[5m])), 0.001)

- alert: Api5xxRatioAbove5Percent
  expr: |-
    5 < round(
      100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
        /
      sum(job:http_requests:rate5m{job="api"}),
      0.001
    )
```

Use this when the blocker is deciding whether one repeated expression deserves its own reusable recorded metric.

## Broken vs Better

Broken:

```yaml
- alert: CpuUsageAbove90Percent
  expr: >-
    0.9 < round(rate(node_cpu_seconds_total{mode!="idle"}[1m]), 0.001)
```

Better -- symptom-oriented with explicit `for` window and actionable annotations:

```yaml
- alert: Api5xxRatioAbove5Percent
  expr: |-
    5 < round(
      100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
        /
      sum(rate(http_requests_total{job="api"}[5m])),
      0.001
    )
  for: 10m
  annotations:
    description: |-
      API 5xx ratio stayed above 5% for 10 minutes.
      Current value: {{ $value }}%.
```

The full template with labels and runbook is in [`../SKILL.md`](../SKILL.md). This reference focuses on pattern choice: the second alert maps more directly to user-visible impact than a low-level saturation signal.

Use this reference for pattern choice, not for ordinary rule anatomy. Baseline guidance for rule groups, `for`, `keep_firing_for`, labels, annotations, templating, and Alertmanager-facing label contracts stays in [`../SKILL.md`](../SKILL.md).

## Multi-Group Layout Patterns

### Separate Recording and Alert Groups

Keep recording rules in their own group so they evaluate independently from alerts that consume them:

Place the recording-rule group first so the alert group reads already-computed series from the earlier evaluation step.

```yaml
groups:
  - name: api-recording
    interval: 30s
    rules:
      - record: job:http_requests:rate5m
        expr: >-
          round(sum by (job) (rate(http_requests_total[5m])), 0.001)
      - record: job:http_errors:rate5m
        expr: >-
          sum by (job) (rate(http_requests_total{status=~"5.."}[5m]))

  - name: api-alerts
    interval: 1m
    rules:
      - alert: Api5xxRatioAbove5Percent
        expr: |-
          5 < 100 * job:http_errors:rate5m{job="api"}
              /
            job:http_requests:rate5m{job="api"}
        for: 10m
        labels:
          severity: page
          service: api
```

Use when: multiple alerts share expensive base queries and you want one canonical evaluation point.

### Domain-Based Grouping

Group rules by the domain or service they monitor rather than by rule type:

```yaml
groups:
  - name: database
    interval: 30s
    rules:
      - record: pg:query_latency:p99:5m
        expr: histogram_quantile(0.99, rate(pg_query_duration_seconds_bucket[5m]))
      - alert: DatabaseQueryLatencyHigh
        expr: 2 < pg:query_latency:p99:5m
        for: 15m
        labels:
          severity: warning
          team: database

  - name: cache
    interval: 15s
    rules:
      - record: redis:hit_rate:1m
        expr: sum(rate(redis_commands_total{cmd="get"}[1m])) / sum(rate(redis_commands_total[1m]))
      - alert: RedisCacheHitRateLow
        expr: redis:hit_rate:1m < 0.9
        for: 10m
        labels:
          severity: warning
          team: platform
```

Use when: different teams own different domains and group-level intervals differ per domain's natural timescale.

## Noise-Control Pattern Catalog

### Rate-of-Change with Absent Detection

Alert on both the presence of a condition AND the absence of expected data:

```yaml
- alert: TargetDown
  expr: up == 0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: 'Target {{ $labels.job }}/{{ $labels.instance }} is down'

- alert: NoScrapeTarget
  expr: absent(up{job="api"})
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: 'No scrape targets found for job=api'
```

Use when: silent data loss (no samples at all) is as dangerous as an explicit failure signal.

### Sustained Deviation with Predictable Baseline

Compare current values against a rolling baseline rather than a fixed threshold:

```yaml
- alert: RequestRateAnomaly
  expr: |-
    abs(
      sum(rate(http_requests_total[5m]))
      -
      avg_over_time(sum(rate(http_requests_total[5m]))[1h:])
    )
    / avg_over_time(sum(rate(http_requests_total[5m]))[1h:])
    > 2
  for: 30m
  labels:
    severity: warning
  annotations:
    summary: 'Request rate anomaly detected for {{ $labels.job }}'
    description: >-
      Current rate deviates more than 200% from the 1-hour average.
      Current: {{ $value }}.
```

Use when: traffic patterns vary by time of day and fixed thresholds produce false positives during low-traffic periods.

### Blackbox Probe with Partial Failure Handling

Distinguish total failure from degraded performance in probe-based alerts:

```yaml
- name: blackbox-probes
  rules:
    - alert: BlackboxProbeFailed
      expr: probe_success == 0
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: 'Blackbox probe failed for {{ $labels.instance }}'
        description: >-
          Probe {{ $labels.instance }} has been failing for 5 minutes.

    - alert: BlackboxProbeSlow
      expr: probe_duration_seconds > 2
      for: 10m
      labels:
        severity: warning
      annotations:
        summary: 'Blackbox probe slow for {{ $labels.instance }}'
        description: >-
          Probe response time is {{ $value }}s (threshold: 2s).
```

Use when: you need separate urgency levels for "completely broken" versus "working but slow."
