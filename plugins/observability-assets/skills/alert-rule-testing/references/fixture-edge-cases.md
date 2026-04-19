---
title: "Alert Rule Fixture Edge Cases"
description: "Open this when stale samples, missing samples, or native histogram fixtures are the blocker."
---

# Alert Rule Fixture Edge Cases

Use this reference when ordinary counter or gauge sequences are no longer enough to express the behavior under test.

## Missing and Stale Samples

- use `_` for a missing sample
- use `stale` when the test must model Prometheus staleness semantics explicitly

Example:

```yaml
input_series:
  - series: 'up{job="api",instance="api-1"}'
    values: '1 1 _ stale'
```

Use when: the blocker is showing the difference between a temporary missing scrape and a series that has become stale for evaluation purposes.

## Native Histogram Fixtures

Use native histogram notation only when the rule under test actually depends on histogram-native behavior. Do not complicate a basic alert test just because the source metric is histogram-shaped elsewhere.

Example:

```yaml
input_series:
  - series: 'http_request_duration_seconds{job="api"}'
    values: '{{schema:1 count:10 sum:2.5 buckets:[1 3 6]}} {{schema:1 count:12 sum:3.0 buckets:[1 4 7]}}'
```

Use when: the rule depends on histogram-native structure rather than a float-only approximation.

## Review Questions

- is the edge-case fixture proving a real regression risk
- would a simpler float-series fixture prove the same behavior more clearly
- are stale or missing samples part of the alert contract or just incidental noise
