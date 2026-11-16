# PromQL Query Examples

## Ready-to-Adapt Templates

Basic selector -- use one metric and one bounded label set:

```promql
up{job="api",instance=~"api-.+"}
```

Use when: you need a simple instant-vector selector for current target health.

Range-vector aggregation -- convert a counter into a grouped per-second rate:

```promql
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need one stable rate query for dashboards, recording rules, or alert thresholds.

Alert-oriented shape -- keep the query stable and symptom-oriented:

```promql
5 < round(
  100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
    /
  sum(rate(http_requests_total{job="api"}[5m])),
  0.001
)
```

Use when: you need a threshold query that can be embedded in an alert rule without hiding the user-facing symptom.

Dashboard-oriented shape -- use `irate()` only for visually volatile counters:

```promql
sum by (instance) (irate(node_network_receive_bytes_total{job="node"}[1m]))
```

Use when: you are shaping a fast-moving dashboard panel rather than a stable alert condition.

Basic vector matching -- start without a modifier when both sides already share the same grouped label set:

```promql
sum by (job) (rate(http_requests_total{job="api",status=~"5.."}[5m]))
/
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need to preserve one shared label set across both sides of a binary operation and both sides already align without extra matching rules.

Missing-series shape -- page on disappearance instead of on a low numeric threshold:

```promql
absent(up{job="api"})
```

Use when: the query should return a signal only when the expected series is missing.
