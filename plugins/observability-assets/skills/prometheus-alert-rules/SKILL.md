---
name: prometheus-alert-rules
description: >-
  Use this skill when the user asks to "write a Prometheus alert", "add or review alert rules", "validate rules with promtool", "test a rule", "tune alert noise", or needs guidance on PromQL-based alerting and recording-rule support.
---

# Prometheus Alert Rules

## Overview

Use this skill to design Prometheus alert rules around real operator symptoms, validate them with `promtool`, and keep alert definitions stable in version-controlled files. The common case is one alert tied to a meaningful symptom, one explicit `for` window that avoids flapping, one alert name that makes the firing condition obvious, one comparison written with the smaller value on the left, and one validation path that proves the rule file is sane before it ships.

## Use This Skill When

- You are adding or reviewing Prometheus alert rules.
- You need to validate rules with `promtool check rules` or `promtool test rules`.
- You need guidance on alert labels, annotations, runbook links, or recording-rule support.
- Do not use this skill when the main task is dashboard layout or Grafana dashboard asset review rather than alert-rule behavior.

## Common-Case Workflow

1. Start from the operator symptom that should page or ticket, not from a random metric spike.
2. Write the smallest PromQL expression that captures the symptom, keep labels stable, and round decimal-producing expressions deliberately.
3. If the same expensive expression will be reused, extract it into a recording rule before writing the alert.
4. Add an explicit `for` window, a clear alert name that reveals the firing condition, a comparison that keeps the smaller value on the left, a severity label, and actionable annotations.
5. Validate syntax with `promtool check rules` and lock alert behavior with `promtool test rules` when the rule matters operationally.

## Minimal Setup

Prometheus rules belong in a file loaded by the server or rule-evaluation stack, and `promtool` should run against the exact file shape that will ship.

Minimal alerting file:

```yaml
groups:
  - name: api-latency
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
        annotations:
          summary: API p95 latency is high
          description: |-
            API p95 latency stayed above 750ms for 10 minutes.
            Current value: {{ $value }}s.
          runbook_url: https://runbooks.example.com/api-high-latency
```

## First Runnable Commands or Code Shape

Start with syntax validation against the actual rule file:

```bash
promtool check rules alerts/api-latency.rules.yaml
```

Use when: the rule is newly added or edited and you need the first safe correctness check before deeper review.

YAML scalar rule:

- use `|-` for multiline PromQL strings
- use `>-` for one-line expressions when plain scalars would become fragile or need escaping
- prefer block scalars over ad hoc quoting when the expression contains YAML-sensitive characters and readability matters
- use `round(expr, 0.001)` or an equally explicit precision when `rate()`, division, or quantile evaluation is expected to produce decimal values
- write comparisons as `threshold < expr` or `threshold <= expr` so the smaller value stays on the left

## Ready-to-Adapt Templates

High error-rate alert — page on sustained user-visible failures instead of one short spike:

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

Recording-rule-assisted alert — reduce repeated query cost and stabilize the alert expression:

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

Recording-rule naming rule:

- name recorded metrics in a `level:metric:operations` shape such as `job:http_requests:rate5m`
- let `level` describe the aggregation level after labels are removed or collapsed
- keep operations ordered from newest transformation outward, such as `rate5m` or `avg_rate5m`
- when a counter feeds `rate()` or `irate()`, drop `_total` from the recorded metric name
- prefer `without (...)` for aggregations so the removed labels are explicit and the resulting level is easy to reason about

Rule test — prove below-threshold, boundary, above-threshold, pending, and resolved behavior with `promtool test rules`:

```yaml
rule_files:
  - alerts/api-errors.rules.yaml

tests:
  - name: below-threshold
    interval: 1m
    input_series:
      - series: >-
          http_requests_total{job="api",status="500"}
        values: >-
          0+4x20
      - series: >-
          http_requests_total{job="api",status="200"}
        values: >-
          0+96x20
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []

  - name: boundary-threshold
    interval: 1m
    input_series:
      - series: >-
          http_requests_total{job="api",status="500"}
        values: >-
          0+5x20
      - series: >-
          http_requests_total{job="api",status="200"}
        values: >-
          0+95x20
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []

  - name: above-threshold-pending
    interval: 1m
    input_series:
      - series: >-
          http_requests_total{job="api",status="500"}
        values: >-
          0+6x20
      - series: >-
          http_requests_total{job="api",status="200"}
        values: >-
          0+94x20
    alert_rule_test:
      - eval_time: 8m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []

  - name: above-threshold-firing
    interval: 1m
    input_series:
      - series: >-
          http_requests_total{job="api",status="500"}
        values: >-
          0+6x20
      - series: >-
          http_requests_total{job="api",status="200"}
        values: >-
          0+94x20
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api

  - name: resolved-after-recovery
    interval: 1m
    input_series:
      - series: >-
          http_requests_total{job="api",status="500"}
        values: >-
          0 6 12 18 24 30 36 42 48 54 60 66 70 74 78 82 86 90 94 98
      - series: >-
          http_requests_total{job="api",status="200"}
        values: >-
          0 94 188 282 376 470 564 658 752 846 940 1034 1130 1226 1322 1418 1514 1610 1706 1802
    alert_rule_test:
      - eval_time: 16m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
```

Use when: the rule is important enough that you want a repeatable contract for below-threshold, boundary, pending, firing, and resolved behavior.

## Validate the Result

Validate the common case with these checks:

- the alert maps to an operator symptom rather than one noisy infrastructure blip
- `for` is explicit and long enough to avoid obvious flapping
- labels stay bounded and routing-oriented
- annotations explain the symptom and include a usable runbook or remediation pointer
- `promtool check rules` passes on the actual shipped file
- `promtool test rules` exists for alerts whose behavior must stay stable over time

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| writing or reading `promtool` test files | `./references/rule-testing.md` |
| choosing between direct alert expressions, recording rules, or common alert patterns | `./references/alert-patterns.md` |

## Invariants

- MUST tie the alert to a meaningful operator symptom.
- MUST validate edited rule files with `promtool check rules` before claiming they are ready.
- MUST keep routing labels and annotations explicit.
- MUST name alerts so the firing condition is obvious from the alert name itself.
- MUST round decimal-producing evaluation expressions deliberately.
- MUST write threshold comparisons with `<` or `<=` so the smaller value stays on the left.
- SHOULD add `promtool test rules` coverage for important or subtle alerts.
- SHOULD keep expressions and labels stable enough for long-lived operations.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| alerting on one short spike with no `for` window | the rule flaps and burns operator trust | add a deliberate `for` window aligned with the symptom |
| using high-cardinality labels such as pod UID in alert labels | routing and deduplication become noisy and unstable | keep labels bounded and move volatile detail into annotations or investigation steps |
| writing only the alert and never validating it | syntax and behavior drift are caught too late | run `promtool check rules` immediately and add tests when the rule matters |
| alerting on a low-level cause with no user-facing impact | operators get pages with weak actionability | page on the symptom and keep lower-level metrics as supporting signals |

## Scope Boundaries

- Activate this skill for:
  - Prometheus alert-rule files
  - PromQL alert expressions and recording-rule support
  - `promtool` validation and rule testing
- Do not use this skill as the primary source for:
  - Grafana dashboard layout or dashboard asset review
  - application instrumentation library setup
  - generic incident-process design outside the alert asset itself
