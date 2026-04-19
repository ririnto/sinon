---
name: alertmanager
description: >-
  Use this skill when the user asks to "write or review Alertmanager config", "route alerts to the right receiver", "tune group_wait or repeat_interval", "add inhibition or mute intervals", or needs guidance on alertmanager.yml authoring and notification routing quality.
metadata:
  title: "Alertmanager"
  official_project_url: "https://prometheus.io/docs/alerting/latest/configuration/"
  reference_doc_urls:
    - "https://prometheus.io/docs/alerting/latest/configuration/"
    - "https://prometheus.io/docs/alerting/latest/alerts_api/"
    - "https://prometheus.io/docs/alerting/latest/notifications/"
    - "https://prometheus.io/docs/practices/alerting/"
  version: "latest"
compatibility: "Requires `amtool` in PATH for the validation command shown in the common path. If `amtool` is unavailable, stop at a blocked validation state instead of claiming the config is ready."
---

# Alertmanager

## Overview

Use this skill to author and review Alertmanager configuration that routes alerts clearly, groups them deliberately, and avoids noisy or misleading notifications. The common case is one root route, one small set of child routes, one deliberate receiver mapping, and one timing policy that batches related alerts without hiding urgent signal.

## Use This Skill When

- You are writing or reviewing `alertmanager.yml`.
- You need guidance on route trees, receivers, matchers, grouping, inhibition, mute windows, or notification templates.
- You need to improve notification quality without rewriting the alert rule that produced the alert.
- Do not use this skill when the main task is writing Prometheus alert rules or PromQL expressions rather than downstream routing and notification handling.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- top-level Alertmanager config structure
- root route and child route tree design
- receiver definition and route attachment
- grouping with `group_by`, `group_wait`, `group_interval`, and `repeat_interval`
- matcher-based branching
- basic inhibition and mute-time route usage
- notification quality and review criteria

## Version-Sensitive Features

- UTF-8 matchers, `time_intervals`, and the modern `matchers` array syntax should be treated as newer Alertmanager features rather than a universal baseline.
- If you must support older configurations, verify whether the deployment still expects classic matcher forms such as `match` and `match_re`.
- This skill uses the modern syntax as the baseline.

## Common-Case Workflow

1. Start from the alert-routing intent: who should receive which alerts, and which alerts should stay grouped together.
2. Define one root route with a safe default receiver.
3. Add child routes only where labels, severity, team ownership, or environment justify a branch.
4. Tune `group_wait`, `group_interval`, and `repeat_interval` deliberately so related alerts batch together without hiding urgent changes.
5. Keep receiver definitions explicit, and make sure routing labels from Prometheus alerts actually match the route tree you wrote.
6. Add inhibition or mute windows only when they remove known noise without suppressing the primary symptom; keep inhibition in top-level `inhibit_rules` and attach mute windows only to the routes they should affect.

## Minimal Setup

Minimal route tree and receiver layout:

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - service
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
        - severity="page"

receivers:
  - name: platform-default
  - name: api-pager
```

Use when: you need one readable Alertmanager baseline with a default receiver and one label-based branch.

## First Runnable Commands or Code Shape

Start by validating the configuration file that will actually ship:

```bash
amtool check-config alertmanager.yml
```

Use when: the config was just edited, `amtool` is available in `PATH`, and you need the first safe syntax and schema check. If `amtool` is unavailable, stop at a blocked validation state instead of claiming the config is ready.

## Ready-to-Adapt Templates

Team route branch — route critical API alerts to a dedicated receiver:

```yaml
route:
  receiver: platform-default
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
        - severity="page"

receivers:
  - name: platform-default
  - name: api-pager
```

Use when: one team owns a clearly labeled alert stream.

Grouping defaults — batch related alerts before the first notification:

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - service
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
```

Use when: you need a sane default notification cadence before adding more routes.

Basic inhibition — suppress a lower-severity symptom when a stronger alert already explains it:

```yaml
inhibit_rules:
  - source_matchers:
      - severity="page"
    target_matchers:
      - severity="ticket"
    equal:
      - service
      - cluster
```

Use when: multiple alerts describe the same outage and the lower-severity signal would only add noise.

Minimal notification template — load one template file and render one stable summary from common labels:

```yaml
global:
  smtp_smarthost: smtp.example.org:587
  smtp_from: alertmanager@example.org

templates:
  - templates/*.tmpl

receivers:
  - name: platform-default
    email_configs:
      - to: oncall@example.org
        html: '{{ template "alert.summary" . }}'
```

```gotemplate
{{ define "alert.summary" }}
{{ .CommonLabels.alertname }} for {{ .CommonLabels.service }}
{{ end }}
```

Use when: the route is already correct and the common path only needs one small template surface for clearer notifications. Keep the template file on disk at the path matched by `templates:` so Alertmanager can actually load it, and wire it through a receiver field that actually supports templated strings.

Mute interval on a route — suppress notifications during a scheduled window:

```yaml
route:
  receiver: platform-default
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
      mute_time_intervals:
        - offhours

time_intervals:
  - name: offhours
    time_intervals:
      - weekdays:
          - monday:friday
        times:
          - start_time: "00:00"
            end_time: "08:00"
```

Use when: a route should stay valid, but notifications from that route should pause during a known schedule.

## Validate the Result

Validate the common case with these checks:

- the root route has a deliberate default receiver
- every child route matches on labels that the upstream alert rules really emit
- grouping timers batch related alerts without muting urgent signal
- receiver names are explicit and connected to the intended routes
- inhibition or mute logic removes known noise rather than hiding the primary symptom
- any mute interval used by a route is defined clearly in the same config
- notification templates use labels and annotations that upstream alerts actually provide
- `amtool check-config` passes on the shipped config file

## Output contract

Return:

1. the recommended Alertmanager config or review decision
2. the intended route, receiver, inhibition, mute-interval, or template changes
3. validation results, including whether `amtool check-config` ran or is blocked by missing local tooling
4. remaining risks, assumptions, or upstream label-contract dependencies

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| designing or reviewing inhibition logic | [`./references/inhibition-rules.md`](./references/inhibition-rules.md) |
| defining mute windows or active time schedules | [`./references/time-intervals.md`](./references/time-intervals.md) |
| shaping notification content with templates | [`./references/notification-templates.md`](./references/notification-templates.md) |

## Invariants

- MUST keep a deliberate default receiver at the root route.
- MUST keep the ordinary Alertmanager authoring path understandable from this file alone.
- MUST make matcher and receiver relationships explicit.
- SHOULD keep route trees shallow unless a deeper split is clearly justified.
- SHOULD use inhibition and mute windows to remove noise, not to hide the primary alert.
- SHOULD keep grouping timers deliberate and reviewable.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| building a deep route tree before the label contract is stable | routing becomes hard to reason about and easy to break | start with one root route and only add branches backed by real labels |
| setting `group_wait` too high for urgent pages | the first useful signal arrives too late | keep the initial wait short for paging routes |
| using inhibition where route tuning would be enough | valid alerts disappear behind suppression logic | prefer clearer routing and grouping before adding inhibition |
| writing matchers for labels that the alert rules do not emit | routes never match in production | verify the Alertmanager config against the upstream label contract |

## Scope Boundaries

- Activate this skill for:
  - Alertmanager route trees and receivers
  - grouping timers, matchers, inhibition, mute intervals, and templates
  - downstream notification quality and routing review
- Do not use this skill as the primary source for:
  - Prometheus alert-rule authoring
  - PromQL query design
  - Grafana dashboard authoring or provisioning
