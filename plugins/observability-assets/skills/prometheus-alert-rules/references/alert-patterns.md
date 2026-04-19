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

- the same expression is reused across alerts and dashboards
- the expression contains expensive aggregation or subquery work that should not be recomputed everywhere
- the alert becomes easier to read if it consumes one named intermediate metric

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

Better:

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

The second alert is still not universally correct, but it usually maps more directly to user-visible impact and is the better shape when the blocker is poor signal choice rather than YAML mechanics.

Use this reference for pattern choice, not for ordinary rule anatomy. Baseline guidance for rule groups, `for`, `keep_firing_for`, labels, annotations, templating, and Alertmanager-facing label contracts stays in [`../SKILL.md`](../SKILL.md).
