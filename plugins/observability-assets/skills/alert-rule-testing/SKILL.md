---
name: alert-rule-testing
description: >-
  Use this skill when writing or reviewing promtool rule tests, testing alert pending/firing/resolved
  lifecycle behavior, designing input_series fixtures, placing eval_time against for windows,
  protecting alert regressions, or needing guidance on Prometheus alert-rule test authoring,
  schema, and verification.
---

# Alert Rule Testing

Write and review `promtool test rules` files that lock alert behavior before a rule ships. The common case is one test file that points at the real rule file, defines a small set of `input_series`, and proves the alert stays non-firing, becomes pending, fires after the `for` window, and resolves when the signal recovers.

## Common-Case Workflow

1. Start from the real alert rule file that must stay stable.
2. Add the smallest `input_series` set that proves normal, pending, firing, and recovery behavior.
3. Pick `eval_time` values that sit clearly before and after the alert `for` window.
4. Assert expected labels and annotations only for the alert state you actually need to protect.
5. Keep the test small enough to explain one behavior change at a time.
6. Run `promtool test rules` on the real test file before treating the rule change as safe.
7. On failure: check that `input_series` values align with the rule expression, verify `eval_time` placement against the `for` window, confirm `exp_labels` match the actual alert label set, then revise and re-run.

## Test File Schema

### Top-Level Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `rule_files` | list of strings | yes | -- | Paths to the rule files under test (relative to the test file). |
| `evaluation_interval` | duration | no | `1m` | Base evaluation interval for the test engine. |
| `group_eval_order` | list of strings | no | -- | Explicit group evaluation order when groups have dependencies. |
| `tests` | list | yes | -- | List of test cases. |
| `fuzzy_compare` | bool | no | `false` | Use approximate float comparison instead of exact equality. |

### Test Case Fields

Each entry in `tests:` supports these fields:

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `name` | string | no | auto-generated | Human-readable name for this test case. Used with `--run` filter. |
| `interval` | duration | no | top-level `evaluation_interval` | Evaluation interval for this specific test case. |
| `start_timestamp` | timestamp | no | epoch zero (`1970-01-01T00:00:00Z`) | Wall-clock start time for this test case. |
| `input_series` | list | no | `[]` | Synthetic time series injected into the test engine. |
| `alert_rule_test` | list | no | `[]` | Assertions about alert state at specific eval times. |
| `promql_expr_test` | list | no | `[]` | Assertions about PromQL expression results at specific eval times. |

Complete test file skeleton:

```yaml
rule_files:
  - alerts/api-errors.rules.yaml

evaluation_interval: 1m

tests:
  - name: api-error-rate-normal
    interval: 1m
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        values: '0+6x20'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+94x20'
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []

  - name: api-error-rate-firing
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        values: '0+10x20'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+90x20'
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api
```

## input_series Notation Formats

Each entry in `input_series` defines one synthetic time series. The `values` field accepts multiple notation formats.

### Counter Notation (Most Common)

Syntax: `<start>+<increment>x<count>`

Generates `count` samples starting from `start`, each incremented by `increment`, spaced at the configured `interval`.

```yaml
# Starts at 0, increments by 6 every interval, generates 20 samples
# Samples: 0, 6, 12, 18, 24, ..., 114
- series: 'http_requests_total{job="api",status="500"}'
  values: '0+6x20'

# Starts at 1000, increments by 50, generates 10 samples
# Samples: 1000, 1050, 1100, ..., 1450
- series: 'node_cpu_seconds_total{mode="idle"}'
  values: '1000+50x10'

# Zero increment -- flat line of constant values
# Samples: 1, 1, 1, 1, 1
- series: 'up{job="api"}'
  values: '1+0x5'
```

Use when: modeling counter-like metrics that increase monotonically over time.

### Explicit Value Syntax

Space-separated literal sample values:

```yaml
# Five explicit samples at each evaluation interval
- series: 'temperature_celsius{room="server-room"}'
  values: '22.5 23.0 23.5 24.0 25.0'

# Mix of integers and floats
- series: 'memory_usage_percent{host="db-1"}'
  values: '60 62 65 70 78 85'
```

Use when: you need precise control over individual sample values, such as testing threshold boundaries exactly.

### Stale Markers

Use `_` for a missing sample and `stale` to mark a series as stale from that point onward:

```yaml
# One missing sample at position 3, then stale from position 5 onward
- series: 'up{job="api",instance="api-1"}'
  values: '1 1 1 _ 1 stale'

# All stale after first three samples
- series: 'http_requests_total{job="api"}'
  values: '10 12 14 stale'
```

Staleness semantics in tests mirror Prometheus production behavior:
- `_` produces a missing sample at that timestamp (the series has no value).
- `stale` marks the series as stale; subsequent evaluations treat it as if it does not exist until a new non-stale sample appears.
- A stale series is excluded from range vector calculations and does not appear in instant query results.

Use when: testing scrape gaps, target downtime, or staleness-dependent expressions such as `absent()` or `up == 0`.

### Native Histogram Notation

Syntax for native histogram samples (Prometheus >= 2.40):

```yaml
- series: 'http_request_duration_seconds{job="api"}'
  values: '{{schema:1 count:10 sum:2.5 buckets:[1 3 6]}} {{schema:1 count:12 sum:3.0 buckets:[1 4 7]}}'
```

Each histogram sample is enclosed in `{{ }}` and contains:

| Field | Type | Description |
| --- | --- | --- |
| `schema` | integer | Native histogram schema (power-of-2 bucket base). |
| `count` | integer | Total observation count for this sample. |
| `sum` | float | Sum of all observations. |
| `buckets` | list of integers | Bucket counts for each schema-defined bucket. |

Use when: the rule under test depends on histogram-native structure rather than a float-only approximation. See [`./references/fixture-edge-cases.md`](./references/fixture-edge-cases.md) for more detail.

## alert_rule_test Complete Schema

Each entry in `alert_rule_test:` asserts the expected alert state at a single point in time.

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `eval_time` | duration | yes | -- | Time offset from test start at which to evaluate. Must be a multiple of the test's `interval`. |
| `alertname` | string | yes | -- | Name of the alert to assert. Must match an `alert:` field in the loaded rule file. |
| `exp_alerts` | list | yes | `[]` | Expected alert instances at this eval time. Empty list means no firing alerts. |

### exp_alerts Entry Schema

Each entry in `exp_alerts:` describes one expected firing alert instance:

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `exp_labels` | map | no | `{}` | Labels the firing alert instance MUST have. Only listed labels are checked; extra labels on the actual alert are ignored. |
| `exp_annotations` | map | no | `{}` | Annotations the firing alert instance MUST have. Checked as rendered template output. |

Important semantics:

- `alertname` is NOT automatically added to `exp_labels`. If your rule sets `alertname` via labels (unusual), include it explicitly.
- The check is subset-based: the actual alert must contain all keys/values in `exp_labels`/`exp_annotations`, but may have additional ones.
- An empty `exp_alerts: []` means "this alert should not be firing at this eval time." This covers both truly-inactive and pending states.

Example with full assertion:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts:
      - exp_labels:
          severity: page
          service: api
        exp_annotations:
          summary: API 5xx ratio is high
```

## promql_expr_test Complete Schema

Each entry in `promql_expr_test:` asserts the result of evaluating a raw PromQL expression at a specific time, independent of any alert rule.

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `expr` | PromQL | yes | -- | Expression to evaluate. |
| `eval_time` | duration | yes | -- | Time offset from test start at which to evaluate. |
| `exp_samples` | list | yes | -- | Expected result samples from the expression. |

### exp_samples Entry Schema

Each entry in `exp_samples:` describes one expected result sample:

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `labels` | string | yes | -- | Label set as a string map literal, e.g. `'{}'` or `'{job="api"}'`. |
| `value` | number | yes | -- | Expected numeric value of the sample. |

Example:

```yaml
promql_expr_test:
  # Verify intermediate recording rule output
  - expr: job:http_requests:rate5m{job="api"}
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 94

  # Verify aggregated rate calculation
  - expr: sum(rate(http_requests_total{job="api"}[5m]))
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 100
```

Use when: the blocker is an intermediate query shape rather than only the final alert state.

## Minimal Setup

Minimal test file shape:

```yaml
rule_files:
  - alerts/api-errors.rules.yaml

evaluation_interval: 1m

tests:
  - interval: 1m
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        values: '0+6x20'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+94x20'
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api
```

Use when: you need one minimal `promtool` test that exercises a real alert rule with concrete fixture data.

## First Runnable Commands or Code Shape

Start by running the exact test file that matches the rule under review:

```bash
promtool test rules alerts/api-errors.test.yaml
```

Use when: the test file already exists or has just been edited and you need the first safe validation run.
If `promtool` is unavailable, stop at a blocked validation state instead of claiming the rule test is ready.

Run a single named test case:

```bash
promtool test rules --run '^api-error-firing$' alerts/api-errors.test.yaml
```

Run all test files in a directory:

```bash
promtool test rules tests/
```

### Test Output Interpretation

Successful run:

```text
PASS  alerts/api-errors.test.yaml   0.003s
PASS  alerts/latency.test.yaml      0.002s
```

Failure output example:

```text
FAIL  alerts/api-errors.test.yaml   0.004s

# Expected alert Api5xxRatioAbove5Percent to be firing but it was not firing at 16m0s
# Test: api-error-firing
# Expr: 5 < round(100 * sum(...) / sum(...), 0.001)
# EvalTime: 16m0s
# Expected:
#   - alertname: Api5xxRatioAbove5Percent
#     labels: {severity="page", service="api"}
# Actual:
#   (no alerts)
```

Common failure messages and their causes:

| Failure message | Likely cause | Fix |
| --- | --- | --- |
| "expected alert ... to be firing but it was not firing" | `eval_time` before `for` window completes, or expression never crosses threshold | Increase `eval_time` past `for` duration, or raise `input_series` values |
| "expected alert ... not to be firing but it was firing" | `eval_time` after `for` window completes unexpectedly | Decrease `eval_time`, lower `input_series` values, or add recovery samples |
| "expected label ... not found" | `exp_labels` contains a key the alert never emits | Remove the key from `exp_labels` or add it to the rule's `labels` block |
| "unexpected alert ... firing" | A different alert instance fired than expected | Add an `exp_alerts` entry for the unexpected alert, or constrain `input_series` so it does not trigger |
| "sample mismatch" (promql_expr_test) | Computed value differs from `exp_samples.value` | Check floating-point precision, use `fuzzy_compare: true`, or verify the expression matches the rule |

## Ready-to-Adapt Templates

Below-threshold check -- prove the alert does not fire during normal traffic:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: you need a fast regression check for the non-firing case.

Pending check -- prove the rule crossed the threshold but has not satisfied `for` yet:

```yaml
alert_rule_test:
  - eval_time: 8m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: the alert has a `for` window and you need to prove it is not firing too early.

Pending-state interpretation rule:

- `promtool test rules` does not expose a separate pending assertion type in `exp_alerts`
- Prove pending behavior by pairing an empty `exp_alerts: []` result with an `eval_time` where the threshold is crossed but the `for` window is not yet complete
- Prove true non-firing behavior with another empty `exp_alerts: []` check where the threshold was never crossed

Firing check -- prove expected labels or annotations once the `for` window completes:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts:
      - exp_labels:
          severity: page
          service: api
```

Use when: you need one stable contract for the actual firing state.

Resolved check -- prove the alert stopped firing after recovery:

```yaml
alert_rule_test:
  - eval_time: 25m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: an earlier eval time already proved the alert fired, and a later eval time now proves the alert cleared after the input series recovered.

Resolved-state interpretation rule:

- `promtool test rules` shows resolved behavior by returning no expected firing alerts at a later `eval_time`
- Distinguish resolved from never-firing by pairing this check with an earlier firing assertion on the same alert
- If the rule uses `keep_firing_for`, place the resolved check after that hold-open window rather than immediately after the signal drops

Full lifecycle test covering all four states:

```yaml
tests:
  - name: api-error-full-lifecycle
    interval: 1m
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        # 0-8m: low error rate (below threshold)
        # 8-20m: high error rate (above threshold)
        # 20-28m: back to low (recovery)
        values: '0+2x8 0+15x12 0+2x8'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+98x8 0+85x12 0+98x8'
    alert_rule_test:
      # Below threshold -- not firing
      - eval_time: 4m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      # Threshold crossed but for=10m not yet satisfied -- pending (not firing)
      - eval_time: 14m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      # for=10m satisfied -- firing with expected labels
      - eval_time: 20m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api
      # Recovered -- resolved (not firing)
      - eval_time: 26m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
```

Use when: you need one test that covers the entire inactive -> pending -> firing -> resolved lifecycle.

PromQL expression check -- verify one intermediate query result directly:

```yaml
promql_expr_test:
  - expr: sum(increase(http_requests_total{job="api"}[5m]))
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 500
```

Use when: the blocker is an intermediate query shape rather than only the final alert state.

Annotation content check -- verify rendered template output:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts:
      - exp_labels:
          severity: page
          service: api
        exp_annotations:
          summary: API 5xx ratio is high
```

Use when: the annotation template itself must stay stable, not just the firing condition.

## Validate the Result

Validate the common case with these checks:

- The test file points at the actual rule file under review.
- `input_series` values make the intended state transition obvious.
- `eval_time` proves non-firing, pending, firing, or recovery on purpose rather than by accident.
- Expected labels and annotations match the alert contract you truly care about.
- The test suite protects real regressions without becoming an unreadable fixture dump.
- `promtool test rules` passes on the test file you intend to ship.
- Each test case has a descriptive `name` field for filtered execution.
- `eval_time` values are multiples of the test's `interval` (non-multiples cause confusing failures).

## Output contract

Return:

1. The recommended test file or review decision.
2. The rule file and test behaviors covered by the result.
3. Validation results, including whether `promtool test rules` ran or is blocked by missing local tooling.
4. Remaining risks, assumptions, or uncovered alert behaviors.

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| time-based test context, custom timestamps, float-comparison tolerances, or filtered test execution | [`./references/test-execution-controls.md`](./references/test-execution-controls.md) |
| stale samples, missing samples, or native histogram fixture shapes | [`./references/fixture-edge-cases.md`](./references/fixture-edge-cases.md) |

## Invariants

- MUST keep the ordinary alert-rule test authoring path understandable from this file alone.
- MUST test the real rule file, not a disconnected copy.
- MUST choose `eval_time` values deliberately.
- SHOULD keep fixtures small enough to explain one behavior at a time.
- SHOULD add explicit regression coverage for alerts whose timing or labels must stay stable.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| testing only the firing case | early firing or silent non-firing regressions go unnoticed | add non-firing and pending coverage where timing matters |
| choosing arbitrary `eval_time` values | the test passes or fails for unclear reasons | place eval times deliberately around the `for` boundary |
| copying labels into `exp_labels` that the alert never emits | tests fail for the wrong reason | assert only the labels that belong to the real alert contract |
| building one giant fixture for many behaviors | review becomes difficult and regressions are harder to isolate | keep each test focused on one behavior or transition |
| using `eval_time` values that are not multiples of `interval` | promtool evaluates at interval boundaries; off-boundary times produce confusing results | always use multiples: `4m`, `8m`, `16m` for `interval: 1m` |
| asserting `alertname` inside `exp_labels` | `alertname` is matched by the top-level `alertname` field, not inside `exp_labels`; putting it in both places is redundant and fragile | keep `alertname` at the top level only |
| writing `input_series` with too few samples | the series ends before `eval_time` is reached, causing missing-data errors | ensure enough samples cover the latest `eval_time` in the test case |

## Scope Boundaries

- Activate this skill for:
  - `promtool test rules` authoring and review
  - alert behavior verification and regression protection
  - fixture design, eval timing, and expected alert-state checks
  - test YAML schema, input_series notation, and output interpretation
- Do not activate for:
  - full alert-rule design (use `prometheus-alert-rules`)
  - Alertmanager routing behavior
  - Grafana dashboard authoring or provisioning
