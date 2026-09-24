---
description: >-
  Open this when custom test timestamps, fuzzy comparison, filtered test execution, group evaluation order, or time precision edge cases are the blocker.
---

# Alert Rule Test Execution Controls

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
| `evaluation_interval` | duration | `1m` | Base interval between evaluation cycles. Alert assertions observe the latest scheduled evaluation at or before `eval_time`. |
| `fuzzy_compare` | bool | `false` | When true, effectively ignores differences in the last bit of the mantissa for sample value assertions. |
| `start_timestamp` | timestamp (per-test) | epoch zero | Wall-clock start time. Affects functions that depend on absolute time, such as `time()`, `month()`, `hour()`. |

### Per-Test Case Controls

```yaml
tests:
  - name: my-test
    interval: 30s
    start_timestamp: 2026-06-15T12:00:00Z
```

The per-test `interval` overrides the top-level `evaluation_interval` for that test only.

Use these controls when:

- Test time must start from a deliberate timestamp.
- Floating-point comparisons are close enough that strict equality is noisy.
- You need a finer or coarser evaluation granularity than the global default.

### Group Evaluation Order

When alert rules consume recording rules, the recording rule's group must evaluate first.
Use `group_eval_order` to enforce this:

```yaml
rule_files:
  - rules/api.rules.yaml

group_eval_order:
  - api-recording
  - api-alerts

tests:
  - input_series:
      - series: 'http_requests_total{job="api"}'
        values: '0+100x20'
    alert_rule_test:
      - eval_time: 10m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
```

Without explicit order, promtool may evaluate groups in file declaration order, but explicit ordering is safer when cross-group dependencies exist.
In this example, `api-recording` must evaluate first because it produces `job:http_requests:rate5m`, and `api-alerts` consumes that recording rule.

Use when: the rule file contains multiple groups and alerts in one group depend on recording rules in another.

The current `promtool test rules` schema includes `start_timestamp`, `fuzzy_compare`, `group_eval_order`, and per-test `interval`.
If the target repository runs an older `promtool`, verify support before relying on these controls.

## Focused Execution

`promtool test rules` runs every test group in the files you pass by default.
Use `--run <regex>` to run only test groups whose `name` matches the regular expression.
The flag is repeatable.
Use `--run` with the existing group name during iteration instead of splitting files only to filter execution.

```sh
promtool test rules alerts/api-errors.test.yaml
```

Use when: you need to iterate on one test group without running the whole suite.

## Alert Evaluation Timing

Alert assertions observe state from the most recent scheduled evaluation at or before the requested `eval_time`.
PromQL expression assertions evaluate at the exact requested time, even when it falls between scheduled rule evaluations.

### Scheduled rule evaluations (interval: 1m)

```yaml
tests:
  - interval: 1m
    alert_rule_test:
      - eval_time: 0m
      - eval_time: 5m
      - eval_time: 10m
      - eval_time: 16m
```

Each value lands on a scheduled evaluation: the initial evaluation, after 5 evaluations, at the `for: 10m` boundary, and well past that boundary.

### Off-boundary alert assertions

```yaml
tests:
  - interval: 1m
    alert_rule_test:
      - eval_time: 30s
      - eval_time: 7m30s
```

`30s` falls between evaluations and observes the initial state at `0m`.
`7m30s` observes the alert state from the scheduled evaluation at `7m`.

Choose a scheduled boundary when the assertion must inspect that exact rule evaluation.
An off-boundary alert assertion deliberately observes the latest earlier evaluation, while a PromQL expression assertion uses the exact requested time.

## Fuzzy Compare Behavior

When `fuzzy_compare: true` is set, promtool effectively ignores differences in the last bit of the mantissa.

### When to use it

- The expression involves division of rates (`rate(a) / rate(b)`) where floating-point rounding makes exact matches unlikely.
- The expression uses `histogram_quantile()` which has inherent approximation error.
- Tests flake intermittently between runs due to floating-point differences across platforms.

## When NOT to use it

- Testing exact threshold boundaries where a difference of 0.000001 changes the semantic result.
- Integer-valued expressions (counters, `count()`, etc.) where exact comparison is reliable.
- You want to catch real regressions that manifest as small value drifts.

Example showing the difference:

```yaml
promql_expr_test:
  - expr: sum(rate(http_requests_total{status="500"}[5m])) / sum(rate(http_requests_total[5m]))
    eval_time: 10m
    exp_samples:
      - labels: '{}'
        value: 0.05
```

Without `fuzzy_compare`, this fails if the actual value is `0.0500000000001`.
With `fuzzy_compare: true` at the top level, that small difference is close enough to `0.05`.

## start_timestamp Use Cases

Most tests work fine with the default epoch-zero start time.
Set `start_timestamp` explicitly when:

### Testing time-dependent template rendering

```yaml
tests:
  - name: hour-based-routing-check
    start_timestamp: 2026-06-15T03:00:00Z
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

The timestamp starts the fixture at 03:00 UTC.

## Verifying behavior across daylight saving transitions (rare)

```yaml
tests:
  - name: dst-transition
    start_timestamp: 2026-03-08T01:59:00Z
    promql_expr_test:
      - expr: hour(timestamp(vector(0)))
        eval_time: 1m
        exp_samples:
          - labels: '{}'
            value: 1
```

The timestamp lands just before a spring-forward transition.

## Review Questions

- Does the test need a custom timestamp or would relative time be clearer?
- Is fuzzy comparison hiding a real query regression?
- Does the selected test protect the changed behavior without duplicating an existing fixture?
- Do off-boundary alert assertions intentionally inspect the latest earlier evaluation, and do PromQL assertions use the intended exact time?
- Does the `group_eval_order` match the actual dependency graph between rule groups?
