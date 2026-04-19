---
title: "Alert Rule Test Execution Controls"
description: "Open this when custom test timestamps, fuzzy comparison, or filtered test execution is the blocker."
---

# Alert Rule Test Execution Controls

Use this reference when the main test shape is already correct, but execution controls or time context still need work.

## Version Notes

- `start_timestamp`, `fuzzy_compare`, and `--run` should be treated as version-sensitive features rather than baseline assumptions.
- `--diff` is documented as experimental.

## Execution Controls

```yaml
evaluation_interval: 1m
fuzzy_compare: true

tests:
  - name: api-error-rate
    start_timestamp: 2026-01-01T00:00:00Z
```

Filtered execution command:

```bash
promtool test rules --run '^api-error-rate$' alerts/api-errors.test.yaml
```

Use these controls when:

- test time must start from a deliberate timestamp
- floating-point comparisons are close enough that strict equality is noisy
- you need to run one named subset of tests during focused iteration

## Review Questions

- does the test really need a custom timestamp or would relative time be clearer
- is fuzzy comparison hiding a real query regression
- would a smaller targeted test be clearer than relying on `--run`
