---
title: "PromQL Query Shaping Reference"
description: "Open this when vector matching, label-set alignment, complex query refactoring, or understanding exact matching output is the blocker."
---

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

## Worked Example: One-to-One Matching with `ignoring`

**Input data** (two metrics with different label sets):

```
# LHS: error counts by method and HTTP status code
method_code:http_errors:rate5m{method="get", code="500"}  => 24
method_code:http_errors:rate5m{method="get", code="404"}  => 30
method_code:http_errors:rate5m{method="put", code="501"}  => 3
method_code:http_errors:rate5m{method="post", code="500"} => 6
method_code:http_errors:rate5m{method="post", code="404"} => 21

# RHS: total requests by method
method:http_requests:rate5m{method="get"}   => 600
method:http_requests:rate5m{method="del"}    => 34
method:http_requests:rate5m{method="post"}  => 120
```

**Query**: fraction of requests that are 500 errors, per method.

```promql
method_code:http_errors:rate5m{code="500"}
/
ignoring(code)
method:http_requests:rate5m
```

**Matching process**: `ignoring(code)` drops `code` from LHS labels before comparison. LHS becomes `{method="get"}` (value 24) and `{method="post"}` (value 6). These match RHS `{method="get"}` (600) and `{method="post"}` (120). The `{method="put"}` entry on LHS has no match (code=501 was filtered out) and `{method="del"}` on RHS has no match.

**Output**:

```
{method="get"}  0.04     # 24 / 600
{method="post"} 0.05     # 6  / 120
```

Entries with no match on either side are dropped (default behavior).

## Worked Example: Many-to-One Matching with `group_left`

Same input data as above. This time we want error rate **per status code**, not aggregated.

**Query**: error count divided by total requests, keeping all codes.

```promql
method_code:http_errors:rate5m
/
ignoring(code)
group_left
method:http_requests:rate5m
```

**Matching process**: LHS has multiple entries per `method` value (one per `code`). RHS has one entry per `method`. `group_left` declares that LHS is the "many" side. Each RHS entry matches against all LHS entries sharing the same `method`.

**Output**:

```
{method="get",  code="500"} 0.04    # 24 / 600
{method="get",  code="404"} 0.05    # 30 / 600
{method="post", code="500"} 0.05    # 6  / 120
{method="post", code="404"} 0.175   # 21 / 120
```

All LHS labels survive in the output because `group_left` propagates the "many"-side identity.

## Worked Example: `group_left` with Label Propagation

When you need extra labels from the "one" side to appear in the result:

```promql
rate(container_cpu_usage_seconds_total{job="kubelet"}[5m])
* on (namespace, pod)
group_left(node)
kube_pod_info
```

Here `kube_pod_info` (the "one" side) carries the `node` label that does not exist on the CPU metric. `group_left(node)` pulls `node` into each output series. Without it, the `node` label would be dropped during matching.

**Before using this pattern**, verify:
- the right-hand metric (`kube_pod_info`) has exactly one series per `(namespace, pod)` pair within the query scope
- in federated or multi-cluster setups, add provenance labels (e.g., `cluster`) to ensure uniqueness

## Worked Example: Set Operator Behavior

**Input data**:

```
# Vector A
up{job="api", instance="a"}      => 1
up{job="api", instance="b"}      => 0
up{job="db",  instance="c"}      => 1

# Vector B
up{job="api", instance="a"}      => 1
up{job="db",  instance="d"}      => 0
```

**`A and B`** -- intersection by exact label set:

```
{job="api", instance="a"} => 1   # only exact label-set match survives; value from A
```

**`A or B`** -- union (all of A plus non-matching from B):

```
{job="api", instance="a"} => 1   # from A (also in B)
{job="api", instance="b"} => 0   # from A only
{job="db",  instance="c"} => 1   # from A only
{job="db",  instance="d"} => 0   # from B only (no match in A)
```

**`A unless B`** -- complement (A minus matching B):

```
{job="api", instance="b"} => 0   # in A, not in B
{job="db",  instance="c"} => 1   # in A, not in B
```

The `{job="api", instance="a"}` entry is dropped because it exists in both vectors.

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

Function-family tradeoffs are covered in the common path of [`../SKILL.md`](../SKILL.md). Use this reference for matching escalation, label-set review, `group_left`/`group_right` justification, and worked input/output examples only.
