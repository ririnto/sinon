---
title: "PromQL Query Shaping Reference"
description: "Open this when vector matching, label-set alignment, or complex query refactoring is the blocker."
---

# PromQL Query Shaping Reference

Use this reference when the query already has the right general goal, but the blocker is how to preserve meaning while changing shape.

## Matching Escalation Order

Start with the smallest matching rule that preserves the intended label set.

1. no modifier when both sides already align
2. `ignoring (...)` when only a few labels should be dropped from matching
3. `on (...)` when only a few labels should be used as the join key
4. `group_left` or `group_right` only when one-to-many or many-to-one output is truly required

Use this order when the blocker is deciding whether a binary operation really needs advanced matching.

## Label-Set Review

Before changing a binary operator or aggregation, answer these questions explicitly:

- which labels must survive in the final output
- which labels are only intermediate noise
- whether the query is producing one series per target, one per service, or one global series

If you cannot answer those questions, the query is not ready for refactoring.

## Readability-Preserving Refactor Pattern

When a query is correct but hard to review, improve it in this order:

1. normalize selectors and label matcher ordering
2. break long binary expressions across lines at operator boundaries
3. make `by (...)`, `without (...)`, `on (...)`, or `ignoring (...)` explicit
4. promote repeated expensive expressions into a recording rule if the query is reused broadly

Broken:

```promql
sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))/sum(rate(http_requests_total{job="api"}[5m]))
```

Better:

```promql
sum(rate(http_requests_total{job="api",status=~"5.."}[5m]))
/
sum(rate(http_requests_total{job="api"}[5m]))
```

## When `group_left` or `group_right` Is Justified

Use `group_left` or `group_right` only when one side carries the join key and the other side carries extra labels that must survive.

Before you copy this pattern, verify that the right-hand metric is a trusted info-style series with exactly one matching series for each join key in the query scope. In federated, multi-cluster, or duplicated-scrape setups, add the extra provenance labels needed to make that uniqueness true before you join.

Example:

```promql
rate(container_cpu_usage_seconds_total{job="kubelet"}[5m])
* on (namespace, pod)
group_left(node)
kube_pod_info
```

Use this when the blocker is enriching one metric with labels from another metric without losing the primary series identity, and the right-hand metadata series is uniquely scoped for the same trust boundary as the left-hand data.

## Function-Family Choice

When several functions seem plausible, pick by consumer intent:

- `rate()` for stable alerting or recorded signals
- `irate()` for visually volatile dashboard panels
- `increase()` when operators need a total increase over the window rather than a per-second rate
- `absent()` or `absent_over_time()` when the missing series itself is the signal

Use this reference for function-family tradeoffs, not for the baseline meanings of those functions. The common path stays in [`../SKILL.md`](../SKILL.md).
