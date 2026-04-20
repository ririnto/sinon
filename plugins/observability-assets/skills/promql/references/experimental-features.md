---
title: "PromQL Experimental Features"
description: "Open this when using experimental functions, feature flags, fill modifiers, or bleeding-edge PromQL capabilities."
---

Use this reference when the query requires a function or operator that is gated behind a Prometheus feature flag, or when you need to know which features may change or be removed in future versions.

## Feature Flags

| Flag | Purpose |
| --- | --- |
| `--enable-feature=promql-experimental-functions` | Enables experimental functions: `double_exponential_smoothing`, `histogram_quantiles`, `info`, `limitk`, `limit_ratio`, `mad_over_time`, `first_over_time`, `ts_of_first_over_time`, `ts_of_last_over_time`, `ts_of_min_over_time`, `ts_of_max_over_time`, `sort_by_label`, `sort_by_label_desc` |
| `--enable-feature=promql-binop-fill-modifiers` | Enables `fill()`, `fill_left()`, `fill_right()` modifiers for binary operations |
| `--enable-feature=promql-duration-expr` | Enables `step()` as a valid duration expression in range selectors and subqueries |

## Experimental Functions Summary

### Smoothing and prediction

| Function | Status | Notes |
| --- | --- | --- |
| `double_exponential_smoothing(v, sf, tf)` | Experimental | Formerly `holt_winters`. Holt Linear method. sf=trend factor, tf=smoothing factor. Both in [0,1]. Gauges only. |

### Multi-quantile computation

| Function | Status | Notes |
| --- | --- | --- |
| `histogram_quantiles(b, label, p1, p2, ...)` | Experimental | 1-10 phi values at once. Output series labeled by `label`. Same interpolation as `histogram_quantile`. |

### Info metric enrichment

| Function | Status | Notes |
| --- | --- | --- |
| `info(v, [selector])` | Experimental | Auto-discovers info metrics (default: `target_info`). Identifying labels assumed to be `instance`+`job`. Behavior may change including possible removal. |

### Sampling aggregators

| Function | Status | Notes |
| --- | --- | --- |
| `limitk(k, v)` | Experimental | Deterministic pseudo-random subset of k elements. Works on floats and histograms. |
| `limit_ratio(r, v)` | Experimental | Pseudo-random sampling by ratio r (abs(r) used; negative inverts selection). |

### Extended `_over_time` functions

| Function | Status | Notes |
| --- | --- | --- |
| `mad_over_time(v)` | Experimental | Median absolute deviation of float samples. |
| `first_over_time(v)` | Experimental | Oldest sample in the range. Float or histogram. |
| `ts_of_first_over_time(v)` | Experimental | Timestamp of earliest sample. Float only. |
| `ts_of_last_over_time(v)` | Experimental | Timestamp of last sample. Float only. |
| `ts_of_min_over_time(v)` | Experimental | Timestamp of last minimum-value sample. Float only. |
| `ts_of_max_over_time(v)` | Experimental | Timestamp of last maximum-value sample. Float only. |

### Label-based sorting

| Function | Status | Notes |
| --- | --- | --- |
| `sort_by_label(v, label, ...)` | Experimental | Natural sort order by label values. Instant queries only. |
| `sort_by_label_desc(v, label, ...)` | Experimental | Descending natural sort by label values. Instant queries only. |

## Fill Modifiers (Experimental)

Fill modifiers override the default behavior of dropping unmatched elements in binary vector operations.

```promql
# Fill missing matches on either side with 0
expr1 / fill(0) expr2

# Fill only one side
expr1 * fill_left(1) expr2
expr1 + fill_right(-1) expr2

# Fill both sides with different defaults
expr1 fill_left(0) fill_right(1) + expr2
```

Constraints:
- Value must be a numeric literal (float sample). Histogram samples not supported.
- Cannot create series missing on both sides.
- Must appear after all other modifiers (`bool`, `on`, `ignoring`, `group_left`, `group_right`).
- Not supported for set operators (`and`, `or`, `unless`).
- With `group_left`/`group_right`: filling the "many" side produces one filled series per match group. Filling the "one" side does not fill extra labels specified in the group modifier.

## Stability Caveats

- **`info()`**: Explicitly documented as an experiment whose behavior may change, including removal. The current limitation (only `target_info`, fixed identifying labels `instance`+`job`) is acknowledged as partially defeating the purpose.
- **`parse_query` endpoint**: Documented as experimental, intended for Prometheus web UI use only. Endpoint name and format may change between versions.
- **`query_exemplars` endpoint**: Experimental; format may change.
- **`targets/relabel_steps` endpoint**: Experimental; intended for UI use only.
- **`targets/metadata` endpoint**: Experimental; may change.
- All experimental functions require their respective feature flags and may change behavior or be removed in future releases.

Function-family tradeoffs and general query patterns are covered in [`../SKILL.md`](../SKILL.md). Use this reference only when you need to determine whether a function is stable, which flag enables it, or what caveats apply to experimental features.
