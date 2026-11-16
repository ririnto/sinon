---
metadata:
  reference:
    Prometheus:
      version: 3.13.5
      license: Apache-2.0
      url:
        - https://prometheus.io/docs/prometheus/3.13/querying/basics/
        - https://prometheus.io/docs/prometheus/3.13/querying/operators/
        - https://prometheus.io/docs/prometheus/3.13/querying/functions/
        - https://prometheus.io/docs/prometheus/3.13/querying/api/
        - https://prometheus.io/docs/prometheus/3.13/feature_flags/
        - https://github.com/prometheus/prometheus/releases/tag/v3.13.5
name: promql
description: >-
  Use for PromQL query authoring, aggregation, vector matching, SLI math, or query review for alerts, dashboards, and recording rules.
---

# PromQL

Write and review PromQL queries that stay readable, correct, and appropriate for the consumer that will use them.
This file keeps the authoring workflow, function choices, and review checks.

For language syntax, operators, functions, vector matching, staleness, or HTTP API shapes, read [language reference](./references/language-reference.md).

## Task Focus

- Establish the operator question, metric type, expected labels, units, and consumer from the available query and metric evidence.
- Change selectors, aggregation, or matching only after the intended output shape is clear.
- Prefer `rate()` for alerts and recording rules.
  Use `irate()` when fast-moving dashboard visualization is the goal.
- Use the matching reference for syntax, complex joins, histograms, or experimental features instead of loading the full language catalog.
- Preserve the consumer's label and unit contract when simplifying an existing expression.

## Core Language Facts

PromQL expressions evaluate to one of four types: instant vector, range vector, scalar, and string.
Instant queries accept any type as root.
Range queries only accept scalar or instant vector as root.

Selectors pair a metric name with label matchers (`=`, `!=`, `=~`, `!~`).
Regex matchers are fully anchored RE2.
Range selectors append a duration, as in `metric[5m]`.
`offset` and `@` modifiers follow the selector immediately, before any wrapping function or aggregation.
Counter functions (`rate`, `irate`, `increase`) go before aggregation so resets are detected: `sum(rate(...))`, never `rate(sum(...))`.

## First Runnable Commands or Code Shape

Start with a query shape that makes the vector type obvious before you optimize it:

```promql
rate(http_requests_total{job="api",status=~"5.."}[5m])
```

Use when: you need the smallest safe starting point for a counter-based error-rate query.

Function-choice baseline:

- use `rate()` for stable alerting or recording-rule inputs built from counters
- use `irate()` only for visually volatile dashboard panels where short-window responsiveness matters more than stability
- use `increase()` when the question is total change over the window rather than per-second rate
- use `histogram_quantile()` only after the bucket series are aggregated to the label set you intend to keep
- use `label_replace()` only when you must reshape labels explicitly.
  - Avoid it when a simpler selector, aggregation, or recording rule keeps the query readable
- use `absent()` or `absent_over_time()` when the missing series itself is the signal rather than a zero-valued metric
- use `delta()` and `idelta()` with gauges only.
  - Never with counters (no reset adjustment)
- use `predict_linear()` with gauges only.
  - For capacity forecasting based on trend
- use `_over_time` functions when you need rollup statistics across a window rather than a rate

## Query Examples

For selector, rate, alert, dashboard, vector-matching, and missing-series examples, read [query examples](./references/query-examples.md) when choosing that query shape.

## Validate the Result

Review the affected query behavior with these checks:

- selectors and label matchers target the intended series set without accidental overreach
- the query uses the right vector type for the chosen function (instant vs range)
- aggregation keeps the labels you need and removes the ones you do not
- binary operators and matching rules are explicit where label-set alignment matters
- `offset` and `@` modifiers follow the selector immediately, not outside the wrapping function/aggregation
- `rate()` versus `irate()` matches the consumer context
- counter functions (`rate`, `irate`, `increase`) are applied before aggregation so resets are detected correctly
- the final expression is readable enough that another operator can review it quickly

Use existing query checks or relevant fixtures to verify changed values, labels, or matching behavior.
For live read-only queries, bound selectors, time range, step, and timeout to the question.
Report observed results separately from reasoning, and state missing data or tooling that limits verification.
Query inspection does not authorize server configuration changes, feature-flag changes, or publishing rules and dashboards.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. The recommended query or review decision
2. The intended consumer context such as alert, dashboard, or recording rule
3. Any required label-set, vector-matching, or function-choice rationale
4. Remaining blockers, assumptions, or follow-up query risks

## References

| When working on... | Read... |
| --- | --- |
| exact syntax, literals, operators, aggregation or function tables, vector matching, staleness, or HTTP API shapes | [`./references/language-reference.md`](./references/language-reference.md) |
| non-trivial vector matching, label-set alignment, worked input/output examples, or refactoring a complex query without changing meaning | [`./references/query-shaping.md`](./references/query-shaping.md) |
| histogram bucket interpolation behavior, native vs classic histogram differences, or advanced histogram_fraction edge cases | [`./references/histogram-details.md`](./references/histogram-details.md) |
| experimental functions, feature flags, or bleeding-edge PromQL features | [`./references/experimental-features.md`](./references/experimental-features.md) |

## Invariants

- MUST choose selectors and label matchers deliberately.
- MUST use the correct vector type for the chosen function.
- MUST place `offset` and `@` modifiers immediately after the selector, before any wrapping aggregation or function.
- SHOULD prefer `rate()` over `irate()` for alerts and recording rules.
- SHOULD make label retention and removal explicit with `by (...)` or `without (...)`.
- SHOULD keep the final query readable enough to review without reverse-engineering every label transition.
- SHOULD apply counter-based functions (`rate`, `irate`, `increase`) before aggregation to ensure reset detection works.

## Common Pitfalls

Start with one-to-one matching before adding `group_left` or `group_right`, and make label-cardinality assumptions explicit.
Use bounded label filters and aggregation before graphing unknown high-cardinality metrics.
Regex matchers are anchored: use `foo.*` for a prefix and `.*bar.*` for a substring.
Stale series disappear rather than becoming zero; detect disappearance with `absent()` or `absent_over_time()` and use explicit zero-fill when needed.
Reserve `delta` and `idelta` for gauges because they do not adjust for counter resets.

## Scope Boundaries

- Activate this skill for:
  - PromQL query authoring and review
  - selector, matcher, aggregation, function, and vector-matching choices
  - HTTP API interaction patterns for querying Prometheus
- Do not activate for:
  - full alert-rule YAML authoring
  - Alertmanager routing and notification design
  - Grafana dashboard layout, provisioning, or panel configuration
  - Prometheus server configuration, scraping, or relabeling rules
  - metric naming conventions, exposition format design, or application instrumentation library setup
