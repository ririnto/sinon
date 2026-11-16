# Rule Schema

## File Structure Hierarchy

Every Prometheus rules file follows this hierarchy:

```text
groups:
  - name: <group-name>
    interval: <duration>
    limit: <int>
    rules:
      - alert: <name> | record: <name>
        expr: <promql>
        for: <duration>
        keep_firing_for: <duration>
        labels: { ... }
        annotations: { ... }

```

The top-level key is always `groups`.
It holds an ordered list of groups.
Each group requires `name` and `rules`.
`interval` and `limit` remain optional.
Each rule is either an `alert:` rule or a `record:` rule, and every rule requires `expr` while `for`, `keep_firing_for`, `labels`, and `annotations` stay optional as described in the schema tables below.

## Rule Group Schema

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `name` | string | yes | -- | Group name. Must be unique within the rules file. |
| `interval` | duration | no | global `evaluation_interval` | How often this group's rules are evaluated. |
| `limit` | integer | no | `0` (unlimited) | Maximum output series or alerts from each rule. A breach discards that rule's output and records an evaluation error. |
| `query_offset` | duration | no | global `rule_query_offset` | Evaluate the group's rules using data from this far in the past. |
| `labels` | map | no | `{}` | Labels added to every rule's output. Rule-level labels override group labels with the same name. |
| `rules` | list | yes | -- | List of alert or recording rules in this group. |

A rule's output is limited independently of other rules in the group.
If the limit is exceeded, Prometheus discards all output from that rule and records an evaluation error.
For an alerting rule, the limit breach also clears its active, pending, and inactive alert state.

Group naming convention:

Use domain-plus-purpose or domain-plus-type names, as shown below.

```yaml
groups:
  - name: api-alerts
    rules: [...]
  - name: infra-recording
    rules: [...]

```

## Alert Rule Schema

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `alert` | string | yes | -- | Alert name, stored as the `alertname` label. Names need not be unique across rules. |
| `expr` | PromQL | yes | -- | PromQL expression returning an instant vector. Each returned series creates an alert instance; an empty vector means no active instance. |
| `for` | duration | no | `0s` | Time the expression must hold true before transitioning from pending to firing. |
| `keep_firing_for` | duration | no | `0s` | Time to keep firing after the expression becomes false. Use it only after confirming the deployed Prometheus version supports it. |
| `labels` | map | no | `{}` | Labels attached to the firing alert. Rule labels override matching labels from the expression result or group. |
| `annotations` | map | no | `{}` | Human-readable text attached to each firing alert instance. Supports Go templating. |

Complete alert rule example:

```yaml
groups:
  - name: api-latency
    interval: 1m
    rules:
      - alert: ApiP95LatencyAbove750ms
        expr: >-
          0.75 < histogram_quantile(
            0.95,
            sum by (job, le) (rate(http_request_duration_seconds_bucket{job="api"}[5m]))
          )
        for: 10m
        keep_firing_for: 5m
        labels:
          severity: page
          service: api
          team: edge
        annotations:
          summary: API p95 latency is high
          description: |-
            API p95 latency stayed above 750ms for 10 minutes.
            Current value: {{ $value }}s.
            Service: {{ $labels.job }}
          runbook_url: https://runbooks.example.com/api-high-latency

```

## Recording Rule Schema

| Field | Type | Required | Default | Description |
| --- | --- | --- | --- | --- |
| `record` | string | yes | -- | Metric name for the recorded output. Must follow Prometheus metric naming conventions. |
| `expr` | PromQL | yes | -- | Expression evaluated at each group interval. Result becomes the recorded metric. |
| `labels` | map | no | `{}` | Extra labels attached to every sample of the recorded series. |

Complete recording rule example:

```yaml
groups:
  - name: api-recording
    interval: 30s
    rules:
      - record: job:http_requests:rate5m
        expr: >-
          sum by (job) (rate(http_requests_total[5m]))
        labels:
          environment: production

```

Recording-rule naming convention:

- Name recorded metrics in a `level:metric:operations` shape such as `job:http_requests:rate5m`
- Let `level` describe the aggregation level after labels are removed or collapsed
- Keep operations ordered from newest transformation outward, such as `rate5m` or `avg_rate5m`
- When a counter feeds `rate()` or `irate()`, drop `_total` from the recorded metric name
- Prefer `without (...)` when the main review need is making removed labels explicit, but use `by (...)` when the retained label set is the clearer part of the contract

## Minimal Setup

Prometheus rules belong in a file loaded by the server or rule-evaluation stack, and `promtool` should run against the exact file shape that will ship.

Use the deployment's `promtool` when available.
Before obtaining a new binary, check the official Prometheus releases for the latest stable version compatible with the target server and rule features.
If `promtool` is unavailable, report validation as blocked instead of claiming the rule is ready.

Minimal alerting file:

```yaml
groups:
  - name: api-latency
    interval: 1m
    rules:
      - alert: ApiP95LatencyAbove750ms
        expr: >-
          0.75 < histogram_quantile(
            0.95,
            sum by (job, le) (rate(http_request_duration_seconds_bucket{job="api"}[5m]))
          )
        for: 10m
        labels:
          severity: page
          service: api
          team: edge
        annotations:
          summary: API p95 latency is high
          description: |-
            API p95 latency stayed above 750ms for 10 minutes.
            Current value: {{ $value }}s.
          runbook_url: https://runbooks.example.com/api-high-latency

```

Use when: you need one minimal valid Prometheus rules file with a complete alert definition.
