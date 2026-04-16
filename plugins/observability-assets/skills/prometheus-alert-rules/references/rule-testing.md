---
title: Prometheus Rule Testing Reference
description: >-
  Reference for writing `promtool test rules` files and deciding when alert behavior deserves explicit tests.
---

Use this reference when the main blocker is not the alert expression itself but how to express expected firing behavior in a `promtool` test file.

If the rule expression uses `rate()`, division, or quantiles that naturally produce decimal values, keep the production expression aligned with the documentation rule: use an explicit rounding step such as `round(expr, 0.001)` and keep the threshold on the left with `<` or `<=`.

## When to Add a Rule Test

Add a rule test when:

- the alert gates paging or ticket routing
- the expression is subtle enough that future edits could change meaning silently
- the rule depends on recording rules or non-obvious evaluation timing

## Test Matrix to Cover

The entrypoint already carries the canonical common-path example. Use this reference when the blocker is deciding which extra states must be protected in a non-trivial rule.

- below-threshold: proves the alert does not fire during normal traffic
- boundary-threshold: proves exact-threshold behavior matches the chosen `<` or `<=` comparison
- pending: proves the rule has crossed the threshold but has not yet satisfied the `for` window
- firing: proves the alert fires with the expected labels after the `for` window completes
- resolved: proves the alert clears after recovery rather than staying latched

## Eval-Time Strategy

Pick eval times deliberately instead of scattering arbitrary timestamps.

- evaluate one point clearly before the `for` window completes to prove pending behavior
- evaluate one point after the `for` window completes to prove firing behavior
- evaluate one point after recovery to prove resolution
- if a recording rule feeds the alert, allow enough time for the recording rule and alert rule evaluation chain to settle

Recording-rule-backed check:

```text
recording rule settles first
-> alert expression consumes recorded metric
-> eval_time proves pending/firing after both steps have had time to evaluate
```

Use this when the blocker is understanding why a recording-rule-backed alert may need more settling time than a raw alert expression.

## Series Design Strategy

Use input series that make the test intent obvious.

- choose values that sit clearly below the threshold for the non-firing case
- choose one exact boundary case so the comparison direction is tested, not assumed
- choose one sustained above-threshold series so the firing case is unambiguous
- choose one recovery pattern where the ratio or quantile visibly returns to a safe range before the final eval time

Run it with:

```bash
promtool test rules alerts/api-errors.test.yaml
```

## Common Mistakes

- forgetting that `eval_time` must account for the alert `for` window
- testing only the firing case and skipping below-threshold, boundary, pending, and resolved states
- testing labels inconsistently with the actual rule file
- using a synthetic series shape that never drives the alert across the threshold
- using a generic alert name that hides the actual firing threshold or symptom
- letting decimal-producing expressions drift between raw float output and rounded production expressions
