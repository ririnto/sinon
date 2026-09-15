---
name: promql
description: >-
  Use for PromQL query authoring, aggregation, vector matching, SLI math, or query review for alerts, dashboards, and recording rules.
---

# PromQL

Write and review PromQL queries that stay readable, correct, and appropriate for the consumer that will use them.
This file keeps the authoring workflow, function-choice baseline, common templates, and review checks.

## Official Baseline

- Use the official Prometheus query documentation for release 3.14.0, read on 2026-09-13: [Querying basics](https://prometheus.io/docs/prometheus/3.14/querying/basics/), [Operators](https://prometheus.io/docs/prometheus/3.14/querying/operators/), and [Functions](https://prometheus.io/docs/prometheus/3.14/querying/functions/).
- Verified against the `prometheus/prometheus` tag `v3.14.0` (Apache License 2.0).

Exact language syntax, operator and function tables, vector matching detail, staleness, and HTTP API shapes live in [`./references/language-reference.md`](./references/language-reference.md).

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

## Minimal Setup

Minimal selector and aggregation shape:

```promql
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need one readable baseline query with explicit label filtering, a range-vector function, and a stable grouped output.

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

## Ready-to-Adapt Templates

Basic selector -- use one metric and one bounded label set:

```promql
up{job="api",instance=~"api-.+"}
```

Use when: you need a simple instant-vector selector for current target health.

Range-vector aggregation -- convert a counter into a grouped per-second rate:

```promql
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need one stable rate query for dashboards, recording rules, or alert thresholds.

Alert-oriented shape -- keep the query stable and symptom-oriented:

```promql
5 < round(
  100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
    /
  sum(rate(http_requests_total{job="api"}[5m])),
  0.001
)
```

Use when: you need a threshold query that can be embedded in an alert rule without hiding the user-facing symptom.

Dashboard-oriented shape -- use `irate()` only for visually volatile counters:

```promql
sum by (instance) (irate(node_network_receive_bytes_total{job="node"}[1m]))
```

Use when: you are shaping a fast-moving dashboard panel rather than a stable alert condition.

Basic vector matching -- start without a modifier when both sides already share the same grouped label set:

```promql
sum by (job) (rate(http_requests_total{job="api",status=~"5.."}[5m]))
/
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need to preserve one shared label set across both sides of a binary operation and both sides already align without extra matching rules.

Missing-series shape -- page on disappearance instead of on a low numeric threshold:

```promql
absent(up{job="api"})
```

Use when: the query should return a signal only when the expected series is missing.

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

| If the blocker is... | Read... |
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

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `irate()` for a stable alert query | brief spikes can reset alert behavior and create noisy evaluation | use `rate()` for alerting or recorded signals |
| aggregating before deciding which labels must survive | the final series set becomes hard to reason about | decide the output label set first, then aggregate with `by (...)` or `without (...)` |
| adding `group_left` or `group_right` before checking whether simple `on (...)` or `ignoring (...)` is enough | query semantics become harder to review and easier to break | start with one-to-one matching and escalate only when the join shape truly requires it |
| writing a query that works only because the current environment has one lucky label layout | the query breaks as soon as a label cardinality changes | make the selector and matching assumptions explicit in the query itself |
| placing `offset` or `@` outside the aggregation/function wrapper | syntax error; these modifiers bind to the selector, not the expression | put the modifier inside: `sum(metric offset 5m)` not `sum(metric) offset 5m` |
| applying `rate()` after `sum()` instead of before | `rate()` cannot detect counter resets across pre-aggregated series | always do `sum(rate(...))` never `rate(sum(...))` for counters |
| using `delta()` or `idelta()` on counters | no automatic reset adjustment; counter rollovers produce spurious large deltas | use `rate()` or `increase()` for counters; reserve `delta`/`idelta` for gauges |
| bare metric name selector in dashboards over high-cardinality metrics | expands to thousands of series, causing slow queries and browser timeouts | always apply label filters and aggregation before graphing unknown data |
| assuming regex matchers are unanchored | `env=~"foo"` matches only exact `"foo"`, not `"foobar"` | use `env=~"foo.*"` for prefix matching, or `env=~".*bar.*"` for substring |
| relying on staleness to produce zero | stale series disappear entirely rather than returning zero | use `absent()` or `absent_over_time()` to detect disappearance; use `* 0` or explicit clamping if zero-fill is needed |

## Scope Boundaries

- Activate this skill for:
  - PromQL query authoring and review
  - selector, matcher, aggregation, function, and vector-matching choices
  - query tuning for alert or dashboard context
  - HTTP API interaction patterns for querying Prometheus
  - understanding data types, literals, operator precedence, and staleness
- Do not activate for:
  - full alert-rule YAML authoring
  - Alertmanager routing and notification design
  - Grafana dashboard layout, provisioning, or panel configuration
  - Prometheus server configuration, scraping, or relabeling rules
  - metric naming conventions, exposition format design, or application instrumentation library setup
