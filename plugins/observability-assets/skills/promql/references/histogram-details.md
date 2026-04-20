---
title: "PromQL Histogram Details"
description: "Open this when working with histogram_quantile, histogram_fraction, native vs classic histograms, bucket interpolation, or monotonicity issues."
---

Use this reference when the query involves histograms and the blocker is understanding interpolation behavior, native vs classic differences, or debugging `histogram_quantile` results.

## Classic Histograms vs Native Histograms

| Aspect | Classic (bucket-based) | Native |
| --- | --- | --- |
| Metric names | `metric_bucket`, `metric_count`, `metric_sum` | Single metric name |
| Label requirement | Must have `le` label on buckets | No special labels needed |
| Bucket representation | Separate time series per bucket | Single sample with bucket schema |
| Aggregation | Must include `le` in `by (...)` clause | Normal aggregation; no `le` needed |
| Interpolation within bucket | Linear (uniform distribution assumed) | Exponential for standard buckets, linear for custom/zero |
| Upper bound of highest bucket | MUST be `+Inf` (else NaN) | Schema-defined |

## Classic Histogram: Required Structure

Buckets must be monotonically non-decreasing. The `_bucket` series must have an `le="+Inf"` bucket.

```
http_request_duration_seconds_bucket{le="0.1"}   => 10
http_request_duration_seconds_bucket{le="0.5"}   => 30
http_request_duration_seconds_bucket{le="1"}     => 50
http_request_duration_seconds_bucket{le="+Inf"}  => 50
```

If the highest bucket is not `+Inf`, `histogram_quantile` returns NaN.

## Aggregation Pattern Difference

**Classic** -- must preserve `le` label:

```promql
histogram_quantile(
  0.99,
  sum by (job, le) (rate(http_request_duration_seconds_bucket[5m]))
)
```

**Native** -- normal aggregation:

```promql
histogram_quantile(
  0.99,
  sum by (job) (rate(http_request_duration_seconds[5m]))
)
```

Forgetting `le` in the classic `by` clause is the most common histogram aggregation mistake. It collapses all buckets into one, making quantiles meaningless.

## Interpolation Behavior

### Classic histograms and custom-boundary native buckets

Assumes **linear interpolation** (uniform distribution of observations within the bucket).

Given buckets `{le="1": 50, le="5": 70}` and asking for phi=0.5 (median):
- The quantile falls between 1 and 5.
- 50 observations are at or below 1. 20 observations are between 1 and 5.
- The median is the 35th observation (50% of 70).
- Linear interpolation places it at: `1 + (5 - 1) * (35 - 50) / (70 - 50)` = `1 + 4 * (-15/20)` = `1 - 3` = `-2`.

This example shows why picking boundaries far from actual bucket edges produces unreliable results with classic histograms.

### Standard exponential native buckets

Uses **exponential interpolation**: assumes observations would uniformly populate a hypothetical higher-resolution histogram. This produces more accurate results for skewed distributions but is computationally more complex.

### Zero bucket behavior

A zero bucket with finite width containing only positive observations assumes no negative observations, and vice versa. This affects quantiles that fall into or near zero.

## Monotonicity Enforcement

`histogram_quantile` enforces monotonically increasing bucket counts:

1. Relative differences smaller than 1e-12 of the sum of both buckets are treated as floating-point noise and ignored.
2. If counts are still non-monotonic after this adjustment, they are raised to the previous bucket's value.
3. This correction triggers an info-level annotation: `input to histogram_quantile needed to be fixed for monotonicity`.

If you see this annotation, investigate the data source for invalid counter resets or ingestion anomalies.

## `histogram_fraction` Edge Cases

- Boundaries that do not align to bucket boundaries produce estimated (interpolated) fractions.
- With classic histograms, picking boundaries far from any bucket edge can produce large error margins.
- For fraction calculations on classic histograms, operating directly on bucket series is often more robust than using `histogram_fraction`.
- `+Inf` and `-Inf` are valid boundary values.
- With native histograms and standard exponential buckets, `NaN` observations are considered outside all buckets. `histogram_fraction(-Inf, +Inf, b)` returns the fraction of non-NaN observations (may be < 1).

## Negative Histograms (Intermediate Results Only)

Applying unary minus or subtracting gauge histograms can produce negative bucket populations or negative observation counts. These intermediate results:

- Cannot be ingested via any exchange format (exposition, remote-write, OTLP).
- Cannot be stored as recording rule results (evaluation will fail).
- Should only exist transiently within a query expression.

Function-family tradeoffs and general query patterns are covered in [`../SKILL.md`](../SKILL.md). Use this reference for histogram-specific interpolation, aggregation rules, and debugging guidance only.
