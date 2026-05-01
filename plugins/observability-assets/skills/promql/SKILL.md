---
name: promql
description: >-
  Write and review PromQL expressions for correctness, aggregation and matching shape, and consumer fit (dashboard, alert, recording rule). Use this skill when writing or reviewing PromQL expressions, debugging query correctness, adding aggregation or matching logic, tuning dashboard or alert queries, working with selectors, aggregations, functions, vector matching, offset/@ modifiers, subqueries, histogram quantiles, recording rules, HTTP API interactions, or needing a complete offline PromQL language reference.
---

# PromQL

Write and review PromQL queries that stay readable, correct, and appropriate for the consumer that will use them.

## Common-Case Workflow

1. Start from the operator question the query must answer.
2. Choose the metric and label matchers deliberately instead of broad selectors that happen to work in one environment.
3. Decide whether the expression should start from an instant vector or a range vector.
4. Apply aggregation, binary operators, or matching rules only after the label set and output shape are clear.
5. Choose functions that fit the consumer: prefer `rate()` for alerts and recording rules, and use `irate()` only when fast-moving dashboard visualization is the goal.
6. Review the final label set, units, and readability before reusing the query in an alert or dashboard.

## Data Types

PromQL expressions evaluate to one of four types:

| Type | Definition | Where it appears |
| --- | --- | --- |
| **Instant vector** | Set of time series, each with a single sample at the same timestamp | Result of instant-vector selectors, most functions, binary operators |
| **Range vector** | Set of time series, each with a range of samples over time | Result of range-vector selectors (e.g., `metric[5m]`), subqueries |
| **Scalar** | A single numeric floating-point value | Literal numbers, `scalar()`, `vector()`, some function results |
| **String** | A simple string value | String literals; currently unused as a standalone result type |

Instant queries accept any type as root. Range queries only accept scalar or instant vector as root result type.

Both vectors may contain a mix of float samples and native histogram samples. Float samples carry counter or gauge flavor by convention (counters typically end in `_total`). Native histograms store their flavor explicitly (counter histogram vs gauge histogram).

## Literals

### String literals

Three forms; all follow Go escaping rules for single/double-quoted forms:

```promql
"double-quoted with \n escapes"
'single-quoted with \t escapes'
`backtick: no escape processing`
```

Escape sequences in single/double quotes: `\a`, `\b`, `\f`, `\n`, `\r`, `\t`, `\v`, `\\`. Octal (`\nnn`), hex (`\xnn`), Unicode (`\unnnn`, `\Unnnnnnnn`). Backtick strings preserve all characters literally including newlines.

### Float literals

```text
23          -2.43    3.4e-9    0x8f    -Inf    NaN
1_000_000   .123_456_789   0x_53_AB_F3_82
```

Underscores may appear between decimal or hex digits for readability. Special values: `NaN`, `Inf`, `-Inf`.

### Time duration literals

Suffix a decimal integer with time units. Units must be ordered longest-to-shortest; each unit appears at most once per literal.

| Unit | Meaning |
| --- | --- |
| `ms` | milliseconds (`0.001`s) |
| `s` | seconds |
| `m` | minutes (`60`s) |
| `h` | hours (`60`m) |
| `d` | days (`24`h) |
| `w` | weeks (`7`d) |
| `y` | years (`365`d) |

```promql
5m        1h30m      12h34m56s      54s321ms      -2h
```

Invalid: `0xABm` (no hex suffix), `1.5h` (no float suffix), `+Infd` (no Inf/NaN suffix).

## Selectors

### Instant vector selector

Returns the most recent sample at-or-before evaluation time for each matched series. Series are returned only if their most recent sample falls within the lookback delta (default 5 minutes).

These examples show, in order, a metric-name-only selector, exact-match filtering, regex plus negation, regex matching on `__name__`, and the `__name__` workaround for reserved keywords.

```promql
http_requests_total
http_requests_total{job="api", method="GET"}
http_requests_total{env=~"staging|dev", method!="GET"}
{__name__=~"http_.*"}
{__name__="on"}
```

Label matcher operators:

| Operator | Meaning |
| --- | --- |
| `=` | Exactly equal |
| `!=` | Not equal |
| `=~` | Regex match (fully anchored) |
| `!~` | Not regex match (fully anchored) |

Regex matches are fully anchored: `env=~"foo"` means `env=~"^foo$"`. RE2 syntax.

**Empty-value matching**: `{environment=""}` matches series where `environment` is absent OR set to empty string. This also selects series that do not have the label at all.

**Multiple matchers on same label**: All must pass (AND semantics).

**Selector validity rules**:

- Must specify a metric name OR at least one non-empty-matching label matcher.
- `{job=~".*"}` is illegal (matches empty). Use `{job=~".+"}` or add a second matcher like `{method="get"}`.
- Metric name MUST NOT be a reserved keyword: `bool`, `on`, `ignoring`, `group_left`, `group_right`.
- Workaround for keywords: `{__name__="on"}`.

### Range vector selector

Appends `[<duration>]` to an instant vector selector. Returns a range of samples for each matched series. The interval is left-open, right-closed: samples at the left boundary are excluded; samples at the right boundary are included.

These examples show a five-minute range selector on a filtered metric and the common pattern of wrapping a range selector in `rate()`.

```promql
http_requests_total{job="api"}[5m]
rate(http_requests_total[5m])
```

## Modifiers

### Offset modifier

Shifts the evaluation time of an instant or range vector. **Must follow the selector immediately**, before any aggregation or function wrapping.

The examples below show, in order, valid placement inside an aggregation, invalid placement outside the aggregation, range-vector use, and a negative offset that looks forward in time.

```promql
sum(http_requests_total{method="GET"} offset 5m)

sum(http_requests_total{method="GET"}) offset 5m

rate(http_requests_total[5m] offset 1w)

rate(http_requests_total[5m] offset -1w)
```

### @ modifier (evaluation-time override)

Overrides the evaluation timestamp for individual instant or range vectors. Accepts a Unix timestamp (float literal), `start()`, or `end()`. **Must follow the selector immediately**, same placement rule as `offset`.

The examples below show a fixed Unix timestamp, placement inside an aggregation, range-vector use, range-query start and end pinning, and the equivalent `@`/`offset` orderings.

```promql
http_requests_total @ 1609746000
sum(http_requests_total{method="GET"} @ 1609746000)
rate(http_requests_total[5m] @ 1609746000)
http_requests_total @ start()
rate(http_requests_total[5m] @ end())

http_requests_total @ 1609746000 offset 5m
http_requests_total offset 5m @ 1609746000
```

For range queries: `start()` resolves to the range start, `end()` to the range end (both constant across steps). For instant queries: both resolve to the evaluation time.

## Subquery Syntax

Runs an instant query over a range at a given resolution, producing a range vector.

```text
<instant_query> '[' <range> ':' [<resolution> ']' [ @ <float_literal> ] [ offset <float_literal> ]
```

Resolution defaults to the global evaluation interval if omitted.

The examples below show a subquery sampled every minute over a five-minute rate window and a subquery pinned with `@ start()`.

```promql
avg_over_time(rate(http_requests_total[5m:1m])[1h:])

sum by (job) (increase(http_requests_total[1m:30s] @ start()))
```

## Operators

### Unary operator

| Operator | Operand types | Result |
| --- | --- | --- |
| `-` | scalar or instant vector | Sign inverted. For histograms: inverts bucket populations, count, sum. Result is always gauge histogram flavor. |

Negative histograms are intermediate-only; they cannot be ingested or exchanged via any format.

### Arithmetic operators

| Operator | Meaning |
| --- | --- |
| `+` | Addition |
| `-` | Subtraction |
| `*` | Multiplication |
| `/` | Division |
| `%` | Modulo |
| `^` | Power/exponentiation (right-associative) |
| `atan2` | Arctangent of y/x (binary, radians, float samples only) |

Defined between: scalar/scalar, vector/scalar, vector/vector (with vector matching).

**Histogram behavior in arithmetic**:

- `* scalar`: multiplies buckets, count, sum. Negative scalar produces gauge histogram.
- `/ scalar` (histogram on LHS): divides buckets, count, sum. Division by zero yields Inf/NaN per component. Negative scalar produces gauge histogram.
- All other scalar/histogram combinations: element removed (info annotation).
- Vector/vector: only `+` and `-` valid between two histograms. All others remove element (info annotation).
- **Metric name is always dropped** in any arithmetic operation involving vectors, even if `__name__` is in `on(...)`.

### Histogram trim operators

| Operator | Meaning |
| --- | --- |
| `</` | Trim upper: removes observations above threshold |
| `>/` | Trim lower: removes observations below threshold |

LHS must be a native histogram (exponential or NHCB). RHS is a float threshold. Interpolation applied when threshold does not align to bucket boundary.

### Comparison operators

| Operator | Default behavior | With `bool` modifier |
| --- | --- | --- |
| `==` | Filter: keep only matching elements | Return 1 for true, 0 for false |
| `!=` | Filter: keep only non-matching elements | Return 1 for true, 0 for false |
| `>` | Filter: keep greater-than elements | Return 1 for true, 0 for false |
| `<` | Filter: keep less-than elements | Return 1 for true, 0 for false |
| `>=` | Filter: keep greater-or-equal elements | Return 1 for true, 0 for false |
| `<=` | Filter: keep less-or-equal elements | Return 1 for true, 0 for false |

Defined between: scalar/scalar (requires `bool`), vector/scalar, vector/vector (with matching).

With `bool`: unmatched elements return no result (not 0). Metric name is dropped. Without `bool`: LHS metric name retained (unless `on` used; `group_right` keeps RHS name).

### Logical / set operators (instant vectors only)

| Operator | Meaning |
| --- | --- |
| `and` | Intersection: LHS elements that have an exact label-set match in RHS |
| `or` | Union: all LHS elements plus RHS elements without matching label sets in LHS |
| `unless` | Complement: LHS elements that have NO exact label-set match in RHS |

These operate on label sets only; sample values are irrelevant. Work identically for float and histogram samples.

### Operator precedence (highest to lowest)

| Precedence | Operators | Associativity |
| --- | --- | --- |
| 1 | `^` | Right |
| 2 | `*`, `/`, `%`, `atan2` | Left |
| 3 | `+`, `-` | Left |
| 4 | `==`, `!=`, `<=`, `<`, `>=`, `>` | Left |
| 5 | `and`, `unless` | Left |
| 6 | `or` | Left |

Example: `2 * 3 % 2` = `(2 * 3) % 2`. Example: `2 ^ 3 ^ 2` = `2 ^ (3 ^ 2)`.

## Aggregation Operators

Syntax: `<aggr-op> [without \| by (<label list>)] ([parameter,] <vector expression>)`

The `by`/`without` clause may appear before or after the expression.

| Operator | Signature | Behavior |
| --- | --- | --- |
| `sum(v)` | Sum over dimensions | Sums sample values. Mixed float/histogram in one group: warn, drop element. |
| `avg(v)` | Arithmetic mean over dimensions | Sum divided by count. Same mixed-type restriction as `sum`. |
| `min(v)` | Minimum over dimensions | Returns minimum float value. Ignores histogram samples (info). NaN considered only if all values are NaN. |
| `max(v)` | Maximum over dimensions | Returns maximum float value. Ignores histogram samples (info). NaN considered only if all values are NaN. |
| `group(v)` | Group existence | Returns 1 for each group containing any value. Works for floats and histograms. |
| `count(v)` | Count of elements | Returns number of series. Works for floats and histograms. |
| `count_values(label, v)` | Value frequency count | One series per unique value; new `label` holds the value. Works for floats and histograms. |
| `stddev(v)` | Population standard deviation | Float only. Ignores histograms (info). |
| `stdvar(v)` | Population variance | Float only. Ignores histograms (info). |
| `quantile(phi, v)` | phi-quantile (0..1) | Float only. Ignores histograms (info). NaN is smallest possible. `phi=NaN` -> NaN; `phi<0` -> -Inf; `phi>1` -> +Inf. |
| `topk(k, v)` | Largest k elements | Preserves original labels. `by`/`without` only bucket input. Float only. NaN farthest from top. |
| `bottomk(k, v)` | Smallest k elements | Same rules as `topk`, ascending order. |
| `limitk(k, v)` | Sample k elements (experimental) | Deterministic pseudo-random selection. Works for floats and histograms. Requires feature flag. |
| `limit_ratio(r, v)` | Sample ratio r (experimental) | Pseudo-random; abs(r) is ratio, negative r inverts selection. Requires feature flag. |

`without` removes listed labels (keeps everything else). `by` keeps only listed labels (drops everything else).

## Function Reference

### Counter and rate functions

| Function | Signature | Notes |
| --- | --- | --- |
| `rate(v range-vector)` | Per-second average rate | Auto-adjusts counter resets. Extrapolates to range ends. Use with counters. Best for alerts/recording rules. Always apply before aggregation. |
| `irate(v range-vector)` | Per-second instant rate (last 2 points) | Auto-adjusts resets. Use only for volatile dashboards. Always apply before aggregation. |
| `increase(v range-vector)` | Total increase over range | Syntactic sugar: `rate(v) * range_seconds`. Human-readable. Use with counters. |
| `resets(v range-vector)` | Number of counter resets | Counts decreases between consecutive samples. Use with counters. |
| `delta(v range-vector)` | First-last difference | No reset adjustment. Use with gauges only. |
| `idelta(v range-vector)` | Last-two-sample difference | No reset adjustment. Use with gauges only. |
| `deriv(v range-vector)` | Per-second derivative (linear regression) | Needs >= 2 float samples. Gauges only. Float only. |
| `predict_linear(v, t)` | Predict value t seconds ahead | Linear regression. Gauges only. Float only. |
| `double_exponential_smoothing(v, sf, tf)` | Holt linear smoothing (experimental) | `sf` is the smoothing factor; lower values give more weight to older data. `tf` is the trend factor; higher values consider trends more strongly. Both are in [0,1]. Gauges only. Float only. Feature flag required. Formerly `holt_winters`. |

### Histogram functions

| Function | Signature | Notes |
| --- | --- | --- |
| `histogram_quantile(phi, b)` | phi-quantile from classic/native histogram | `b` must be rate() of bucket metric (classic) or rate() of native histogram. Classic needs `le` label in `by`. Interpolates within buckets. |
| `histogram_quantiles(b, label, p1, p2, ...)` | Multiple quantiles (experimental) | 1-10 phi values. Output labeled by `label`. Feature flag required. |
| `histogram_fraction(lo, hi, b)` | Fraction of obs between lo and hi | Works on classic (float=bucket counts) and native histograms. Interpolates at boundaries. |
| `histogram_avg(b)` | Mean observation value | Native histograms only. Ignores floats. Equivalent to `histogram_sum/histogram_count`. |
| `histogram_count(b)` | Observation count | Native histograms only. Ignores floats. |
| `histogram_sum(b)` | Observation sum | Native histograms only. Ignores floats. |
| `histogram_stddev(b)` | Estimated standard deviation | Native histograms only. Uses geometric mean for exponential buckets. |
| `histogram_stdvar(b)` | Estimated variance | Native histograms only. |

### Mathematical functions (float only, ignore histograms)

| Function | Signature | Notes |
| --- | --- | --- |
| `abs(v)` | Absolute value | |
| `ceil(v)` | Round up to nearest integer | `ceil(+Inf)=+Inf`, `ceil(+-0)=+-0` |
| `floor(v)` | Round down to nearest integer | `floor(+Inf)=+Inf`, `floor(+-0)=+-0` |
| `round(v, to_nearest=1)` | Round to nearest multiple | Ties round up. `to_nearest` can be fractional. |
| `clamp(v, min, max)` | Clamp to [min, max] | Empty vector if min > max. NaN if min/max is NaN. |
| `clamp_max(v, max)` | Upper clamp | |
| `clamp_min(v, min)` | Lower clamp | |
| `exp(v)` | e^x | `exp(+Inf)=+Inf`, `exp(NaN)=NaN` |
| `ln(v)` | Natural log | `ln(0)=-Inf`, `ln(x<0)=NaN` |
| `log2(v)` | Base-2 log | Same special cases as `ln`. |
| `log10(v)` | Base-10 log | Same special cases as `ln`. |
| `sqrt(v)` | Square root | |
| `sgn(v)` | Sign: 1, -1, or 0 | |

### Trigonometric functions (radians, float only, ignore histograms)

| Function | Signature |
| --- | --- |
| `acos(v)` | Arccosine |
| `acosh(v)` | Inverse hyperbolic cosine |
| `asin(v)` | Arcsine |
| `asinh(v)` | Inverse hyperbolic sine |
| `atan(v)` | Arctangent |
| `atanh(v)` | Inverse hyperbolic tangent |
| `cos(v)` | Cosine |
| `cosh(v)` | Hyperbolic cosine |
| `sin(v)` | Sine |
| `sinh(v)` | Hyperbolic sine |
| `tan(v)` | Tangent |
| `tanh(v)` | Hyperbolic tangent |
| `deg(v)` | Radians to degrees |
| `rad(v)` | Degrees to radians |
| `pi()` | Returns pi (no arguments) |

### Time and date functions (default argument: `vector(time())`, ignore histograms)

| Function | Signature | Returns |
| --- | --- | --- |
| `time()` | Seconds since 1970-01-01 UTC | Evaluation timestamp (scalar) |
| `timestamp(v)` | Timestamp of each sample | Unix timestamp per series |
| `year(v)` | Year (UTC) | e.g., 2026 |
| `month(v)` | Month (UTC) | 1-12 |
| `day_of_month(v)` | Day of month (UTC) | 1-31 |
| `day_of_week(v)` | Day of week (UTC) | 0=Sunday .. 6=Saturday |
| `day_of_year(v)` | Day of year (UTC) | 1-365/366 |
| `days_in_month(v)` | Days in month (UTC) | 28-31 |
| `hour(v)` | Hour (UTC) | 0-23 |
| `minute(v)` | Minute (UTC) | 0-59 |

### Sorting functions

| Function | Signature | Notes |
| --- | --- | --- |
| `sort(v)` | Ascending by sample value | Instant queries only. Ignores histograms. |
| `sort_desc(v)` | Descending by sample value | Instant queries only. Ignores histograms. |
| `sort_by_label(v, label, ...)` | Ascending by label values (experimental) | Natural sort order. Instant queries only. Feature flag required. |
| `sort_by_label_desc(v, label, ...)` | Descending by label values (experimental) | Natural sort order. Instant queries only. Feature flag required. |

### Vector conversion and utility functions

| Function | Signature | Notes |
| --- | --- | --- |
| `scalar(v)` | Single float sample to scalar | Returns NaN if not exactly one float element. Ignores histograms. |
| `vector(s)` | Scalar to single-element instant vector | No labels. |
| `absent(v)` | 1 if vector empty, else empty | Derives labels from input. Useful for "series disappeared" alerts. |
| `label_join(v, dst, sep, src1, src2, ...)` | Join label values into new label | Any number of source labels. |
| `label_replace(v, dst, replacement, src, regex)` | Regex-based label transform | Capture groups `$1`, `$2`, `$name`. Returns unchanged if no match. |
| `changes(v range-vector)` | Number of value changes | Float->histogram or histogram->float counts as a change. |
| `info(v, [selector])` | Enrich with info metric labels (experimental) | Auto-discovers target_info. Selector uses `{...}` matcher syntax. Feature flag required. |

### `_over_time` functions (range-vector input)

Each aggregates all samples in the range per series, returning an instant vector.

| Function | Signature | Notes |
| --- | --- | --- |
| `avg_over_time(v)` | Average value | Float or histogram (with aggregation semantics). |
| `min_over_time(v)` | Minimum value | Float only. |
| `max_over_time(v)` | Maximum value | Float only. |
| `sum_over_time(v)` | Sum of values | Float or histogram (with aggregation semantics). |
| `count_over_time(v)` | Sample count | Float and histogram. |
| `quantile_over_time(phi, v)` | phi-quantile | Float only. |
| `stddev_over_time(v)` | Population std dev | Float only. |
| `stdvar_over_time(v)` | Population variance | Float only. |
| `last_over_time(v)` | Most recent sample | Float and histogram. |
| `present_over_time(v)` | 1 if any sample exists | Float and histogram. |
| `absent_over_time(v)` | 1 if range empty, else empty | Like `absent` but for a time window. |
| `first_over_time(v)` | Oldest sample (experimental) | Float and histogram. Feature flag required. |
| `mad_over_time(v)` | Median absolute deviation (experimental) | Float only. Feature flag required. |
| `ts_of_first_over_time(v)` | Timestamp of first sample (experimental) | Float only. Feature flag required. |
| `ts_of_last_over_time(v)` | Timestamp of last sample (experimental) | Float only. Feature flag required. |
| `ts_of_min_over_time(v)` | Timestamp of last min sample (experimental) | Float only. Feature flag required. |
| `ts_of_max_over_time(v)` | Timestamp of last max sample (experimental) | Float only. Feature flag required. |

All samples in the range have equal weight regardless of spacing. `first_over_time(m[1m])` differs from `m offset 1m`: the former selects within the range, the latter selects from the lookback interval prior to the offset.

## Vector Matching

Binary operations between two instant vectors find matching entries by label set. Two modes exist:

### One-to-one matching (default)

Entries match when they share the same label set (default), or when they match on a reduced label set.

The examples below show default label-set matching, dropping `code` during comparison, and restricting the join key to `method` only.

```promql
method_code:http_errors:rate5m / method:http_requests:rate5m

method_code:http_errors:rate5m{code="500"} / ignoring(code) method:http_requests:rate5m

method_code:http_errors:rate5m on(method) method:http_requests:rate5m
```

### Many-to-one / one-to-many matching

Required when one side has higher cardinality than the other per join key. Must use `group_left` or `group_right`.

The first example keeps the higher-cardinality left-hand series; the second also propagates `instance` from the lower-cardinality side into the result.

```promql
method_code:http_errors:rate5m / ignoring(code) group_left method:http_requests:rate5m

method_code:http_errors:rate5m / ignoring(code) group_left(instance) method:http_requests:rate5m
```

The optional label list after `group_left`/`group_right` specifies labels from the "one" side to propagate into the result. With `on`, a label cannot appear in both lists.

### Fill modifiers (experimental)

Override default behavior of dropping unmatched elements. Requires `--enable-feature=promql-binop-fill-modifiers`.

These examples show filling missing matches on both sides, only on the left, only on the right, and on both sides with explicit defaults.

```promql
expr1 / fill(0) expr2

expr1 / fill_left(0) expr2

expr1 / fill_right(0) expr2

expr1 / fill_left(0) fill_right(0) expr2
```

Fill modifiers go last, after `bool`, `on`, `ignoring`, `group_left`, `group_right`. Not supported for set operators (`and`, `or`, `unless`). Only float samples supported (no histograms).

## Staleness Model

Prometheus assigns values at query-evaluation timestamps independently of actual sample timestamps. It takes the newest sample within the **lookback delta** (default 5 minutes; configurable via `--query.lookback-delta` flag or per-query `lookback_delta` parameter).

Key behaviors:

- When a target stops exporting a series, the series is marked **stale**.
- Stale series disappear from graphs at their latest sample timestamp.
- After staleness marking, queries return no value for that series until new samples arrive.
- Exporters with self-assigned timestamps behave differently: stale series hold their last value for the lookback delta before disappearing (configurable via `track_timestamps_staleness`).
- Bare metric name selectors can expand to thousands of series; always filter and aggregate before graphing unknown data.

## HTTP API Reference

Base path: `/api/v1`. All responses use this JSON envelope:

```json
{
  "status": "success" | "error",
  "data": <result>,
  "errorType": "<string>",
  "error": "<string>",
  "warnings": ["<string>"],
  "infos": ["<string>"]
}
```

### Expression query endpoints

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/api/v1/query` | GET/POST | Instant query at a single timestamp |
| `/api/v1/query_range` | GET/POST | Query over a time range with step resolution |
| `/api/v1/format_query` | GET/POST | Pretty-format a PromQL expression |
| `/api/v1/parse_query` | GET/POST | Parse expression to AST (experimental) |

**Instant query parameters**: `query`, `time` (optional), `timeout` (optional), `limit` (optional), `lookback_delta` (optional), `stats` (optional).

**Range query parameters**: `query`, `start`, `end`, `step`, `timeout` (optional), `limit` (optional), `lookback_delta` (optional), `stats` (optional).

Timestamps accept RFC3339 or Unix (seconds, optional decimals). Durations use PromQL time-unit syntax. POST accepts URL-encoded body for large queries.

### Result formats

| resultType | Shape |
| --- | --- |
| `vector` (instant vector) | `[{metric: {...}, value: [ts, val]}, ...]` |
| `matrix` (range vector) | `[{metric: {...}, values: [[ts, val], ...]}, ...]` |
| `scalar` | `[ts, val]` |
| `string` | `[ts, str]` |

Sample values are JSON strings (to accommodate NaN, Inf, -Inf). Native histograms include a `"histogram"` key with count, sum, and buckets array.

### Metadata and discovery endpoints

| Endpoint | Purpose |
| --- | --- |
| `/api/v1/series` | Find series by label matchers (`match[]` required) |
| `/api/v1/labels` | List label names (filterable by `match[]`) |
| `/api/v1/label/<name>/values` | List values for a label name |
| `/api/v1/targets/metadata` | Metric metadata (type, help, unit) per target |
| `/api/v1/metadata` | Metric metadata aggregated across targets |
| `/api/v1/query_exemplars` | Exemplars for a query over a time range (experimental) |

### Operational endpoints

| Endpoint | Purpose |
| --- | --- |
| `/api/v1/targets` | Target discovery state (active/dropped) |
| `/api/v1/scrape_pools` | Configured scrape pool names |
| `/api/v1/targets/relabel_steps` | Relabeling debug for a target (experimental) |
| `/api/v1/rules` | Recording and alerting rules, active alerts |
| `/api/v1/alerts` | Active alerts only |
| `/api/v1/alertmanagers` | Alertmanager discovery state |
| `/api/v1/status/config` | Current YAML config |
| `/api/v1/status/flags` | Command-line flags |
| `/api/v1/status/runtimeinfo` | Server runtime properties |
| `/api/v1/status/buildinfo` | Build version info |
| `/api/v1/openapi.yaml` | Full OpenAPI spec (add `?openapi_version=3.2` for extended) |

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
- use `label_replace()` only when you must reshape labels explicitly; avoid it when a simpler selector, aggregation, or recording rule keeps the query readable
- use `absent()` or `absent_over_time()` when the missing series itself is the signal rather than a zero-valued metric
- use `delta()` and `idelta()` with gauges only; never with counters (no reset adjustment)
- use `predict_linear()` with gauges only; for capacity forecasting based on trend
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

Many-to-one enrichment -- attach metadata labels from an info metric:

```promql
rate(container_cpu_usage_seconds_total{job="kubelet"}[5m])
* on (namespace, pod)
group_left(node)
kube_pod_info
```

Use when: enriching a metric with labels from a uniquely-scoped metadata series.

Missing-series shape -- page on disappearance instead of on a low numeric threshold:

```promql
absent(up{job="api"})
```

Use when: the query should return a signal only when the expected series is missing.

Temporal comparison -- compare current value against past using offset:

```promql
rate(http_requests_total[5m])
/ rate(http_requests_total[5m] offset 1w) > 1.5
```

Use when: detecting week-over-week changes in a counter rate.

Subquery for downsampling -- compute average over coarse windows:

```promql
avg_over_time(rate(http_requests_total[5m])[1h:1m])
```

Use when: smoothing a rate into hourly averages at 1-minute resolution.

Histogram quantile (classic) -- aggregate then compute percentile:

```promql
histogram_quantile(
  0.99,
  sum by (job, le) (rate(http_request_duration_seconds_bucket[5m]))
)
```

Use when: computing the 99th percentile request duration per job from classic histogram buckets.

Histogram quantile (native) -- simpler aggregation without `le`:

```promql
histogram_quantile(
  0.99,
  sum by (job) (rate(http_request_duration_seconds[5m]))
)
```

Use when: computing percentiles from native histograms.

Gauge prediction -- forecast disk fill:

```promql
predict_linear(node_filesystem_avail_bytes{mount="/data"}[1h], 4 * 3600) < 0
```

Use when: predicting whether a gauge will cross zero within a future window.

## Validate the Result

Validate the common case with these checks:

- selectors and label matchers target the intended series set without accidental overreach
- the query uses the right vector type for the chosen function (instant vs range)
- aggregation keeps the labels you need and removes the ones you do not
- binary operators and matching rules are explicit where label-set alignment matters
- `offset` and `@` modifiers follow the selector immediately, not outside the wrapping function/aggregation
- `rate()` versus `irate()` matches the consumer context
- counter functions (`rate`, `irate`, `increase`) are applied before aggregation so resets are detected correctly
- the final expression is readable enough that another operator can review it quickly

## Output contract

Return:

1. the recommended query or review decision
2. the intended consumer context such as alert, dashboard, or recording rule
3. any required label-set, vector-matching, or function-choice rationale
4. remaining blockers, assumptions, or follow-up query risks

## References

| If the blocker is... | Read... |
| --- | --- |
| non-trivial vector matching, label-set alignment, worked input/output examples, or refactoring a complex query without changing meaning | [`./references/query-shaping.md`](./references/query-shaping.md) |
| histogram bucket interpolation behavior, native vs classic histogram differences, or advanced histogram_fraction edge cases | [`./references/histogram-details.md`](./references/histogram-details.md) |
| experimental functions, feature flags, or bleeding-edge PromQL features | [`./references/experimental-features.md`](./references/experimental-features.md) |

## Invariants

- MUST choose selectors and label matchers deliberately.
- MUST keep the ordinary PromQL authoring path understandable from this file alone.
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
