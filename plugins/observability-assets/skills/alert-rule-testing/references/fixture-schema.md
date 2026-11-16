# Fixture Schema and Sample Notation

## Test File Schema

The schema also supports per-test-group `external_labels` and `external_url`, which the tables below omit.

### Top-Level Fields

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `rule_files` | list of strings | yes | -- | Paths to the rule files under test (relative to the test file). |
| `evaluation_interval` | duration | no | `1m` | Global cadence for rule evaluations in each test case. |
| `group_eval_order` | list of strings | no | -- | Explicit group evaluation order when groups have dependencies. |
| `tests` | list | yes | -- | List of test cases. |
| `fuzzy_compare` | bool | no | `false` | Use approximate float comparison instead of exact equality. |

### Test Case Fields

Each entry in `tests:` supports these fields:

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `name` | string | no | auto-generated | Human-readable name for this test case. Used with `--run` filter. |
| `interval` | duration | no | top-level `evaluation_interval` | Spacing between input samples for this test case. It does not change the rule evaluation cadence. |
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
See [`./fixture-edge-cases.md`](./fixture-edge-cases.md) for more detail.

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
- An empty `exp_alerts: []` means "this alert should not be firing at this eval time."
  This covers both truly-inactive and pending states.

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
