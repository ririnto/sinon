---
name: prometheus-alert-rules
description: >-
  Use this skill when the user asks to "write a Prometheus alert", "add or review alert rules", "run promtool check rules", "tune alert noise", or needs guidance on alert-local PromQL and recording-rule-backed alert design.
metadata:
  title: "Prometheus Alert Rules"
  official_project_url: "https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/"
  reference_doc_urls:
    - "https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/"
    - "https://prometheus.io/docs/prometheus/latest/configuration/recording_rules/"
    - "https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/"
    - "https://prometheus.io/docs/alerting/latest/overview/"
  version: "latest"
---

## Overview

Use this skill to design and review Prometheus alert rules around real operator symptoms, validate them with `promtool`, and keep alert definitions stable in version-controlled files. The common case is one rule group with a deliberate evaluation interval, one alert tied to a meaningful symptom, one explicit `for` window that avoids flapping, one clear alert name, one bounded label contract for Alertmanager, and one validation path that proves the shipped rule file is sane before it lands.

## Use This Skill When

- You are adding or reviewing Prometheus alert rules.
- You need to validate rule files with `promtool check rules`.
- You need guidance on rule groups, `for`, `keep_firing_for`, labels, annotations, templating, runbook links, or recording-rule support.
- Do not use this skill when the main task is dashboard layout or Grafana dashboard asset review rather than alert-rule behavior.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- rule group structure, evaluation order, and the alert rule shape that actually ships
- alert expressions, `for`, `keep_firing_for`, and the pending-to-firing lifecycle
- labels, annotations, and lightweight templating for operator-facing alert context
- noise reduction, review criteria, and when recording rules support alert readability or cost
- the Alertmanager-facing label contract that starts in the rule file even though routing lives elsewhere

## Version-Sensitive Features

- `keep_firing_for` should be treated as a newer Prometheus alerting feature rather than a baseline assumption.
- `for`, `labels`, `annotations`, and basic alert-rule structure are long-standing Prometheus features; this skill does not attach a narrower minimum version to them.

## Common-Case Workflow

1. Start from the operator symptom that should page or ticket, not from a random metric spike.
2. Put the rule in a deliberate group, keep the evaluation interval explicit when the default is not enough, and write the smallest alert expression that captures the symptom.
3. Add an explicit `for` window, and add `keep_firing_for` only when brief recoveries or scrape gaps would otherwise cause noisy false resolution and the deployed Prometheus version supports it.
4. Keep labels literal, bounded, and routing-oriented, keep annotations actionable, keep link-like annotations such as `runbook_url` literal and trusted, and use lightweight templates such as `{{ $labels.service }}` or `{{ $value }}` only in human-readable annotations where they improve operator clarity.
5. If the same expensive expression will be reused, extract it into a recording rule before writing the final alert expression.
6. Validate syntax with `promtool check rules`, and hand off deeper regression-fixture work to the adjacent alert-rule testing path when dedicated tests are needed.

## Minimal Setup

Prometheus rules belong in a file loaded by the server or rule-evaluation stack, and `promtool` should run against the exact file shape that will ship.

`promtool` must already be installed and available in `PATH` before you treat a rule edit as ready. If it is unavailable, stop at a blocked validation state instead of claiming the rule is ready.

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

Optional newer hold-open field:

```yaml
keep_firing_for: 5m
```

Use when: the deployed Prometheus version supports this newer field and brief recovery gaps would otherwise create noisy false resolution.

## First Runnable Commands or Code Shape

Start with syntax validation against the actual rule file:

```bash
promtool check rules alerts/api-latency.rules.yaml
```

Use when: the rule is newly added or edited, `promtool` is available in `PATH`, and you need the first safe correctness check before deeper review.

YAML scalar rule:

- use `|-` for multiline PromQL strings
- use `>-` for one-line expressions when plain scalars would become fragile or need escaping
- prefer block scalars over ad hoc quoting when the expression contains YAML-sensitive characters and readability matters
- use `round(expr, 0.001)` or an equally explicit precision when `rate()`, division, or quantile evaluation is expected to produce decimal values
- write comparisons as `threshold < expr` or `threshold <= expr` so the smaller value stays on the left

Rule lifecycle rule:

- each group evaluates on its interval and processes rules in declared order
- without `for`, a matching series becomes active immediately on evaluation
- with `for`, a matching series stays pending until the duration is satisfied
- `keep_firing_for` keeps the alert firing briefly after the condition clears so transient gaps do not create noisy resolve/re-fire churn

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
- prefer `without (...)` when the main review need is making removed labels explicit, but use `by (...)` when the retained label set is the clearer part of the contract

## Validate the Result

Validate the common case with these checks:

- the alert maps to an operator symptom rather than one noisy infrastructure blip
- rule groups and intervals are deliberate rather than left implicit by accident
- `for` is explicit and long enough to avoid obvious flapping
- `keep_firing_for` is present only when it reduces noisy false resolution
- labels stay bounded and routing-oriented
- annotations explain the symptom and include a usable runbook or remediation pointer
- template usage stays in human-readable annotations, while link-like annotations such as `runbook_url` stay literal and trusted
- recording-rule usage is justified by reuse, cost, or readability rather than habit
- the label set is a deliberate contract for downstream Alertmanager routing, grouping, and inhibition
- `promtool check rules` passes on the actual shipped file
- dedicated alert-rule tests exist in the adjacent testing path when alert behavior must stay stable over time

## Output contract

Return:

1. the recommended alert rule or review decision
2. the rule-file path or rule-group placement decision
3. validation results for syntax, label/annotation contract, and recording-rule usage
4. any remaining blockers, follow-up test needs, or Alertmanager handoff risks

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| deciding whether an alert needs dedicated regression coverage, recording-rule settle-time checks, or `for`/`keep_firing_for` lifecycle protection | [`./references/rule-testing.md`](./references/rule-testing.md) |
| choosing between direct alerts, recording rules, or reusable low-noise alert patterns | [`./references/alert-patterns.md`](./references/alert-patterns.md) |

## Invariants

- MUST tie the alert to a meaningful operator symptom.
- MUST validate edited rule files with `promtool check rules` before claiming they are ready.
- MUST keep the ordinary alert-rule path understandable from this file alone.
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

## Scope Boundaries

- Activate this skill for:
  - Prometheus alert-rule files
  - alert-local PromQL expressions as part of alert authoring
  - `for`, `keep_firing_for`, labels, annotations, templating, and recording-rule-aware alert design
  - `promtool check rules` validation and the rule-side label contract for Alertmanager handoff
- Do not use this skill as the primary source for:
  - Grafana dashboard layout or dashboard asset review
  - Alertmanager routing trees, receivers, inhibition rules, or notification templates
  - deep PromQL language design beyond what is needed to write or review an alert expression
  - dedicated rule-test authoring guidance beyond the common path covered here
  - application instrumentation library setup
  - generic incident-process design outside the alert asset itself
