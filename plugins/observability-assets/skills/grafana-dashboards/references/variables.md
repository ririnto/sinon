---
title: Complete Variable Reference
description: "Open this when you need the full variable type catalog, syntax formats, global variables, format options, or advanced variable patterns."
---

# Complete Variable Reference

Use this reference when you need to understand every variable type in detail, how variable substitution works across different contexts, what global variables are available, and how to build advanced variable patterns like cascading variables and multi-variable repeats.

## All 9 Variable Types

### 1. Query Variable (`type: "query"`)

Queries a datasource at runtime to populate options. The most commonly used variable type.

```json
{
  "name": "namespace",
  "type": "query",
  "label": "Namespace",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "query": "label_values(kube_pod_info, namespace)",
  "regex": "",
  "sort": 1,
  "multi": true,
  "includeAll": true,
  "allValue": ".+",
  "current": { "selected": true, "text": ["All"], "value": ["$__all"] },
  "hide": 0,
  "options": []
}
```

**Field reference:**

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `query` | string | required | Datasource-specific query string |
| `datasource` | object | required | Which datasource to query |
| `regex` | string | `""` | Filter query results with regex capture groups |
| `sort` | integer | `0` | Sort order: 0=none, 1=asc numeric, 2=desc numeric, 3=asc alpha, 4=desc alpha, 5=asc natural, 6=desc natural |
| `multi` | boolean | `false` | Allow multiple selections |
| `includeAll` | boolean | `false` | Add "All" option (requires `multi: true`) |
| `allValue` | string | `""` | What `$__all` expands to in queries |
| `current` | object | required | Currently selected value(s) |
| `hide` | integer | `0` | Visibility: 0=visible, 1=label only, 2=hidden |

**Prometheus query functions for query variables:**

```promql
# List all values of a label
label_values(metric_name, label_name)

# List values matching a filter
label_values({job="api"}, instance)

# List metric names matching a regex
metrics(.*_total)

# Query result (returns a single-column table)
query_result(up{job="api"})

# Label_values with regex filtering on results
label_values(http_requests_total{code=~"5.."}, method)
```

**Regex extraction from query results:**

When `regex` is set, Grafana applies it to each result row and uses capture groups to produce the option value. The first capture group becomes the value; if there are two groups, group 1 is value and group 2 is display text.

```json
{
  "name": "pod",
  "type": "query",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "query": "kube_pod_info{namespace=\"$namespace\"}",
  "regex": "/pod=\"([^\"]+)\"/",
  "sort": 3
}
```

### 2. Custom Variable (`type: "custom"`)

Static list of options defined at authoring time.

```json
{
  "name": "env",
  "type": "custom",
  "label": "Environment",
  "query": "prod,staging,dev",
  "current": { "selected": true, "text": "prod", "value": "prod" },
  "options": [
    { "selected": true, "text": "Production", "value": "prod" },
    { "selected": false, "text": "Staging", "value": "staging" },
    { "selected": false, "text": "Development", "value": "dev" }
  ],
  "hide": 0
}
```

The `query` field serves as the source text. When it contains commas, Grafana splits on comma to create options. Each option gets:
- `text`: display label shown in the dropdown
- `value`: actual value used in `${var}` substitution

If no `options` array is provided, Grafana auto-generates one from the `query` string where both text and value are identical to the split segments. Provide explicit `options` when display labels differ from substitution values.

Use custom variables when:
- The set of values is small (under ~20 items)
- Values are stable and change rarely
- Display labels should differ from query values
- No datasource dependency is desired

### 3. Textbox Variable (`type: "textbox"`)

Free-text input field for arbitrary user-entered values.

```json
{
  "name": "search",
  "type": "textbox",
  "label": "Search",
  "query": "",
  "current": { "selected": false, "text": "", "value": "" },
  "hide": 0,
  "default": ""
}
```

Security consideration: textbox values are interpolated directly into queries. Always use parameterized patterns where the datasource supports them, or apply regex escaping. For PromQL, wrap textbox values appropriately:

```promql
# Safer: use regex match with escaped input
{job=~".*${search}.*"}

# Dangerous: direct interpolation can break query syntax
${search}
```

### 4. Constant Variable (`type: "constant"`)

Hidden constant injected into all queries without appearing in the dashboard UI.

```json
{
  "name": "cluster_id",
  "type": "constant",
  "label": "",
  "query": "us-east-1-prod",
  "current": { "selected": false, "text": "us-east-1-prod", "value": "us-east-1-prod" },
  "hide": 2,
  "options": [ { "selected": true, "text": "", "value": "us-east-1-prod" } ]
}
```

Key behavior:
- `hide: 2` means fully hidden -- not visible anywhere in the dashboard UI
- The value is always active and substituted wherever `${cluster_id}` appears
- Cannot be changed by operators through the dashboard interface
- Useful for environment tags, cluster identifiers, team labels, deployment markers

Common pattern -- use constants alongside query variables to scope dashboards:

```json
{
  "templating": {
    "list": [
      { "name": "team", "type": "constant", "query": "platform", "hide": 2 },
      { "name": "service", "type": "query", "datasource": { "type": "prometheus", "uid": "prometheus" }, "query": "label_values({team=\"$team\"}, service)" }
    ]
  }
}
```

### 5. Datasource Variable (`type: "datasource"`)

Runtime datasource selector that lets operators switch between datasources.

```json
{
  "name": "ds_prometheus",
  "type": "datasource",
  "label": "Prometheus Source",
  "query": "prometheus",
  "current": { "selected": true, "text": "Prometheus", "value": "prometheus" },
  "regex": "",
  "hide": 0,
  "isMulti": false,
  "pluginVersion": ""
}
```

**Query filter types:**

| Query Value | Matches |
| --- | --- |
| `"prometheus"` | All Prometheus datasources |
| `"loki"` | All Loki datasources |
| `"grafana"` | Built-in Grafana datasource |
| `"mysql"` | All MySQL datasources |
| `""` (empty) | All datasources |
| `"grafana-cloud-watch"` | Specific plugin by ID |

**Using datasource variables in panels:**

```json
{
  "datasource": {
    "type": "prometheus",
    "uid": "${ds_prometheus}"
  }
}
```

The `type` must still match the expected plugin type. The `uid` field receives the variable substitution. This allows operators to switch between Prometheus instances without editing panel JSON.

### 6. Interval Variable (`type: "interval"`)

Predefined time interval choices for controlling query granularity.

```json
{
  "name": "resolution",
  "type": "interval",
  "label": "Resolution",
  "query": "10s,30s,1m,5m,15m,30m,1h",
  "auto": true,
  "auto_min": "10s",
  "auto_count": 30,
  "current": { "selected": true, "text": "Auto", "value": "$__auto_interval" },
  "hide": 0,
  "options": [
    { "selected": false, "text": "Auto", "value": "$__auto_interval" },
    { "selected": true, "text": "30 seconds", "value": "30s" },
    { "selected": false, "text": "1 minute", "value": "1m" },
    { "selected": false, "text": "5 minutes", "value": "5m" }
  ]
}
```

**Auto-calculation logic:**
When `auto` is `true` and the operator selects "Auto", Grafana computes:

```
interval = (time_range_width) / auto_count
```

For example, with `time_range` of 1 hour and `auto_count` of 30, the auto interval is 120 seconds (rounded to the nearest supported step). The computed value replaces `$__interval` and `$__interval_ms` in all queries.

**Usage in PromQL:**

```promql
# Use $__interval for automatic rate/scrape alignment
rate(http_requests_total[$__interval])

# Use the named interval variable for manual override
rate(http_requests_total[${resolution}])
```

### 7. Ad Hoc Filters Variable (`type: "adhoc"`)

Dynamic tag-key/tag-value filter builder. Creates a UI element where operators add/remove filter pairs.

```json
{
  "name": "adhoc",
  "type": "adhoc",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "hide": 0
}
```

How ad hoc filters work:
1. The UI shows an "Add filter" button next to other variables
2. Operator selects a label key from the available set (queried from the datasource)
3. Operator chooses an operator (=, !=, =~, !~)
4. Operator enters a value
5. Grafana appends the resulting matcher to **every** PromQL query in the dashboard

Example: if the operator adds `job = api` and `environment != test`, every query gains `{job="api", environment!="test"}` as additional label matchers.

No `$variable` syntax is needed in queries -- filters apply automatically through the datasource query system.

### 8. Switch Variable (`type: "switch"`)

Boolean-like toggle between two states.

```json
{
  "name": "verbose",
  "type": "switch",
  "label": "Verbose Mode",
  "query": "false,true",
  "current": { "selected": true, "text": "Off", "value": "false" },
  "options": [
    { "selected": true, "text": "Off", "value": "false" },
    { "selected": false, "text": "On", "value": "true" }
  ],
  "hide": 0
}
```

Common use cases:
- Toggle between summary and detailed views
- Enable/disable debug information
- Switch between production and staging context
- Control whether hidden/debug panels appear

## Global Variables

Grafana provides these built-in variables without any declaration in `templating.list`. They are always available in every dashboard.

| Variable | Type | Expands To | Example |
| --- | --- | --- | --- |
| `$__dashboard.uid` | string | Current dashboard UID | `"api-overview"` |
| `$__from` | number | Dashboard start time (epoch ms) | `1713556800000` |
| `$__to` | number | Dashboard end time (epoch ms) | `1713558600000` |
| `$__interval` | string | Auto-calculated scrape interval | `"5m"` |
| `$__interval_ms` | number | Interval in milliseconds | `300000` |
| `$__name` | string | Dashboard title | `"API Overview"` |
| `$__org.id` | number | Organization ID | `1` |
| `$__org.name` | string | Organization name | `"Main Org."` |
| `$__user.login` | string | Logged-in user login name | `"admin"` |
| `$__user.email` | string | User email address | `"admin@example.com"` |
| `$__range` | number | Time range width in ms | `1800000` |
| `$__range_ms` | number | Alias for `$__range` | `1800000` |
| `$__range_s` | number | Time range in seconds | `1800` |
| `$__rate_interval` | string | Safe rate interval (4x interval) | `"20m"` |
| `$__timezone` | string | Dashboard timezone setting | `"browser"` or `"UTC"` |

**Practical usage examples:**

```promql
# Safe rate calculation using $__rate_interval
# This accounts for potential scrapes missed within the range
rate(http_requests_total[$__rate_interval])

# Use $__from/$__to for external API calls in data links
https://external-api.example.com/metrics?start=${__from}&end=${__to}

# Use $__user.login for per-user scoping
{owner="${__user.login}"}

# Use $__range_s for adaptive window sizing
histogram_quantile(0.99, sum(rate(request_duration_seconds_bucket[$__range_s])) by (le))
```

## Variable Syntax Formats

Every format modifier changes how multi-value variables expand into query strings.

### Basic Syntax

| Syntax | Behavior | Example Input | Example Output |
| --- | --- | --- | --- |
| `$var` | Simple substitution | var=`["a","b"]` | `(a\|b)` in PromQL |
| `${var}` | Explicit delimiters | same | same |

### Format Modifiers

| Modifier | Output Format | Example with `[a, b, c]` |
| --- | --- | --- |
| *(none)* | Context-dependent (PromQL: pipe-separated) | `a\|b\|c` |
| `:raw` | No quoting or wrapping | `a,b,c` |
| `:csv` | Comma-separated, quoted | `"a","b","c"` |
| `:doublequote` | Double-quoted, comma-separated | `"a","b","c"` |
| `:singlequote` | Single-quoted, comma-separated | `'a','b','c'` |
| `:json` | JSON array | `["a","b","c"]` |
| `:lucene` | Lucene OR syntax | `("a" OR "b" OR "c")` |
| `:pipe` | Pipe-separated | `a\|b\|c` |
| `:percentencode` | URL-encoded | `a%2Cb%2Cc` |
| `:regex` | Regex alternation | `(a\|b\|c)` |
| `:glob` | Glob pattern | `{a,b,c}` |
| `:sqlstring` | SQL quoted, comma-separated | `'a','b','c'` |
| `:filepath` | File path safe | `a,b,c` |

### Context-Specific Default Behavior

When no format modifier is specified, Grafana picks a default based on where the variable is used:

| Context | Default Format | Reason |
| --- | --- | --- |
| PromQL expression | `pipe` | PromQL uses `\|` for alternation |
| InfluxQL | `regex` | InfluxQL uses regex for tag matching |
| URL parameter | `percentencode` | URLs need encoding |
| Panel title | `glob` | Human-readable |
| Data link URL | `percentencode` | URL safety |
| Text content | `raw` | Plain text |

### Correct Usage Patterns by Context

**PromQL label matcher (multi-value):**

```promql
# CORRECT: csv format wraps each value in quotes
http_requests_total{instance~"${instances:csv}"}

# WRONG: raw format breaks on special characters
http_requests_total{instance=~$instances}

# CORRECT: pipe format works for simple alphanumeric values
http_requests_total{instance=~${instances:pipe}}
```

**InfluxQL tag filter:**

```influxql
# CORRECT: regex format matches InfluxQL expectations
SELECT * FROM metrics WHERE tag =~ /${tags:regex}/
```

**Data link URL:**

```json
{
  "url": "https://example.com/search?q=${search:percentencode}"
}
```

**Panel title with conditional text:**

```json
{
  "title": "Metrics ${if verbose then '(Detailed)' else ''}"
}
```

## Advanced Variable Patterns

### Cascading Variables

Chain variables so that selecting one filters the options of another. Each downstream variable references upstream variables in its query.

```json
{
  "templating": {
    "list": [
      {
        "name": "cluster",
        "type": "custom",
        "query": "us-east,us-west,eu-central",
        "current": { "text": "us-east", "value": "us-east" }
      },
      {
        "name": "namespace",
        "type": "query",
        "datasource": { "type": "prometheus", "uid": "prometheus" },
        "query": "label_values(kube_pod_info{cluster=\"$cluster\"}, namespace)",
        "refresh": 1
      },
      {
        "name": "service",
        "type": "query",
        "datasource": { "type": "prometheus", "uid": "prometheus" },
        "query": "label_values(http_requests_total{namespace=\"$namespace\", cluster=\"$cluster\"}, service)",
        "refresh": 1
      }
    ]
  }
}
```

Set `refresh` to `1` on dependent variables so they re-query when upstream variables change. Refresh modes: `0`=on dashboard load, `1`=on time range change, `2`=never (manual refresh only).

### Interpolation in Panel Titles

Use variables directly in panel titles for dynamic labeling:

```json
{
  "title": "${service} - Request Rate (${env})"
}
```

Available template functions in titles:

| Function | Example | Result |
| --- | --- | --- |
| `${var}` | `${service}` | Variable value |
| `${var:upper}` | `${service:upper}` | Uppercase |
| `${var:lower}` | `${service:lower}` | Lowercase |
| `${if cond then x else y}` | `${if env='prod' then 'PROD' else 'TEST'}` | Conditional |
| `${__cell_N}` | `${__cell_0}` | Table cell value (row-repeated panels) |
| `${__data.fields.F}` | `${__data.fields.method}` | Field value from data frame |

### Repeating Across Multiple Variables

Panels can repeat over multiple variables simultaneously using nested repeat containers or comma-separated variable names:

```json
{
  "repeat": "instance, job",
  "repeatDirection": "h",
  "maxPerRow": 4,
  "title": "CPU - ${instance} (${job})"
}
```

This creates a cartesian product: every combination of `instance` and `job` values generates a repeated panel. Use sparingly -- the panel count grows multiplicatively.

### Variable Hiding Levels

| Hide Value | Effect |
| --- | --- |
| `0` | Fully visible (default) |
| `1` | Label visible but dropdown hidden (info-only) |
| `2` | Completely hidden from UI (constant/injected values) |

Pattern: hide intermediate cascade variables that operators should not change independently:

```json
[
  { "name": "region", "type": "custom", "query": "us-eu,apac", "hide": 0 },
  { "name": "region_filter", "type": "constant", "query": "us-eu", "hide": 2 },
  { "name": "cluster", "type": "query", "query": "...", "hide": 0 }
]
```

## Complete Transformation Catalog

Transformations reshape query results before rendering. They execute in array order; each transformation receives the output of the previous one. The common transformations are covered in [`../SKILL.md`](../SKILL.md). This section covers the full catalog.

### Transformation Index

| ID | Name | Purpose |
| --- | --- | --- |
| `organize` | Organize fields | Rename, hide, reorder columns |
| `merge` | Merge | Combine multiple query results into one table |
| `pivot` | Pivot | Convert wide time series to pivoted table |
| `sortBy` | Sort by | Sort rows by field values |
| `filterDataByValues` | Filter data by values | Include/exclude rows by condition |
| `filterDataByQueryRef` | Filter data by query ref | Keep rows from specific query |
| `filterFieldsByName` | Filter fields | Show/hide columns by name |
| `groupBy` | Group by | Aggregate rows by grouping fields |
| `reduce` | Reduce | Collapse all rows into one aggregated row |
| `partitionByValues` | Partition by values | Split into frames per unique value |
| `joinByField` | Join by field | Left join two result sets on a shared field |
| `outerJoin` | Outer join | Full outer join two result sets |
| `labelsToFields` | Labels to fields | Convert Prometheus labels to columns |
| `addFieldFromCalculation` | Add calculated field | Compute new column from existing ones |
| `convertFieldType` | Convert field type | Change semantic type of a field |
| `renameByRegex` | Rename by regex | Apply regex rename to field names |
| `extractFields` | Extract fields | Parse structured fields (JSON, logfmt) |
| `concatenateFields` | Concatenate fields | Join multiple fields into one |
| `prepareTimeSeries` | Prepare time series | Align and fill time series gaps |
| `formatString` | Format string | Apply printf-style formatting |
| `configFromQueryResults` | Config from query results | Use query results to drive field config |
| `groupingToMatrix` | Grouping to matrix | Convert grouped data to matrix form |

### Detailed Schemas for Less Common Transformations

#### Join by Field

Left-join two data frames on a shared field value.

```json
{
  "id": "joinByField",
  "options": {
    "field": "instance"
  }
}
```

All fields from both frames are included in the output. Rows from the left frame without a matching right-frame row keep null values for right-side fields.

#### Outer Join

Full outer join -- keeps all rows from both frames regardless of match.

```json
{
  "id": "outerJoin",
  "options": {
    "byField": "timestamp"
  }
}
```

#### Labels to Fields

Convert Prometheus label pairs into separate columns. Essential when working with instant queries that return label-based dimensions.

```json
{
  "id": "labelsToFields",
  "options": {}
}
```

Input: one row with labels `{method: "GET", status: "200"}`, value: `42`.
Output: columns `method`, `status`, `Value #A` with values `"GET"`, `"200"`, `42`.

#### Extract Fields

Parse structured string fields into sub-fields. Supports JSON and logfmt formats.

```json
{
  "id": "extractFields",
  "options": {
    "source": "message",
    "format": "json"
  }
}
```

Supported formats: `"json"`, `"logfmt"`. When format is `"json"`, nested objects create dot-delimited field names (e.g., `request.headers.user-agent`).

#### Concatenate Fields

Join multiple field values into a single string field.

```json
{
  "id": "concatenateFields",
  "options": {
    "fields": ["first_name", "last_name"],
    "separator": " ",
    "targetField": "full_name"
  }
}
```

#### Prepare Time Series

Align time series to regular intervals and fill gaps.

```json
{
  "id": "prepareTimeSeries",
  "options": {
    "fillMode": "previous",
    "stepCalculation": "auto"
  }
}
```

Fill modes: `"null"`, `"previous"`, `"value"` (with `fillValue`), `"delta"`, `"difference"`.
Step calculation: `"auto"`, `"max"`, `"min"`, `"count"`, `"sum"`, `"mean"`, `"first"`, `"last"`.

#### Format String

Apply printf-style formatting to a field's values.

```json
{
  "id": "formatString",
  "options": {
    "fields": {
      "duration_ms": {
        "format": "%.2fms"
      },
      "status_code": {
        "format": "HTTP %d"
      }
    }
  }
}
```

#### Config From Query Results

Use one query's results to dynamically configure field properties (thresholds, units, etc.) for another query's output. Advanced pattern for self-configuring dashboards.

```json
{
  "id": "configFromQueryResults",
  "options": {
    "queries": [
      {
        "refId": "B",
        "fields": {
          "threshold_level": { "alias": "thresholds" },
          "unit_name": { "alias": "unit" }
        }
      }
    ]
  }
}
```

#### Filter by Query Ref

Keep only rows produced by a specific query (identified by refId).

```json
{
  "id": "filterDataByQueryRef",
  "options": {
    "refId": "A"
  }
}
```

#### Filter Fields by Name

Show or hide entire columns by name pattern.

```json
{
  "id": "filterFieldsByName",
  "options": {
    "include": { "pattern": "/^(Time|Value)/" },
    "exclude": {}
  }
}
```

#### Grouping to Matrix

Convert grouped/bucketed data into a matrix layout suitable for heatmap rendering.

```json
{
  "id": "groupingToMatrix",
  "options": {
    "bucketField": "le",
    "valueField": "Value #A",
    "labelField": "method"
  }
}
```
