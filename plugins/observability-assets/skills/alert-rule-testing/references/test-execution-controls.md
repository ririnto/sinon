---
title: Alert Rule Test Execution Controls
description: "Open this when custom test timestamps, fuzzy comparison, filtered test execution, group evaluation order, or time precision edge cases are the blocker."
---

Use this reference when the main test shape is already correct, but execution controls or time context still need work.

## Execution Controls

### Top-Level Controls

```yaml
evaluation_interval: 1m
fuzzy_compare: true

tests:
  - name: api-error-rate
    start_timestamp: 2026-01-01T00:00:00Z
```

| Control | Type | Default | Effect |
| --- | --- | --- | --- |
| `evaluation_interval` | duration | `1m` | Base interval between evaluation cycles. All `eval_time` values must be multiples of this. |
| `fuzzy_compare` | bool | `false` | When true, uses approximate float comparison (tolerance ~1e-6) instead of exact equality for sample value assertions. |
| `start_timestamp` | timestamp (per-test) | epoch zero | Wall-clock start time. Affects functions that depend on absolute time, such as `time()`, `month()`, `hour()`. |

### Per-Test Case Controls

```yaml
tests:
  - name: my-test
    interval: 30s           # override evaluation_interval for this test only
    start_timestamp: 2026-06-15T12:00:00Z
```

Use these controls when:

- Test time must start from a deliberate timestamp.
- Floating-point comparisons are close enough that strict equality is noisy.
- You need a finer or coarser evaluation granularity than the global default.

### Group Evaluation Order

When alert rules consume recording rules, the recording rule's group must evaluate first. Use `group_eval_order` to enforce this:

```yaml
rule_files:
  - rules/api.rules.yaml

# Without explicit order, promtool may evaluate groups in file declaration order,
# but explicit ordering is safer when cross-group dependencies exist
group_eval_order:
  - api-recording   # must evaluate first -- produces job:http_requests:rate5m
  - api-alerts      # consumes job:http_requests:rate5m

tests:
  - input_series:
      - series: 'http_requests_total{job="api"}'
        values: '0+100x20'
    alert_rule_test:
      - eval_time: 10m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
```

Use when: the rule file contains multiple groups and alerts in one group depend on recording rules in another.

`start_timestamp`, `fuzzy_compare`, `group_eval_order`, and per-test `interval` are version-sensitive features. Verify the deployed Prometheus/promtool version supports them before relying on these controls.

## Filtered Execution

Run one named subset of tests during focused iteration:

```bash
promtool test rules --run '^api-error-rate$' alerts/api-errors.test.yaml
```

The `--run` argument is a regex matched against test case `name` fields. Examples:

```bash
# Run all tests with names starting with "api-error"
promtool test rules --run '^api-error' tests/

# Run all tests with "firing" in the name
promtool test rules --run 'firing' tests/

# Run all tests except "slow" ones
promtool test rules --run '(?!.*slow)' tests/
```

Use when: you need to run one named subset of tests during focused iteration.

## Time Precision and eval_time Alignment

`eval_time` MUST be a multiple of the effective evaluation interval. Misaligned times produce confusing results because Prometheus evaluates at interval boundaries, not at arbitrary timestamps.

**Correct alignment (interval: 1m):**

```yaml
tests:
  - interval: 1m
    # These are all valid: multiples of 1m
    alert_rule_test:
      - eval_time: 0m     # initial evaluation
      - eval_time: 5m     # after 5 evaluations
      - eval_time: 10m    # exactly at the for boundary
      - eval_time: 16m    # well past the for boundary
```

**Misaligned (will cause problems):**

```yaml
tests:
  - interval: 1m
    # These are NOT clean multiples of 1m
    alert_rule_test:
      - eval_time: 30s    # falls between evaluation points
      - eval_time: 7m30s  # falls between evaluation points
```

When `eval_time` does not land on an evaluation boundary, promtool evaluates at the last boundary before the requested time. An `eval_time: 7m30s` with `interval: 1m` actually evaluates at `7m`. This can make tests appear to fail or pass for unclear reasons.

Rule of thumb: always use integer multiples of the interval as `eval_time`.

## Fuzzy Compare Behavior

When `fuzzy_compare: true` is set, promtool uses approximate comparison for numeric assertions instead of exact equality. The tolerance is typically around 1e-6 relative to the expected value.

**When to use it:**

- The expression involves division of rates (`rate(a) / rate(b)`) where floating-point rounding makes exact matches unlikely.
- The expression uses `histogram_quantile()` which has inherent approximation error.
- Tests flake intermittently between runs due to floating-point differences across platforms.

**When NOT to use it:**

- Testing exact threshold boundaries where a difference of 0.000001 changes the semantic result.
- Integer-valued expressions (counters, `count()`, etc.) where exact comparison is reliable.
- You want to catch real regressions that manifest as small value drifts.

Example showing the difference:

```yaml
# WITHOUT fuzzy_compare -- this FAILS if actual value is 5.000000000001
promql_expr_test:
  - expr: sum(rate(http_requests_total{status="500"}[5m])) / sum(rate(http_requests_total[5m]))
    eval_time: 10m
    exp_samples:
      - labels: '{}'
        value: 0.05

# WITH fuzzy_compare: true at top level -- this PASSES
# because 0.0500000000001 is "close enough" to 0.05
```

## start_timestamp Use Cases

Most tests work fine with the default epoch-zero start time. Set `start_timestamp` explicitly when:

**Testing time-dependent template rendering:**

```yaml
tests:
  - name: hour-based-routing-check
    start_timestamp: 2026-06-15T03:00:00Z   # 3 AM
    input_series:
      - series: 'error_count{job="batch"}'
        values: '100+10x5'
    alert_rule_test:
      - eval_time: 2m
        alertname: BatchJobFailed
        exp_alerts:
          - exp_annotations:
              summary: >-
                Batch job failed during off-hours run at 03:02 UTC
```

**Verifying behavior across daylight saving transitions (rare):**

```yaml
tests:
  - name: dst-transition
    start_timestamp: 2026-03-08T01:59:00Z   # just before spring-forward
    ...
```

## Review Questions

- Does the test really need a custom timestamp or would relative time be clearer?
- Is fuzzy comparison hiding a real query regression?
- Would a smaller targeted test be clearer than relying on `--run`?
- Are all `eval_time` values clean multiples of the effective interval?
- Does the `group_eval_order` match the actual dependency graph between rule groups?
