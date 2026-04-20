---
title: Prometheus Rule Testing Reference
description: "Open this when the blocker is deciding whether an alert needs regression coverage or which lifecycle behaviors the adjacent testing path should protect."
---

Use this reference when the alert expression is already shaped correctly, but you need to decide whether the rule deserves dedicated regression coverage and which alert-side behaviors are risky enough to protect in the adjacent testing path.

Actual `promtool test rules` file authoring belongs in [`../../alert-rule-testing/SKILL.md`](../../alert-rule-testing/SKILL.md). Keep baseline alert anatomy in [`../SKILL.md`](../SKILL.md).

## When This Alert Likely Needs Regression Coverage

Author regression test fixtures when any of these are true:

- The alert gates paging, ticket creation, or another high-cost operator action.
- The expression has threshold-edge behavior where `<` versus `<=` materially changes the result.
- The alert depends on a non-trivial `for` window that must not fire too early.
- `keep_firing_for` is present and false resolution would create noisy churn.
- The alert consumes a recording rule, so evaluation timing depends on more than one rule step.
- The expression uses `histogram_quantile()`, `rate()` over counters, or other functions where floating-point rounding could shift boundary behavior.

## Behaviors Worth Protecting

The main alert-authoring decision is not how to write the test file, but which alert behaviors should be preserved over time:

- Threshold edge behavior when exact-boundary values matter.
- Pending versus firing timing around the configured `for` window.
- Hold-open behavior when `keep_firing_for` is part of the noise-control design.
- Post-recovery resolution timing for alerts that must clear predictably.
- Recording-rule settle time when the alert depends on an intermediate recorded series.
- Label set stability when downstream Alertmanager routing depends on specific label values.

Recording-rule-backed timing reminder:

```text
recording rule evaluates first
-> alert expression consumes the recorded series later
-> regression checks should allow for both steps before interpreting firing behavior
```

## Test Coverage Matrix by Alert Type

| Alert characteristic | Minimum test cases | Recommended test cases |
| --- | --- | --- |
| Simple threshold, no `for` | firing | below-threshold, firing |
| With `for: 10m` | pending (before `for`), firing (after `for`) | below-threshold, pending, firing, resolved |
| With `keep_firing_for` | firing, resolved (after hold-open) | below-threshold, pending, firing, during-hold-open, resolved |
| Consumes recording rule | firing with recorded value check | all above + promql_expr_test on intermediate |
| Multi-threshold tiered alerts | each tier's firing case | each tier's below/pending/firing/resolved |
| Uses histogram_quantile | firing at boundary | boundary-exact, above-boundary, below-boundary |

## Alert-Authoring Handoff Notes

- Keep the test file pointed at the exact rule file that will ship.
- Preserve the same alert name, bounded routing labels, and meaningful threshold contract from the rule file.
- If the production expression rounds decimal output, keep that same rounded expression in the shipped rule before writing regression checks around it.
- Author `promtool test rules` fixtures when the blocker becomes fixture design, `input_series`, `eval_time`, `alert_rule_test`, or runnable test commands.

## Common Test Design Mistakes to Avoid During Authoring

These are mistakes made while writing the alert rule itself that make testing harder or impossible:

**Expression depends on external data not available in tests**

Broken -- references a metric that cannot be easily synthesized:

```yaml
# Hard to test: depends on real cluster topology
expr: count(up{job="api"}) < count(kube_node_info)
```

Better -- self-contained within the scrape target:

```yaml
expr: count(up{job="api"} == 0) > 0
```

**Expression uses `time()` or other non-deterministic functions**

Broken -- test results vary by wall-clock time:

```yaml
expr: time() - timestamp(some_metric[1h]) > 3600
```

Better -- use relative durations that work at any eval_time:

```yaml
expr: some_metric offset 1h == 0
```

**Overly broad label matchers that produce many series**

Broken -- test needs dozens of input_series entries:

```yaml
expr: sum(rate(http_requests_total[5m])) by (job) > 100
```

Better -- constrain the scope so a minimal fixture proves the point:

```yaml
expr: sum(rate(http_requests_total{job="api"}[5m])) > 100
```
