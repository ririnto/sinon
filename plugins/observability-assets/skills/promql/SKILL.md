---
name: promql
description: >-
  Use this skill when the user asks to "write or review a PromQL query", "fix a Prometheus expression", "add query aggregation or vector matching", "tune a dashboard or alert query", or needs guidance on PromQL authoring and query readability.
metadata:
  title: "PromQL"
  official_project_url: "https://prometheus.io/docs/prometheus/latest/querying/basics/"
  reference_doc_urls:
    - "https://prometheus.io/docs/prometheus/latest/querying/basics/"
    - "https://prometheus.io/docs/prometheus/latest/querying/operators/"
    - "https://prometheus.io/docs/prometheus/latest/querying/functions/"
    - "https://prometheus.io/docs/practices/rules/"
  version: "latest"
---

# PromQL

## Overview

Use this skill to write and review PromQL queries that stay readable, correct, and appropriate for the consumer that will use them. The common case is one query with a clear selector, the right vector type, one deliberate aggregation or function choice, and one output shape that matches either an alert condition or a dashboard panel without relying on accidental label behavior.

## Use This Skill When

- You are writing or reviewing a PromQL query.
- You need guidance on selectors, label matchers, range vectors, aggregation, functions, or vector matching.
- You need to tune one query for alerting versus dashboard use without rewriting the surrounding alert YAML or dashboard JSON.
- Do not use this skill when the main task is full alert-rule authoring, Alertmanager routing, or Grafana dashboard structure rather than the query itself.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- selectors, label matchers, and choosing the metric and label set deliberately
- instant vectors versus range vectors and when each query shape is valid
- aggregation with `by (...)` or `without (...)`
- arithmetic and comparison operators in ordinary query review
- basic vector matching when both sides already align, with explicit `on (...)` or `ignoring (...)` matching reserved for the deeper shaping reference
- core function choice such as `rate`, `increase`, `irate`, `histogram_quantile`, `label_replace`, and `absent`
- alert-oriented versus dashboard-oriented query decisions
- readability, maintainability, and query review criteria

## Version-Sensitive Features

- The ordinary path in this skill uses long-standing PromQL features and does not attach a narrower minimum Prometheus version to selectors, matchers, vectors, aggregation, binary operators, or the common functions listed here.
- If you use native histograms, treat them as version-sensitive Prometheus capability rather than a universal baseline.
- If you rely on experimental PromQL functions or feature-flagged modifiers, verify the deployed Prometheus version and enabled feature flags before treating them as available.

## Common-Case Workflow

1. Start from the operator question the query must answer.
2. Choose the metric and label matchers deliberately instead of broad selectors that happen to work in one environment.
3. Decide whether the expression should start from an instant vector or a range vector.
4. Apply aggregation, binary operators, or matching rules only after the label set and output shape are clear.
5. Choose functions that fit the consumer: prefer `rate()` for alerts and recording rules, and use `irate()` only when fast-moving dashboard visualization is the goal.
6. Review the final label set, units, and readability before reusing the query in an alert or dashboard.

## Minimal Setup

Minimal selector and aggregation shape:

```promql
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use this when: you need one readable baseline query with explicit label filtering, a range-vector function, and a stable grouped output.

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
- use `label_replace()` only when you must reshape labels explicitly; avoid it when a simpler selector, aggregation, or recording rule keeps the query readable
- use `absent()` or `absent_over_time()` when the missing series itself is the signal rather than a zero-valued metric

## Ready-to-Adapt Templates

Basic selector — use one metric and one bounded label set:

```promql
up{job="api",instance=~"api-.+"}
```

Use when: you need a simple instant-vector selector for current target health.

Range-vector aggregation — convert a counter into a grouped per-second rate:

```promql
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need one stable rate query for dashboards, recording rules, or alert thresholds.

Alert-oriented shape — keep the query stable and symptom-oriented:

```promql
5 < round(
  100 * sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
    /
  sum(rate(http_requests_total{job="api"}[5m])),
  0.001
)
```

Use when: you need a threshold query that can be embedded in an alert rule without hiding the user-facing symptom.

Dashboard-oriented shape — use `irate()` only for visually volatile counters:

```promql
sum by (instance) (irate(node_network_receive_bytes_total{job="node"}[1m]))
```

Use when: you are shaping a fast-moving dashboard panel rather than a stable alert condition.

Basic vector matching — start without a modifier when both sides already share the same grouped label set:

```promql
sum by (job) (rate(http_requests_total{job="api",status=~"5.."}[5m]))
/
sum by (job) (rate(http_requests_total{job="api"}[5m]))
```

Use when: you need to preserve one shared label set across both sides of a binary operation and both sides already align without extra matching rules.

Missing-series shape — page on disappearance instead of on a low numeric threshold:

```promql
absent(up{job="api"})
```

Use when: the query should return a signal only when the expected series is missing.

## Validate the Result

Validate the common case with these checks:

- selectors and label matchers target the intended series set without accidental overreach
- the query uses the right vector type for the chosen function
- aggregation keeps the labels you need and removes the ones you do not
- binary operators and matching rules are explicit where label-set alignment matters
- `rate()` versus `irate()` matches the consumer context
- the final expression is readable enough that another operator can review it quickly

## Output contract

Return:

1. the recommended query or review decision
2. the intended consumer context such as alert, dashboard, or recording rule
3. any required label-set, vector-matching, or function-choice rationale
4. remaining blockers, assumptions, or follow-up query risks

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| non-trivial vector matching, label-set alignment, or refactoring a complex query without changing meaning | [`./references/query-shaping.md`](./references/query-shaping.md) |

## Invariants

- MUST choose selectors and label matchers deliberately.
- MUST keep the ordinary PromQL authoring path understandable from this file alone.
- MUST use the correct vector type for the chosen function.
- SHOULD prefer `rate()` over `irate()` for alerts and recording rules.
- SHOULD make label retention and removal explicit with `by (...)` or `without (...)`.
- SHOULD keep the final query readable enough to review without reverse-engineering every label transition.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `irate()` for a stable alert query | brief spikes can reset alert behavior and create noisy evaluation | use `rate()` for alerting or recorded signals |
| aggregating before deciding which labels must survive | the final series set becomes hard to reason about | decide the output label set first, then aggregate with `by (...)` or `without (...)` |
| adding `group_left` or `group_right` before checking whether simple `on (...)` or `ignoring (...)` is enough | query semantics become harder to review and easier to break | start with one-to-one matching and escalate only when the join shape truly requires it |
| writing a query that works only because the current environment has one lucky label layout | the query breaks as soon as a label cardinality changes | make the selector and matching assumptions explicit in the query itself |

## Scope Boundaries

- Activate this skill for:
  - PromQL query authoring and review
  - selector, matcher, aggregation, function, and vector-matching choices
  - query tuning for alert or dashboard context
- Do not use this skill as the primary source for:
  - full alert-rule YAML authoring
  - Alertmanager routing and notification design
  - Grafana dashboard layout, provisioning, or panel configuration
