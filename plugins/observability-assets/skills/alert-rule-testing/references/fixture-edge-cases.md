---
title: Alert Rule Fixture Edge Cases
description: "Open this when stale samples, missing samples, native histogram fixtures, or series-not-in-input edge cases are the blocker."
---

Use this reference when ordinary counter or gauge sequences are no longer enough to express the behavior under test.

## Missing and Stale Samples

Use `_` for a missing sample and `stale` when the test must model Prometheus staleness semantics explicitly.

### Missing Sample (`_`)

A missing sample means the series has no value at that specific timestamp. The series still exists for evaluation purposes -- it simply has a gap.

```yaml
- series: 'up{job="api",instance="api-1"}'
  values: '1 1 1 _ 1 1'
```

This sequence has one gap at position 3, then the series continues afterward.

Effect on PromQL functions:

```yaml
input_series:
  - series: 'http_requests_total{job="api"}'
    values: '100 _ 120 140'
```

`rate()` and `increase()` interpolate across the gap. The gap does not cause the series to disappear from results; at sample 4, `rate(http_requests_total[2m])` sees roughly `(140 - 100) / 4m = 10/s`.

### Stale Marker (`stale`)

The `stale` marker causes the series to be treated as nonexistent from that point onward. This is different from `_`:

```yaml
- series: 'up{job="api",instance="api-1"}'
  values: '1 1 1 1 stale'
```

This sequence marks the series stale at position 5.

Effect on PromQL functions:

After the stale marker, instant queries return no sample for this series, range vectors exclude it, `absent(up{instance="api-1"})` returns a sample, and `count(up)` decreases because the series is gone.

Example testing staleness-dependent alert:

```yaml
rule_files:
  - alerts/target-down.rules.yaml

tests:
  - interval: 30s
    input_series:
      - series: 'up{job="api",instance="api-1"}'
        values: '1 1 1 1 stale'
    alert_rule_test:
      - eval_time: 1m
        alertname: TargetDown
        exp_alerts: []
      - eval_time: 7m
        alertname: TargetDown
        exp_alerts:
          - exp_labels:
              severity: critical
              instance: api-1
```

Use when: the blocker is showing the difference between a temporary missing scrape and a series that has become stale for evaluation purposes.

## Series Not in input_series

When an expression references a metric name that has no corresponding `input_series` entry, promtool treats it as an empty result set. This is useful for testing `absent()` behavior but can also cause confusing failures if accidental.

**Intentional absent test:**

```yaml
tests:
  - input_series: []
    alert_rule_test:
      - eval_time: 5m
        alertname: NoTargetForJob
        exp_alerts:
          - exp_labels:
              severity: warning
```

**Accidental missing series -- common failure mode:**

```yaml
input_series:
  - series: 'http_requests_total{job="api",status="500"}'
    values: '0+10x20'
```

This is broken when the rule denominator needs `http_requests_total{job="api",status="200"}`. The missing series can produce division by zero or an empty result, so add the denominator series explicitly.

## Native Histogram Fixtures

Use native histogram notation only when the rule under test actually depends on histogram-native behavior. Do not complicate a basic alert test just because the source metric is histogram-shaped elsewhere.

### Basic Native Histogram Sample

```yaml
input_series:
  - series: 'http_request_duration_seconds{job="api"}'
    values: '{{schema:0 count:10 sum:25.0 buckets:[3 5 2]}} {{schema:0 count:15 sum:40.0 buckets:[5 7 3]}}'
```

Schema defines the bucket resolution:

| Schema | Bucket width formula |
| --- | --- |
| -4 | 2^-4 = 0.0625 |
| -3 | 0.125 |
| -2 | 0.25 |
| -1 | 0.5 |
| 0 | 1 |
| 1 | 2 |
| 2 | 4 |
| 3 | 8 |

Schema 0 with buckets `[3 5 2]` means:

- Bucket [0, 1): 3 observations
- Bucket [1, 2): 5 observations
- Bucket [2, +inf): 2 observations
- Total count: 10, sum: 25.0

### Testing histogram_quantile with Native Histograms

```yaml
rule_files:
  - alerts/latency.rules.yaml

tests:
  - interval: 1m
    input_series:
      - series: 'http_request_duration_seconds{job="api"}'
        values: >-
          {{schema:0 count:100 sum:200 buckets:[10 20 30 40]}}
          {{schema:0 count:100 sum:250 buckets:[5 15 35 45]}}
          {{schema:0 count:100 sum:300 buckets:[0 10 40 50]}}
          {{schema:0 count:100 sum:350 buckets:[0 5 45 50]}}
          {{schema:0 count:100 sum:400 buckets:[0 0 50 50]}}
          {{schema:0 count:100 sum:450 buckets:[0 0 40 60]}}
          {{schema:0 count:100 sum:500 buckets:[0 0 30 70]}}
          {{schema:0 count:100 sum:550 buckets:[0 0 20 80]}}
          {{schema:0 count:100 sum:600 buckets:[0 0 10 90]}}
          {{schema:0 count:100 sum:650 buckets:[0 0 5 95]}}
          {{schema:0 count:100 sum:700 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:750 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:800 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:850 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:900 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:950 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:1000 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:1050 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:1100 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:1150 buckets:[0 0 0 100]}}
          {{schema:0 count:100 sum:1200 buckets:[0 0 0 100]}}
    alert_rule_test:
      - eval_time: 16m
        alertname: HighLatencyP99
        exp_alerts:
          - exp_labels:
              severity: warning
```

Use when: the rule depends on histogram-native structure rather than a float-only approximation.

## Counter Reset Simulation

Prometheus counters reset when a process restarts. Simulate this in fixtures by including a decreasing value, which promtool interprets as a counter reset:

```yaml
- series: 'process_cpu_seconds_total{job="api"}'
  values: '100+10x5 50+10x5'
```

This sequence increases normally, drops to simulate a process restart, then increases again. `rate()` and `increase()` handle the reset between sample 5 and 6.

Use when: the alert depends on `rate()` or `increase()` over counters and you need to verify reset handling.

## Float Precision Edge Cases

Floating-point arithmetic in PromQL can produce values like `5.000000000001` instead of exactly `5`. Two strategies:

**Strategy 1: Use `fuzzy_compare` at the top level**

```yaml
fuzzy_compare: true

tests:
  - alert_rule_test:
      - eval_time: 16m
        alertname: RatioAlert
        exp_alerts:
          - exp_labels:
              severity: page
```

### Strategy 2: Round in the rule expression

```yaml
expr: 5 < round(100 * errors / total, 0.001)
```

Apply rounding in the rule file rather than the test file when the production expression should also avoid boundary precision noise.

See [`./test-execution-controls.md`](./test-execution-controls.md) for more on `fuzzy_compare`.

## Review Questions

- Is the edge-case fixture proving a real regression risk?
- Would a simpler float-series fixture prove the same behavior more clearly?
- Are stale or missing samples part of the alert contract or just incidental noise?
- Does the fixture have enough samples to cover the latest `eval_time` in every assertion?
- Is each native histogram bucket count consistent with the declared `count` and `sum`?
