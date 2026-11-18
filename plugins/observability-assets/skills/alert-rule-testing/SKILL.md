---
metadata:
  reference:
    Prometheus:
      version: 3.13.5
      license: Apache-2.0
      url:
        - https://prometheus.io/docs/prometheus/3.13/configuration/unit_testing_rules/
        - https://prometheus.io/docs/prometheus/3.13/querying/basics/
        - https://prometheus.io/docs/prometheus/3.13/querying/functions/
        - https://raw.githubusercontent.com/prometheus/prometheus/v3.13.5/cmd/promtool/unittest.go
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

## Fixture Semantics

When constructing or reviewing test YAML, read [fixture schema and sample notation](./references/fixture-schema.md) for supported fields, defaults, sample formats, and expected-result shapes.
`rule_files` paths are relative to the test file; `interval` spaces samples and does not change rule evaluation cadence.
Counter notation `<start>+<increment>x<steps>` yields `steps + 1` samples.
`_` marks a missing sample; `stale` makes a series absent until a new non-stale sample.
Alert assertions inspect the latest scheduled rule evaluation at or before `eval_time`, while `promql_expr_test` evaluates at the exact time.
Empty `exp_alerts` means no firing alerts and cannot alone distinguish inactive from pending.
Expected labels and annotations are exact full maps, excluding `__name__` and the top-level `alertname` unless the rule itself sets that label.

## First Runnable Commands or Code Shape

Run the test file for the rule under review:

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

For failure messages and likely causes, read [test output interpretation](./references/test-output.md) when diagnosing a test failure.

## Lifecycle Assertions

For concrete normal, pending, firing, recovery, expression, or annotation examples, read [lifecycle examples](./references/lifecycle-examples.md) when testing those behaviors.
To establish pending state, pair a non-firing assertion with evidence that the threshold was crossed before `for` elapsed; use a separate non-firing check for a threshold never crossed.
To establish recovery, pair the later non-firing assertion with an earlier firing assertion and wait past `keep_firing_for` when configured.

## Validate the Result

Review the affected assertions with these checks:

- The test file points at the actual rule file under review.
- `input_series` values make the intended state transition obvious.
- `eval_time` proves non-firing, pending, firing, or recovery on purpose rather than by accident.
- Expected labels and annotations match the alert contract you truly care about.
- The test suite protects real regressions without becoming an unreadable fixture dump.
- `promtool test rules` passes on the test file you intend to ship.
- Each test case has a descriptive `name` field for filtered execution.
- Alert `eval_time` values use a scheduled boundary when the exact evaluation matters.
  Off-boundary values inspect the latest prior evaluation.
  PromQL assertions use the exact requested time.

Local fixture execution does not deploy rules or prove live alert delivery.
Report the tested behaviors and the exact command result before claiming those behaviors are verified.

## Output contract

Deliver the requested fixture or review decision with the rule path and the evaluation times that distinguish the expected states.
Report the `promtool test rules` result and any uncovered transition.

## References

| When working on... | Read... |
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
- Do not activate for:
  - full alert-rule design
  - Alertmanager routing behavior
  - Grafana dashboard authoring or provisioning
