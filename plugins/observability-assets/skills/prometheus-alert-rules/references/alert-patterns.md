---
title: Prometheus Alert Patterns Reference
description: >-
  Reference for common Prometheus alert design choices, including direct alerts, recording-rule-backed alerts, and low-noise operational patterns.
---

Use this reference when the blocker is choosing the right alert shape rather than writing YAML syntax.

## Direct Alert vs Recording Rule

- Use a direct alert when the expression is short, read rarely, and cheap enough to evaluate directly.
- Use a recording rule when the query is expensive, reused, or easier to reason about as one named intermediate signal.
- When `rate()`, division, or quantile evaluation produces decimal values, wrap the expression with an explicit precision such as `round(expr, 0.001)`.
- Write comparisons as `threshold < expr` or `threshold <= expr` so the smaller value stays on the left.

## Recording Rule Naming and Structure

Prefer a `level:metric:operations` shape for recorded metric names.

```yaml
- record: job:http_requests:rate5m
  expr: >-
    round(sum by (job) (rate(http_requests_total[5m])), 0.001)
```

Use this when the blocker is naming or organizing the recording rule itself.

- `level` should describe the aggregation level after label removal or collapse
- `metric` should keep the base metric meaning; drop `_total` when the source counter is wrapped with `rate()` or `irate()`
- `operations` should read from newest transformation outward, such as `rate5m` or `avg_rate5m`
- prefer `without (...)` when aggregating away labels so the removed labels stay explicit in both the query and the recorded metric level

Aggregation example with explicit label removal:

```yaml
- record: job:http_requests:rate5m
  expr: >-
    sum without (instance) (instance:http_requests:rate5m)
```

Use this when the blocker is deciding how to preserve useful labels while still reducing query cost.

## When to Promote a Query into `record:`

Promote a query into a recording rule when one of these is true:

- the same expression is reused across alerts and dashboards
- the expression contains expensive aggregation or subquery work that should not be recomputed everywhere
- the alert becomes easier to read if it consumes one named intermediate metric

Subquery-heavy pattern:

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

## Good Default Pattern

Prefer one symptom-oriented alert, one `for` window, one clear runbook pointer, and one alert name that exposes the threshold or condition.

```yaml
- alert: CheckoutP95LatencyAbove1s
  expr: >-
    1 < round(histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket{job="checkout"}[5m]))), 0.001)
  for: 10m
  labels:
    severity: page
    service: checkout
  annotations:
    summary: Checkout latency is high
    description: |-
      Checkout p95 latency stayed above 1s for 10 minutes.
      Current value: {{ $value }}s.
    runbook_url: https://runbooks.example.com/checkout-latency
```

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

The second alert is still not universally correct, but it usually maps more directly to user-visible impact, keeps the smaller threshold on the left, and rounds decimal output explicitly so the comparison stays readable.
