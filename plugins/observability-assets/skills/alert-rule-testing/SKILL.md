---
metadata:
  reference:
    Prometheus:
      version: 3.14.0
      license: Apache-2.0
      url:
        - https://prometheus.io/docs/prometheus/3.14/configuration/unit_testing_rules/
        - https://prometheus.io/docs/prometheus/3.14/querying/basics/
        - https://prometheus.io/docs/prometheus/3.14/querying/functions/
        - https://github.com/prometheus/prometheus/blob/v3.14.0/cmd/promtool/unittest.go
    Prometheus releases:
      url: https://github.com/prometheus/prometheus/releases
    Native Histograms:
      license: Apache-2.0
      url: https://prometheus.io/docs/specs/native_histograms/
name: alert-rule-testing
description: >-
  Use for promtool test rules fixtures, alert lifecycle timing, expected labels and annotations, and alert regression review.
---

# Alert Rule Testing

Write and review `promtool test rules` files that lock alert behavior before a rule ships.
Use the real rule file and fixtures that protect the behavior under change.

## Task Focus

- Inspect the real rule and existing tests before adding fixtures.
- Select normal, pending, firing, recovery, or label-contract coverage according to the changed behavior and regression risk.
- Choose `input_series` and `eval_time` values that demonstrate the intended state around relevant `for` and `keep_firing_for` boundaries.
- For each firing assertion, include the full emitted label and annotation maps.
- Extend focused existing tests instead of duplicating coverage or adding a full lifecycle fixture for every edit.
- Investigate failures against the intended rule behavior before changing fixtures or expected results.

## Test File Schema

The schema also supports per-test-group `external_labels` and `external_url`, which the tables below omit.

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
        values: '0+4x20'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+96x20'
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

Each entry in `input_series` defines one synthetic time series.
The `values` field accepts multiple notation formats.

### Counter Notation (Most Common)

Syntax: `<start>+<increment>x<steps>`

Generates `steps + 1` samples: the first sample is `start`, followed by `steps` additional samples incremented by `increment` at the configured `interval`.

For example, `0+6x20` expands to 21 samples from `0` through `120` in increments of `6`.

```yaml
- series: 'http_requests_total{job="api",status="500"}'
  values: '0+6x20'

- series: 'node_cpu_seconds_total{mode="idle"}'
  values: '1000+50x10'

- series: 'up{job="api"}'
  values: '1+0x5'

```

The examples expand to values from `0` through `120` in increments of `6`, values from `1000` through `1500` in increments of `50`, and six constant `1` samples.

Use when: modeling counter-like metrics that increase monotonically over time.

### Explicit Value Syntax

Space-separated literal sample values:

```yaml
- series: 'temperature_celsius{room="server-room"}'
  values: '22.5 23.0 23.5 24.0 25.0'

- series: 'memory_usage_percent{host="db-1"}'
  values: '60 62 65 70 78 85'

```

The first series has five explicit samples.
The second mixes integer-looking and float-compatible values.

Use when: you need precise control over individual sample values, such as testing threshold boundaries exactly.

### Stale Markers

Use `_` for a missing sample and `stale` to mark a series as stale from that point onward:

```yaml
- series: 'up{job="api",instance="api-1"}'
  values: '1 1 1 _ 1 stale'

- series: 'http_requests_total{job="api"}'
  values: '10 12 14 stale'

```

The first sequence has one missing sample at position 3, then a stale marker from position 5 onward.
The second becomes stale after its first three samples.

Staleness semantics in tests mirror Prometheus production behavior:

- `_` produces a missing sample at that timestamp (the series has no value).
- `stale` marks the series as stale.
  Subsequent evaluations treat it as if it does not exist until a new non-stale sample appears.
- A stale series is excluded from range vector calculations and does not appear in instant query results.

Use when: testing scrape gaps, target downtime, or staleness-dependent expressions such as `absent()` or `up == 0`.

### Native Histogram Notation

Minimal native histogram sample syntax for current `promtool test rules`:

```yaml
- series: 'http_request_duration_seconds{job="api"}'
  values: '{{schema:1 count:10 sum:2.5 buckets:[1 3 6]}} {{schema:1 count:12 sum:3.0 buckets:[1 4 7]}}'

```

Each histogram sample is enclosed in `{{ }}`.
The minimal fields shown above are enough for compact examples, but promtool supports additional optional fields such as `z_bucket`, `z_bucket_w`, `offset`, `n_buckets`, `n_offset`, `counter_reset_hint`, and `custom_values`.

| Field | Type | Description |
| --- | --- | --- |
| `schema` | integer | Native histogram schema. Valid values are `-53` for custom buckets or `-4` through `8` for standard schemas. |
| `count` | non-negative float | Total observation count for this sample. |
| `sum` | float | Sum of all observations. |
| `buckets` | list of non-negative floats | Positive bucket counts represented as absolute counts. |

Use when: the rule under test depends on histogram-native structure rather than a float-only approximation.
See [`./references/fixture-edge-cases.md`](./references/fixture-edge-cases.md) for more detail.

## alert_rule_test Complete Schema

Each entry in `alert_rule_test:` asserts the expected alert state at a requested time.
The assertion observes the most recent scheduled rule evaluation at or before that time.
`promql_expr_test` instead evaluates its expression at the exact requested time.

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `eval_time` | duration | yes | -- | Time offset from test start. Alert state is checked at the latest scheduled evaluation at or before this time. |
| `alertname` | string | yes | -- | Name of the alert to assert. Must match an `alert:` field in the loaded rule file. |
| `exp_alerts` | list | yes | `[]` | Expected alert instances at this eval time. Empty list means no firing alerts. |

### exp_alerts Entry Schema

Each entry in `exp_alerts:` describes one expected firing alert instance:

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `exp_labels` | map | no | `{}` | Full expanded label set expected for this firing alert instance, excluding `__name__` and the top-level `alertname`. |
| `exp_annotations` | map | no | `{}` | Full rendered annotation set expected for this firing alert instance. |

Important semantics:

- `alertname` is NOT automatically added to `exp_labels`.
  - If your rule sets `alertname` via labels (unusual), include it explicitly.
- `exp_labels` and `exp_annotations` are exact expected maps for the alert instance.
  - Include every label and annotation the rule emits and do not rely on extra actual keys being ignored.
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
| `value` | number | for float samples | -- | Expected numeric value of the sample. |
| `histogram` | string | for histogram samples | -- | Expected native histogram in promtool series notation. A non-empty value makes `value` ignored. |

For a float sample, provide `labels` and `value`.
For a native histogram, provide `labels` and one `histogram` descriptor in the same notation used by input series.

Float and histogram expected samples:

```yaml
promql_expr_test:
  - expr: sum(increase(http_requests_total{job="api"}[5m]))
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 500

  - expr: http_request_duration_seconds{job="api"}
    eval_time: 16m
    exp_samples:
      - labels: '{job="api"}'
        histogram: '{{schema:1 count:12 sum:3.0 buckets:[1 4 7]}}'
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

```sh
promtool test rules alerts/api-errors.test.yaml

```

Use this command to validate an existing or edited test file.
Use the deployment's `promtool` when available.
Before obtaining a new binary, check the official Prometheus releases for the latest stable version compatible with the target server and rule features.
If `promtool` is unavailable, report validation as blocked instead of claiming the rule test is ready.

Run only the test groups within a file whose `name` field matches the `--run` flag, interpreted as a regular expression (the flag has no short form and may be repeated to match several groups):

```sh
promtool test rules --run api-error-rate-firing alerts/api-errors.test.yaml

```

Run all test files matched by a shell glob:

```sh
promtool test rules tests/*.yaml

```

`promtool test rules` accepts test-file paths, not a directory path.
The shell expands the glob to positional file arguments.

### Test Output Interpretation

Successful run:

```text
PASS  alerts/api-errors.test.yaml   0.003s
PASS  alerts/latency.test.yaml      0.002s

```

Failure output example:

```text
FAIL  alerts/api-errors.test.yaml   0.004s

Expected alert Api5xxRatioAbove5Percent to be firing but it was not firing at 16m0s
Test: api-error-firing
Expr: 5 < round(100 * sum(...) / sum(...), 0.001)
EvalTime: 16m0s
Expected:
  - alertname: Api5xxRatioAbove5Percent
    labels: {severity="page", service="api"}
Actual:
  (no alerts)

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

The fixture uses low error rate samples for 0-8m, high error rate samples for 8-20m, and recovery samples for 20-28m.
The assertions then check below-threshold, pending, firing, and resolved behavior in order.

```yaml
tests:
  - name: api-error-full-lifecycle
    interval: 1m
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        values: '0+2x8 0+15x12 0+2x8'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+98x8 0+85x12 0+98x8'
    alert_rule_test:
      - eval_time: 4m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      - eval_time: 14m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      - eval_time: 20m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api
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

Use when: the annotation template itself must stay stable alongside the firing condition.

## Validate the Result

Review the affected assertions with these checks:

- The test file points at the actual rule file under review.
- `input_series` values make the intended state transition obvious.
- `eval_time` proves non-firing, pending, firing, or recovery on purpose rather than by accident.
- Expected labels and annotations match the alert contract you truly care about.
- The test suite protects real regressions without becoming an unreadable fixture dump.
- `promtool test rules` passes on the test file you intend to ship.
- Each test case has a descriptive `name` field for filtered execution.
- Alert `eval_time` values use a scheduled boundary when the exact evaluation matters; off-boundary values inspect the latest prior evaluation. PromQL assertions use the exact requested time.

Local fixture execution does not deploy rules or prove live alert delivery.
Report the tested behaviors and the exact command result before claiming those behaviors are verified.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. The recommended test file or review decision.
2. The rule file and test behaviors covered by the result.
3. Validation results, including whether `promtool test rules` ran or is blocked by missing local tooling.
4. Remaining risks, assumptions, or uncovered alert behaviors.

## References

| If the blocker is... | Read... |
| --- | --- |
| time-based test context, custom timestamps, float-comparison tolerances, or filtered test execution | [`./references/test-execution-controls.md`](./references/test-execution-controls.md) |
| stale samples, missing samples, or native histogram fixture shapes | [`./references/fixture-edge-cases.md`](./references/fixture-edge-cases.md) |

## Invariants

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
| assuming alert assertions evaluate at arbitrary off-boundary times | alert state comes from the latest scheduled rule evaluation at or before `eval_time`, while PromQL assertions use the exact time | choose a scheduled boundary when exact alert-state timing matters |
| asserting `alertname` inside `exp_labels` | `alertname` is matched by the top-level `alertname` field, not inside `exp_labels`; putting it in both places is redundant and fragile | keep `alertname` at the top level only |
| writing `input_series` with too few samples | the series ends before `eval_time` is reached, causing missing-data errors | ensure enough samples cover the latest `eval_time` in the test case |

## Scope Boundaries

- Activate this skill for:
  - `promtool test rules` authoring and review
  - alert behavior verification and regression protection
  - fixture design, eval timing, and expected alert-state checks
  - test YAML schema, input_series notation, and output interpretation
- Do not activate for:
  - full alert-rule design
  - Alertmanager routing behavior
  - Grafana dashboard authoring or provisioning
