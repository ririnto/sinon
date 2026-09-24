---
metadata:
  reference:
    Prometheus:
      version: 3.14.0
      license: Apache-2.0
      url:
        - https://prometheus.io/docs/prometheus/3.14/configuration/recording_rules/
        - https://prometheus.io/docs/prometheus/3.14/configuration/alerting_rules/
    Prometheus releases:
      url: https://github.com/prometheus/prometheus/releases
name: prometheus-alert-rules
description: >-
  Use for Prometheus alert and recording rules, firing timers, routing labels, annotations, and promtool check rules validation.
---

# Prometheus Alert Rules

Design and review Prometheus alert and recording rules around real operator symptoms, validate them with `promtool`, and keep rule definitions stable in version-controlled files.

## Task Focus

- Inspect the affected rules, consumers, and existing tests against the operator symptom that should page or ticket.
- Keep group placement, evaluation intervals, and the alert expression deliberate.
- Choose an explicit `for` window for sustained symptoms.
  Use `keep_firing_for` only when brief recoveries need suppression and the deployed version supports it.
- Keep labels literal, bounded, and routing-oriented, and keep annotations actionable.
  Keep link-like annotations such as `runbook_url` literal and trusted.
  Use lightweight Go templates only in human-readable annotations where they improve operator clarity.
- Introduce recording rules when reuse or evaluation cost justifies them, not as a prerequisite to alert authoring.
- Validate edited rule files with `promtool check rules`.
  Use `alert-rule-testing` when timing, thresholds, or label changes need regression fixtures.

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
          0.75 < round(
            histogram_quantile(
              0.95,
              sum by (le) (rate(http_request_duration_seconds_bucket{job="api"}[5m]))
            ),
            0.001
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
          round(sum by (job) (rate(http_requests_total[5m])), 0.001)
        labels:
          environment: production

```

Recording-rule naming convention:

- Name recorded metrics in a `level:metric:operations` shape such as `job:http_requests:rate5m`
- Let `level` describe the aggregation level after labels are removed or collapsed
- Keep operations ordered from newest transformation outward, such as `rate5m` or `avg_rate5m`
- When a counter feeds `rate()` or `irate()`, drop `_total` from the recorded metric name
- Prefer `without (...)` when the main review need is making removed labels explicit, but use `by (...)` when the retained label set is the clearer part of the contract

## Alert State Machine

Prometheus alerts transition through three states:

```text
inactive --> pending --> firing
   ^                    |
   |____________________|
           resolves

```

## Inactive

The expression evaluates to false or produces no series.
No alert state exists.

## Pending

The expression first evaluates to true.
A timer starts for the `for` duration.
During pending:

- The alert is visible in the UI as "pending"
- No notification is sent to Alertmanager
- If the expression becomes false before `for` elapses, the alert reverts to inactive
- If the expression stays true for the full `for` duration, the alert transitions to firing

## Firing: The `for` duration has been satisfied while the expression remains true

- Alert instances are sent to Alertmanager
- Notifications are dispatched according to Alertmanager routing
- If the expression becomes false:
  - Without `keep_firing_for`: the alert immediately returns to inactive
  - With `keep_firing_for`: the alert stays in firing for the configured hold-open duration, then returns to inactive

## Flapping Behavior

An alert that oscillates around the threshold resets its pending timer each time it drops back to inactive.
Only a continuous `for` window of true evaluations causes a transition to firing.

Timing diagram for `for: 10m` with `keep_firing_for: 5m`:

```text
Time   0m   5m   10m  15m  20m  25m
Expr   T    T    T    T    F    F
State  pen  pen  fire fire fire inactive
                        ^--keep_firing_for holds here

```

## Go Template Variables in Annotations and Labels

These variables are available inside `{{ }}` template expressions in annotation values:

| Variable | Type | Description |
| --- | --- | --- |
| `$labels` | map | Labels of the alert instance (from the expression result). Access fields as `$labels.label_name`. |
| `$value` | string | String representation of the alert expression value at the time of firing. |
| `$externalLabels` | map | External labels configured on the Prometheus server (`prometheus.yml` -> `global.external_labels`). |
| `$externalURL` | string | Configured external URL of the Prometheus server (`prometheus.yml` -> `global.external_url`). |

Template syntax reference.
The examples below cover plain variable interpolation in `summary`, value rendering in `description`, conditional rendering with `if`, and dashboard-link composition with `$externalURL`:

```yaml
annotations:
  summary: 'High error rate on {{ $labels.job }}'

  description: 'Error rate is {{ $value }} over the last 5 minutes.'

  description: >-
    {{ if eq $labels.severity "critical" }}Critical{{ else }}Warning{{ end }}:
    value is {{ $value }}

  dashboard: '{{ $externalURL }}/d/my-dashboard?var-job={{ $labels.job }}'

```

Template rules:

- Templates execute once per alert instance (per labelset)
- Template errors cause the entire annotation to render as the raw error string
- Use `{{ $labels.name }}` syntax -- dot notation like `{{ $labels.name }}` works.
  - Bracket notation `{{ index $labels "name" }}` handles special characters
- `$value` is always a string.
  - Compare numerically with helper functions where available
- Do NOT put templates in `labels` values unless you have a specific reason -- labels should be stable and low-cardinality
- Keep link-like annotations such as `runbook_url` as literal strings without template variables

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
          0.75 < round(
            histogram_quantile(
              0.95,
              sum by (le) (rate(http_request_duration_seconds_bucket{job="api"}[5m]))
            ),
            0.001
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

## First Runnable Commands or Code Shape

Start with syntax validation against the actual rule file:

```sh
promtool check rules alerts/api-latency.rules.yaml

```

Use when: the rule is newly added or edited, `promtool` is available in `PATH`, and you need the first safe correctness check before deeper review.

Validate multiple files at once:

```sh
promtool check rules rules/*.yaml

```

Strict mode (fail on warnings):

```sh
promtool check --strict rules alerts/api-latency.rules.yaml

```

### promtool Output Interpretation

Successful output:

```text
rules/api-latency.rules.yaml SUCCESS: 1 rules found

```

Error output examples:

Syntax error in the PromQL expression:

```text
rules/api-latency.rules.yaml FAILED: parsing YAML file rules/api-latency.rules.yaml: error parsing rules: 1:13: parse error

```

Runtime alert evaluation error (not reported by `promtool check rules`):

```text
vector contains metrics with the same labelset after applying alert labels

```

Invalid duration format:

```text
rules/api-latency.rules.yaml FAILED: error parsing rules: invalid duration "10"

```

Missing required field:

```text
rules/api-latency.rules.yaml FAILED: error parsing rules: missing required field 'expr'

```

YAML scalar rule:

- Use `|-` for multiline PromQL strings
- Use `>-` for one-line expressions when plain scalars would become fragile or need escaping
- Prefer block scalars over ad hoc quoting when the expression contains YAML-sensitive characters and readability matters
- Use `round(expr, 0.001)` or an equally explicit precision when `rate()`, division, or quantile evaluation is expected to produce decimal values
- Write comparisons as `threshold < expr` or `threshold <= expr` so the smaller value stays on the left

Rule lifecycle rule:

- Each group evaluates on its interval and processes rules in declared order
- Without `for`, a matching series becomes active immediately on evaluation
- With `for`, a matching series stays pending until the duration is satisfied
- `keep_firing_for` keeps the alert firing briefly after the condition clears so transient gaps do not create noisy resolve/re-fire churn

## Ready-to-Adapt Templates

High error-rate alert -- page on sustained user-visible failures instead of one short spike:

```yaml
groups:
  - name: api-errors
    rules:
      - alert: Api5xxRatioAbove5Percent
        expr: |-
          5 < round(
            100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
              /
            sum(rate(http_requests_total{job="api"}[5m])),
            0.001
          )
        for: 10m
        labels:
          severity: page
          service: api
        annotations:
          summary: API 5xx ratio is high
          description: |-
            API 5xx ratio stayed above 5% for 10 minutes.
            Current value: {{ $value }}%.
          runbook_url: https://runbooks.example.com/api-high-error-rate

```

Use when: user-visible failures matter more than one host-level saturation signal.

Recording-rule-assisted alert -- reduce repeated query cost and stabilize the alert expression:

```yaml
groups:
  - name: api-recording
    rules:
      - record: job:http_requests:rate5m
        expr: >-
          round(sum by (job) (rate(http_requests_total[5m])), 0.001)

  - name: api-alerts
    rules:
      - alert: Api5xxRatioAbove5Percent
        expr: |-
          5 < round(
            100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
              /
            sum(job:http_requests:rate5m{job="api"}),
            0.001
          )
        for: 10m
        labels:
          severity: page
          service: api
        annotations:
          summary: API 5xx ratio is high
          description: |-
            API 5xx ratio stayed above 5% for 10 minutes.
            Current value: {{ $value }}%.

```

Use when: the alert expression is reused or expensive enough to deserve one stable recording layer.

Multi-threshold alert with severity tiering via separate rules:

```yaml
groups:
  - name: disk-usage
    interval: 1m
    rules:
      - alert: DiskSpaceWarning
        expr: >-
          100 * (1 - node_filesystem_avail_bytes{fstype!="tmpfs"}
            /
          node_filesystem_size_bytes{fstype!="tmpfs"}) > 80
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: 'Low disk space on {{ $labels.instance }}'
          description: >-
            Disk usage is above 80% on {{ $labels.instance }} (device {{ $labels.device }}).
            Current: {{ $value }}%.

      - alert: DiskSpaceCritical
        expr: >-
          100 * (1 - node_filesystem_avail_bytes{fstype!="tmpfs"}
            /
          node_filesystem_size_bytes{fstype!="tmpfs"}) > 95
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: 'Critical disk space on {{ $labels.instance }}'
          description: >-
            Disk usage is above 95% on {{ $labels.instance }} (device {{ $labels.device }}).
            Current: {{ $value }}%.

```

Use when: the same symptom needs different urgency levels at different thresholds.

## Validate the Result

Review the affected rules with these checks:

- The alert maps to an operator symptom rather than one noisy infrastructure blip
- Rule groups and intervals are deliberate rather than left implicit by accident
- `for` is explicit and long enough to avoid obvious flapping
- `keep_firing_for` is present only when it reduces noisy false resolution
- Labels stay bounded and routing-oriented
- Annotations explain the symptom and include a usable runbook or remediation pointer
- Template usage stays in human-readable annotations, while link-like annotations such as `runbook_url` stay literal and trusted
- Recording-rule usage is justified by reuse, cost, or readability rather than habit
- The label set is a deliberate contract for downstream Alertmanager routing, grouping, and inhibition
- `promtool check rules` passes on the actual shipped file
- Dedicated alert-rule tests exist in the adjacent testing path when alert behavior must stay stable over time

Report syntax validation separately from behavioral test evidence.
Authoring and local validation do not authorize deploying rules or reloading Prometheus.
Obtain explicit authorization for the target before either runtime action.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. The recommended alert rule or review decision
2. The rule-file path or rule-group placement decision
3. Validation results for syntax, label/annotation contract, and recording-rule usage
4. Any remaining blockers, follow-up test needs, or Alertmanager handoff risks

## References

| If the blocker is... | Read... |
| --- | --- |
| deciding whether an alert needs dedicated regression coverage, recording-rule settle-time checks, or `for`/`keep_firing_for` lifecycle protection | [`./references/testing.md`](./references/testing.md) |
| choosing between direct alerts, recording rules, or reusable low-noise alert patterns | [`./references/alert-patterns.md`](./references/alert-patterns.md) |

## Invariants

- MUST tie the alert to a meaningful operator symptom.
- MUST validate edited rule files with `promtool check rules` before claiming they are ready.
- MUST keep routing labels and annotations explicit.
- MUST keep rule group structure, `for`, and `keep_firing_for` choices deliberate.
- MUST name alerts so the firing condition is obvious from the alert name itself.
- MUST round decimal-producing evaluation expressions deliberately.
- MUST write threshold comparisons with `<` or `<=` so the smaller value stays on the left.
- SHOULD keep templates lightweight because they execute on rule evaluation.
- SHOULD add `promtool test rules` coverage for important or subtle alerts.
- SHOULD keep expressions and labels stable enough for long-lived operations.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| alerting on one short spike with no `for` window | the rule flaps and burns operator trust | add a deliberate `for` window aligned with the symptom |
| using high-cardinality labels such as pod UID in alert labels | routing and deduplication become noisy and unstable | keep labels bounded and move volatile detail into annotations or investigation steps |
| writing only the alert and never validating it | syntax and behavior drift are caught too late | run `promtool check rules` immediately and add tests when the rule matters |
| alerting on a low-level cause with no user-facing impact | operators get pages with weak actionability | page on the symptom and keep lower-level metrics as supporting signals |
| putting template variables in `runbook_url` or other link annotations | broken links when template data contains URL-unsafe characters | keep link-like annotations as literal trusted strings |
| one alert rule produces duplicate output label sets after adding alert labels | Prometheus cannot identify separate alert instances with identical labels | preserve a unique label set for every series produced by the rule |
| setting `for: 0s` explicitly instead of omitting it | signals intent to fire immediately, which is almost never what you want | either omit `for` entirely or set a meaningful duration based on the symptom's natural timescale |

## Scope Boundaries

- Activate this skill for:
  - Prometheus alert-rule and recording-rule files
  - alert scoped PromQL expressions as part of alert authoring
  - `for`, `keep_firing_for`, labels, annotations, templating, and recording-rule-aware alert design
  - `promtool check rules` validation and the rule-side label contract for Alertmanager handoff
  - rule group structure, evaluation intervals, and alert state machine behavior
- Do not activate for:
  - Grafana dashboard layout or dashboard asset review
  - Alertmanager routing trees, receivers, inhibition rules, or notification templates
  - deep PromQL language design beyond what is needed to write or review an alert expression
  - dedicated rule-test authoring guidance beyond the common path covered here
  - application instrumentation library setup
  - generic incident-process design outside the alert asset itself
