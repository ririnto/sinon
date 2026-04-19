---
name: alert-rule-testing
description: >-
  Use this skill when the user asks to "write or review promtool rule tests", "test alert firing behavior", "add input_series fixtures", "protect alert regressions", or needs guidance on Prometheus alert-rule test authoring and verification.
metadata:
  title: "Alert Rule Testing"
  official_project_url: "https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/"
  reference_doc_urls:
    - "https://prometheus.io/docs/prometheus/latest/configuration/unit_testing_rules/"
    - "https://prometheus.io/docs/prometheus/latest/command-line/promtool/"
    - "https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/"
  version: "latest"
compatibility: "Requires `promtool` in PATH for the validation command shown in the common path. If `promtool` is unavailable, stop at a blocked validation state instead of claiming the test suite is ready."
---

# Alert Rule Testing

## Overview

Use this skill to write and review `promtool test rules` files that lock alert behavior before a rule ships. The common case is one test file that points at the real rule file, defines a small set of `input_series`, and proves the alert stays non-firing, becomes pending, fires after the `for` window, and resolves when the signal recovers.

## Use This Skill When

- You are writing or reviewing `promtool test rules` files.
- You need to verify alert timing, firing, non-firing, or regression behavior.
- You need guidance on `input_series`, `eval_time`, expected labels, or expected annotations.
- Do not use this skill when the main task is designing the alert rule itself rather than testing its behavior.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- test file structure with `rule_files`, `evaluation_interval`, and `tests`
- `input_series` notation and fixture design
- `alert_rule_test` structure and expectation shape
- firing, non-firing, pending, and resolved-state checks
- regression-focused test selection and review criteria

## Version-Sensitive Features

- `start_timestamp`, `fuzzy_compare`, and the `--run` flag are newer Prometheus testing features and should be treated as version-sensitive rather than baseline assumptions.
- `--diff` is currently documented as experimental; do not treat it as a universal baseline.
- Core test file structure such as `rule_files`, `input_series`, `alert_rule_test`, and `promql_expr_test` is documented without narrower minimum-version guidance in the official docs.

## Common-Case Workflow

1. Start from the real alert rule file that must stay stable.
2. Add the smallest `input_series` set that proves normal, pending, firing, and recovery behavior.
3. Pick `eval_time` values that sit clearly before and after the alert `for` window.
4. Assert expected labels and annotations only for the alert state you actually need to protect.
5. Keep the test small enough to explain one behavior change at a time.
6. Run `promtool test rules` on the real test file before treating the rule change as safe.

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

## Ready-to-Adapt Templates

Below-threshold check — prove the alert does not fire during normal traffic:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: you need a fast regression check for the non-firing case.

Pending check — prove the rule crossed the threshold but has not satisfied `for` yet:

```yaml
alert_rule_test:
  - eval_time: 8m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: the alert has a `for` window and you need to prove it is not firing too early.

Pending-state interpretation rule:

- `promtool test rules` does not expose a separate pending assertion type in `exp_alerts`
- prove pending behavior by pairing an empty `exp_alerts: []` result with an `eval_time` where the threshold is crossed but the `for` window is not yet complete
- prove true non-firing behavior with another empty `exp_alerts: []` check where the threshold was never crossed

Firing check — prove expected labels or annotations once the `for` window completes:

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

Resolved check — prove the alert stopped firing after recovery:

```yaml
alert_rule_test:
  - eval_time: 25m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []
```

Use when: an earlier eval time already proved the alert fired, and a later eval time now proves the alert cleared after the input series recovered.

Resolved-state interpretation rule:

- `promtool test rules` shows resolved behavior by returning no expected firing alerts at a later `eval_time`
- distinguish resolved from never-firing by pairing this check with an earlier firing assertion on the same alert
- if the rule uses `keep_firing_for`, place the resolved check after that hold-open window rather than immediately after the signal drops

PromQL expression check — verify one intermediate query result directly:

```yaml
promql_expr_test:
  - expr: sum(increase(http_requests_total{job="api"}[5m]))
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 500
```

Use when: the blocker is an intermediate query shape rather than only the final alert state.

## Validate the Result

Validate the common case with these checks:

- the test file points at the actual rule file under review
- `input_series` values make the intended state transition obvious
- `eval_time` proves non-firing, pending, firing, or recovery on purpose rather than by accident
- expected labels and annotations match the alert contract you truly care about
- the test suite protects real regressions without becoming an unreadable fixture dump
- `promtool test rules` passes on the test file you intend to ship

## Output contract

Return:

1. the recommended test file or review decision
2. the rule file and test behaviors covered by the result
3. validation results, including whether `promtool test rules` ran or is blocked by missing local tooling
4. remaining risks, assumptions, or uncovered alert behaviors

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| time-based test context, custom timestamps, or float-comparison tolerances | [`./references/test-execution-controls.md`](./references/test-execution-controls.md) |
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

## Scope Boundaries

- Activate this skill for:
  - `promtool test rules` authoring and review
  - alert behavior verification and regression protection
  - fixture design, eval timing, and expected alert-state checks
- Do not use this skill as the primary source for:
  - full alert-rule design
  - Alertmanager routing behavior
  - Grafana dashboard authoring or provisioning
