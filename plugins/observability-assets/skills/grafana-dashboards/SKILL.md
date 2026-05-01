---
name: grafana-dashboards
description: >-
  Author and review Grafana dashboards as version-controlled JSON assets with stable uid, deliberate datasource handling, and operator-centric panel layout. Use this skill when creating or reviewing Grafana dashboards, editing classic dashboard JSON, authoring panel queries and visualization config, using Grafana variables (7 standard classic templating types plus global variables), configuring transformations, field config, thresholds, overrides, and value mappings, stabilizing dashboard uid, applying USE/RED/Golden Signals frameworks, setting up repeat behavior and links, using a Grafana mixin or Jsonnet generation workflow, or needing guidance on Grafana dashboard asset structure and panel JSON schema.
---

# Grafana Dashboards

Author and review Grafana dashboards as version-controlled assets while keeping dashboard identity stable across environments.

The common case: one dashboard with a stable `uid`, a deliberate title, explicit datasource handling, a default time range no broader than the last 30 minutes, and a panel layout that answers a real operator question instead of becoming a generic metric scrapbook.

This skill owns the ordinary path for dashboard structure, queries, variables, transformations, field configuration, thresholds, legends, units, layout, repeat behavior, links, annotations, panel types, overrides, value mappings, data links, query options, and the dashboard JSON model boundary. For complete panel-type JSON schemas, see [`./references/panel-types.md`](./references/panel-types.md). For variable types, syntax, and global variables, see [`./references/variables.md`](./references/variables.md). For field config, overrides, value mappings, and data links, see [`./references/field-config.md`](./references/field-config.md). For Grafana mixin or Jsonnet-oriented generation workflows, see [`./references/grafana-mixin.md`](./references/grafana-mixin.md). For export cleanup decisions, normalization targets, and ownership boundaries after UI edits or rendering, see [`./references/dashboard-structure.md`](./references/dashboard-structure.md).

## Common-Case Workflow

1. Start from the operator question the dashboard must answer.
2. Keep one stable dashboard identity with a deliberate `uid` and title.
3. Choose the datasource and queries deliberately, then shape the dashboard around the returned data rather than copying an arbitrary UI export.
4. Pick the right panel type for each question (see Panel Type Decision Guide below).
5. Add variables, repeated panels, transformations, field config, units, thresholds, and legends only when they improve operator readability for the main question.
6. Keep links, annotations, and layout aligned to one narrative flow such as saturation, errors, and latency.
7. Default the dashboard time range to the last 30 minutes or less, and widen it only when the operator question needs more history.

Default to classic dashboard JSON (`schemaVersion` 39) for all examples and templates. When working with resource-style schemas or v2 layout models, document the version boundary explicitly in the dashboard asset or review notes.

## Minimal Setup

Minimal dashboard JSON -- smallest valid shape for syntax testing:

```json
{
  "uid": "api-overview",
  "title": "API Overview",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "30s",
  "panels": []
}
```

Use when: you need a minimal valid dashboard shell to validate JSON syntax before adding content.

## First Runnable Commands or Code Shape

Start by validating the JSON structure before the file is treated as a Git-owned dashboard asset:

```bash
python3 -m json.tool grafana/dashboards/api-overview.json
```

Use when: the dashboard JSON was edited manually and you need the first fast syntax check.
Run this from the repository root shown in the path examples, or replace the path with the dashboard file location in your tree.

## Ready-to-Adapt Templates

### Full Dashboard Shell

Stable `uid`, clear title, one time-picker baseline, and one working panel:

```json
{
  "uid": "api-overview",
  "title": "API Overview",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "30s",
  "time": { "from": "now-30m", "to": "now" },
  "timezone": "browser",
  "timepicker": {
    "refresh_intervals": ["5s", "10s", "30s", "1m", "5m", "15m", "30m", "1h"],
    "time_options": ["5m", "15m", "1h", "6h", "12h", "24h", "2d", "7d", "30d"]
  },
  "templating": { "list": [] },
  "annotations": { "list": [] },
  "panels": [
    {
      "id": 1,
      "title": "Request Rate",
      "type": "timeseries",
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "targets": [
        { "expr": "round(sum(rate(http_requests_total{job=\"api\"}[5m])), 0.001)", "legendFormat": "req/s", "refId": "A" }
      ],
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 }
    }
  ]
}
```

Use when: you need a small but production-shaped dashboard asset to expand from Git.

### Variable and Repeat Pattern

Merge this fragment into the full dashboard shell to drive repeated panels from one bounded query variable:

```json
{
  "templating": {
    "list": [
      {
        "name": "instance",
        "type": "query",
        "datasource": {
          "type": "prometheus",
          "uid": "prometheus"
        },
        "query": "label_values(up{job=\"api\"}, instance)",
        "current": {
          "selected": false,
          "text": "All",
          "value": "$__all"
        },
        "hide": 0
      }
    ]
  },
  "panels": [
    {
      "title": "Request Rate - ${instance}",
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "repeat": "instance",
      "repeatDirection": "h",
      "maxPerRow": 3
    }
  ]
}
```

Use when: one panel shape should repeat across a bounded variable set without copying panel JSON by hand. This is a fragment to merge into the full dashboard shell, not a standalone importable dashboard.

For the full variable type reference (7 standard classic variable types, plus global variables and a boolean-toggle convention), see [`./references/variables.md`](./references/variables.md).

### Transformations and Field Config

Merge this fragment into the target panel when the query already returns the right signal:

```json
{
  "fieldConfig": {
    "defaults": {
      "unit": "reqps",
      "min": 0,
      "decimals": 2,
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "color": "green", "value": null },
          { "color": "yellow", "value": 300 },
          { "color": "red", "value": 500 }
        ]
      }
    }
  },
  "options": {
    "legend": {
      "displayMode": "list"
    }
  },
  "transformations": [
    { "id": "organize", "options": {} }
  ]
}
```

Use when: the query already returns the right signal and the panel only needs clearer units, thresholds, legend behavior, or column organization. This is a fragment to merge into an existing panel object, not a standalone dashboard asset or dashboard-root object.

### Links and Annotations

Merge this fragment into the dashboard shell to attach one drilldown path and one event context source directly to the dashboard:

```json
{
  "annotations": {
    "list": [
      {
        "name": "Deployments",
        "datasource": {
          "type": "grafana",
          "uid": "-- Grafana --"
        }
      }
    ]
  },
  "links": [
    {
      "title": "API Runbook",
      "url": "https://runbooks.example.com/api"
    }
  ]
}
```

Use when: operators need one direct drilldown and one timeline context source while reading the dashboard. This is a fragment to merge into the full dashboard shell, not a complete dashboard by itself.

### Query Options

Query options control how much data is fetched and how queries are evaluated:

```json
{
  "targets": [
    {
      "expr": "rate(http_requests_total[5m])",
      "refId": "A",
      "maxDataPoints": 500,
      "interval": "",
      "intervalMs": 15000,
      "queryType": "range",
      "datasource": { "type": "prometheus", "uid": "prometheus" }
    }
  ]
}
```

Key fields:

| Field | Type | Purpose |
| --- | --- | --- |
| `maxDataPoints` | integer | Maximum number of data points returned (default varies by datasource) |
| `interval` / `intervalMs` | string / integer | Override `$__interval` (e.g., `15s`, `1m`) |
| `queryType` | string | `"range"` (time series) or `"instant"` (single value at `to` time) |
| `timeFrom` | string | Relative offset from dashboard start (e.g., `"now-6h"`) |
| `timeShift` | string | Shift query time window (e.g., `"1d"` for day-over-day comparison) |
| `cacheTimeout` | string | Cache duration override (e.g., `"5m"`) |

Time shift example -- compare today vs yesterday: add `"timeShift": "1d"` to a second target with the same expression and a different `legendFormat`.

## Panel Type Decision Guide

Choose the panel type based on what the operator needs to see. The most common types are listed first.

### Quick Decision Table

| Operator Question | Panel Type | Key Distinguishing Feature |
| --- | --- | --- |
| How does this metric change over time? | `timeseries` | Line/area chart with X = time axis |
| What is the single current value? | `stat` | Large number with optional sparkline |
| What is the value relative to min/max? | `gauge` | Arc gauge with color zones |
| How do categories compare? | `barchart` | Vertical/horizontal bars, grouped/stacked |
| What is the distribution of values? | `histogram` | Bucketed frequency distribution |
| Where is density in two dimensions? | `heatmap` | Color-coded grid (X and Y axes) |
| How is a whole divided? | `piechart` | Proportional slices |
| What are the raw rows? | `table` | Tabular data with sorting/filtering |
| What state was active when? | `statetimeline` | Colored horizontal bands per state |
| How often did states change? | `statushistory` | Timeline of discrete status events |
| What log lines match? | `logs` | Log viewer with highlighting |
| What is the trace detail? | `traces` | Trace waterfall/duration view |
| What is the flame graph? | `flamegraph` | Hierarchical call-stack profiling |

### Timeseries Panel (Most Common)

The default panel for any time-varying metric. Supports multiple series, legends, annotations, and threshold lines.

```json
{
  "id": 1,
  "title": "Request Rate",
  "type": "timeseries",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "targets": [
    { "expr": "sum(rate(http_requests_total[5m])) by (method)", "refId": "A" }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "reqps",
      "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] }
    }
  },
  "options": {
    "legend": { "displayMode": "table", "placement": "right", "calcs": ["mean", "max"] },
    "drawStyle": "line",
    "lineWidth": 1,
    "fillOpacity": 10,
    "stacking": { "mode": "none", "group": "A" }
  },
  "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 }
}
```

Key `options.drawStyle`: `"line"`, `"bars"`, `"points"`. Key `options.stacking.mode`: `"none"`, `"normal"`, `"percent"`.

### Stat Panel

Single large number with optional sparkline, progress bar, and text mode.

```json
{
  "id": 2,
  "title": "Error Rate",
  "type": "stat",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "targets": [
    { "expr": "sum(rate(http_requests_total{status=~\"5..\"}[5m])) / sum(rate(http_requests_total[5m])) * 100", "refId": "A" }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "decimals": 2,
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "color": "green", "value": null },
          { "color": "yellow", "value": 1 },
          { "color": "red", "value": 5 }
        ]
      },
      "color": { "mode": "thresholds" }
    }
  },
  "options": {
    "textMode": "auto",
    "colorMode": "value",
    "graphMode": "area",
    "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false }
  },
  "gridPos": { "h": 4, "w": 6, "x": 0, "y": 8 }
}
```

Key `options.textMode`: `"auto"`, `"name"`, `"none"`, `"value"`. Key `options.graphMode`: `"none"`, `"area"`, `"linear"`. Key `options.colorMode`: `"none"`, `"value"`, `"background"`.

### Table Panel

Display raw tabular data with column customization, sorting, and cell coloring.

```json
{
  "id": 5,
  "title": "Top Endpoints",
  "type": "table",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "targets": [
    { "expr": "topk(10, sum by (path) (increase(http_requests_total[1h])))", "refId": "A", "format": "table", "instant": true }
  ],
  "fieldConfig": {
    "defaults": { "unit": "short", "custom": { "align": "left", "filterable": true } }
  },
  "options": { "showHeader": true },
  "gridPos": { "h": 8, "w": 12, "x": 0, "y": 24 }
}
```

For complete JSON schemas covering all remaining panel types (gauge, barchart, heatmap, statetimeline, logs, piechart, histogram, bar gauge, candlestick, trend, XY chart, node graph, traces, flame graph, canvas, geomap, dashboard list, alert list, annotations list, and text/news panels), see [`./references/panel-types.md`](./references/panel-types.md).

## Variable Types Reference (Common Path)

The three most common variable types. For all 7 standard classic variable types with complete JSON schemas, global variables, format modifier catalog, cascading patterns, and a boolean-toggle convention, see [`./references/variables.md`](./references/variables.md).

### Query Variable (Most Common)

Queries a datasource to populate options dynamically.

```json
{
  "name": "namespace",
  "type": "query",
  "label": "Namespace",
  "datasource": { "type": "prometheus", "uid": "prometheus" },
  "query": "label_values(kube_pod_info, namespace)",
  "sort": 1,
  "multi": true,
  "includeAll": true,
  "allValue": ".+",
  "current": { "selected": true, "text": ["All"], "value": ["$__all"] },
  "hide": 0
}
```

Key fields: `query` (datasource-specific), `regex` (filter results), `sort` (0-6), `multi`, `includeAll`, `allValue`.

### Custom Variable

Hardcoded list of options defined inline. Use when the set of values is small, stable, and known at authoring time.

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

### Textbox Variable

Free-text input for ad-hoc values. Always sanitize textbox values in queries to prevent injection.

```json
{
  "name": "search",
  "type": "textbox",
  "label": "Search",
  "query": "",
  "current": { "selected": false, "text": "", "value": "" },
  "hide": 0
}
```

## Transformation Types (Common Path)

Transformations reshape query results before rendering. Apply them in order; each transformation receives the output of the previous one. For the full catalog of 20+ transformation types, see [`./references/variables.md`](./references/variables.md).

### Organize Fields (Default)

Rename, hide, and reorder columns returned by queries.

```json
{
  "transformations": [
    {
      "id": "organize",
      "options": {
        "excludeByName": { "__name__": true, "job": true },
        "renameByName": { "Value #A": "requests_per_sec", "Time": "timestamp" },
        "indexByName": {}
      }
    }
  ]
}
```

### Merge

Combine results from multiple queries into one table by joining on shared fields.

```json
{
  "transformations": [
    { "id": "merge", "options": {} }
  ]
}
```

### Filter Data by Values

Remove rows that do not match a condition.

```json
{
  "transformations": [
    {
      "id": "filterDataByValues",
      "options": {
        "filters": [
          { "fieldName": "status", "type": "include", "match": { "value": "200" } }
        ],
        "match": "any"
      }
    }
  ]
}
```

Filter types: `"include"`, `"exclude"`. Match modes: `"value"`, `"regex"`, `"is"`, `"isNot"`.

## Field Config, Thresholds, Overrides, Value Mappings, and Data Links

These systems control how data appears visually after queries return results. For the complete unit catalog (~60 specifiers), all 5 matcher types, all override property IDs, all 4 mapping types (range, regex, special), and full data link variable catalog, see [`./references/field-config.md`](./references/field-config.md).

### Standard Field Config Options

Applied via `fieldConfig.defaults` at the panel level.

```json
{
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "decimals": 2,
      "min": 0,
      "max": null,
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "color": "green", "value": null },
          { "color": "yellow", "value": 50 },
          { "color": "red", "value": 100 }
        ]
      },
      "noValue": "--",
      "displayName": "",
      "color": { "mode": "palette-classic" },
      "mappings": []
    }
  }
}
```

Common unit specifiers: `"short"`, `"percent"`, `"percentunit"`, `"bytes"`, `"bps"`, `"s"`, `"ms"`, `"reqps"`, `"ops"`. Custom unit prefix/suffix: `"prefix:suffix"` (e.g., `"$:USD"` displays as `$123 USD`). For the complete unit catalog, see [`./references/field-config.md`](./references/field-config.md).

### Thresholds -- Absolute Mode (Most Common)

Steps trigger at exact numeric values. The first step always has `value: null` (base color).

```json
{
  "mode": "absolute",
  "steps": [
    { "color": "green", "value": null },
    { "color": "yellow", "value": 50 },
    { "color": "red", "value": 100 }
  ]
}
```

For percentage mode (requires explicit `min`/`max` on the field), see [`./references/field-config.md`](./references/field-config.md).

### Overrides System

Overrides let you apply different visual settings to specific fields or series within the same panel. An override consists of **matchers** (which fields to target) and **properties** (what to change).

```json
{
  "fieldConfig": {
    "overrides": [
      {
        "matcher": { "id": "byName", "options": "errors" },
        "properties": [
          { "id": "color", "value": { "mode": "fixed", "fixedColor": "red" } },
          { "id": "custom.lineWidth", "value": 3 }
        ]
      }
    ]
  }
}
```

For all 5 matcher types (`byName`, `byRegexp`, `byType`, `byFrameRefID`, `byValue`) and common override property IDs, see [`./references/field-config.md`](./references/field-config.md).

### Value Mappings

Map raw values to displayed text/colors without changing the underlying data.

**Value mapping** (most common): exact match on specific values.

```json
{
  "mappings": [
    {
      "type": "value",
      "options": {
        "0": { "text": "OK", "color": "green" },
        "1": { "text": "WARNING", "color": "yellow" },
        "2": { "text": "CRITICAL", "color": "red" }
      }
    }
  ]
}
```

For range, regex, and special mapping types (null/NaN/boolean handling), see [`./references/field-config.md`](./references/field-config.md). Multiple mapping types can coexist in the same `mappings` array; they are evaluated in order and the first match wins.

### Data Links

Attach clickable URLs to data points that open external resources with interpolated context.

```json
{
  "fieldConfig": {
    "defaults": {
      "links": [
        {
          "title": "View Traces",
          "url": "https://jaeger.example.com/search${__url_time_range}&service=${__data.fields.service}",
          "targetBlank": true,
          "tooltip": "Open in Jaeger"
        }
      ]
    }
  }
}
```

For the full data link variable catalog (`${__series.name}`, `${__field.labels.*}`, `${__value.*}`, etc.), see [`./references/field-config.md`](./references/field-config.md).

## Best Practice Frameworks

Structure dashboards around proven observability frameworks so every panel answers a meaningful operational question.

### USE Method

Focus on Utilization, Saturation, and Errors for resource-centric views.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Utilization | How busy is the resource? | `node_cpu_seconds_total` (non-idle %) |
| Saturation | How much demand is queued? | `node_load_avg`, queue depth, conn count |
| Errors | How many operations failed? | Error rate, 5xx count, error ratio |

Dashboard layout following USE:

```json
{
  "title": "Node Resource Health - ${instance}",
  "panels": [
    { "title": "CPU Utilization", "type": "timeseries" },
    { "title": "CPU Saturation (Load)", "type": "timeseries" },
    { "title": "Memory Utilization", "type": "timeseries" },
    { "title": "Memory Saturation (OOM Kills)", "type": "timeseries" },
    { "title": "Disk I/O Utilization", "type": "timeseries" },
    { "title": "Disk Saturation (I/O Wait)", "type": "timeseries" },
    { "title": "Network Errors", "type": "timeseries" }
  ]
}
```

### RED Method

Focus on Rate, Errors, and Duration for request-driven services.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Rate | How many requests per second? | `rate(http_requests_total[5m])` |
| Errors | How many are failing? | `rate(http_requests_total{status=~"5.."}[5m])` |
| Duration | How long do requests take? | `histogram_quantile(0.99, ...)` |

Dashboard layout following RED:

```json
{
  "title": "Service RED Metrics - ${service}",
  "panels": [
    { "title": "Request Rate", "type": "timeseries" },
    { "title": "Error Rate (%)", "type": "stat" },
    { "title": "Error Count", "type": "timeseries" },
    { "title": "Latency p50", "type": "timeseries" },
    { "title": "Latency p95", "type": "timeseries" },
    { "title": "Latency p99", "type": "timeseries" }
  ]
}
```

### Four Golden Signals

Google's SRE framework: Latency, Traffic, Errors, and Saturation.

| Signal | Question | Example Metric |
| --- | --- | --- |
| Latency | How long do requests take? | Service response time percentiles |
| Traffic | How much demand is there? | Requests/sec, connections/sec |
| Errors | How many are failing? | Error rate, failure percentage |
| Saturation | How close to capacity? | CPU, memory, disk, connection pool usage |

Dashboard layout following Four Golden Signals:

```json
{
  "title": "SRE Golden Signals - ${service}",
  "panels": [
    { "title": "Request Latency (p50/p95/p99)", "type": "timeseries" },
    { "title": "Traffic (RPS)", "type": "stat" },
    { "title": "Error Rate", "type": "stat" },
    { "title": "Errors Over Time", "type": "timeseries" },
    { "title": "CPU Saturation", "type": "gauge" },
    { "title": "Memory Saturation", "type": "gauge" },
    { "title": "Connection Pool Usage", "type": "gauge" }
  ]
}
```

### Dashboard Maturity Model

Progressive levels of dashboard quality. Aim for Level 3 minimum for production dashboards.

| Level | Characteristics |
| --- | --- |
| 1 | Basic metrics visible, no structure, no thresholds, copied from export |
| 2 | Panels answer questions, stable UID, explicit datasource, basic thresholds |
| 3 | Follows USE/RED/Golden Signals, consistent units, meaningful titles, variables used deliberately |
| 4 | Includes runbook links, alert annotations, drill-down paths, self-documenting layout |
| 5 | Automated testing, versioned alongside code, reviewed on every change, part of on-call rotation |

## Time Picker Configuration

Control how the dashboard time range behaves.

```json
{
  "time": {
    "from": "now-30m",
    "to": "now"
  },
  "timezone": "browser",
  "graphTooltip": 0,
  "timepicker": {
    "refresh_intervals": ["5s", "10s", "30s", "1m", "5m", "15m", "30m", "1h"],
    "hidden": false,
    "collapse": false,
    "time_options": ["5m", "15m", "1h", "6h", "12h", "24h", "2d", "7d", "30d"]
  }
}
```

Key fields:

| Field | Values | Purpose |
| --- | --- | --- |
| `timezone` | `"browser"`, `"utc"`, `"America/New_York"` etc. | Which timezone to use for display |
| `graphTooltip` | `0` (single), `1` (per series), `2` (all series) | Tooltip behavior on hover |
| `timepicker.hidden` | `true`, `false` | Hide the time picker UI entirely |
| `timepicker.collapse` | `true`, `false` | Collapse time picker by default |
| `refresh` | `"5s"`, `"10s"`, `"30s"`, `"1m"`, `"5m"` etc. | Auto-refresh interval |

Panel-level time override -- individual panels can shift or constrain their own time range independently of the dashboard:

```json
{
  "timeFrom": "now-6h",
  "timeShift": "1d",
  "hideTimeOverride": false
}
```

Add these fields directly to a panel object (not inside `options`). `timeFrom` sets the earliest data point relative to now. `timeShift` shifts the entire query window (useful for day-over-day comparisons). `hideTimeOverride` hides the panel's custom time indicator.

## Repeat Behavior

Control how repeated panels lay out across the dashboard.

```json
{
  "repeat": "instance",
  "repeatDirection": "h",
  "maxPerRow": 3
}
```

| Field | Values | Effect |
| --- | --- | --- |
| `repeat` | variable name | Which variable drives repetition |
| `repeatDirection` | `"h"`, `"v"` | Horizontal or vertical layout |
| `maxPerRow` | integer | Max panels per row (horizontal mode only) |

When `repeatDirection` is `"h"`, panels fill left-to-right, wrapping to the next row after `maxPerRow`. When `"v"`, panels stack top-to-bottom. Keep one repeat driver per repeating panel or row. If you still need a two-dimensional layout, compose it from supported building blocks such as a repeated row for the outer dimension and panels inside that row using regular variable interpolation for titles and queries.

## JSON Model Boundary

Keep dashboard authoring and dashboard delivery separated even when both are reviewed together:

```text
reviewed source of truth: grafana/dashboards/api-overview.json
adjacent but separate concern: provisioning file that points at this dashboard
```

Dashboard authoring belongs here; provisioning configuration and rollout concerns stay in the adjacent delivery domain.

Direct dashboard asset layout -- keep the repository path explicit:

```text
grafana/
  dashboards/
    api-overview.json
```

Use when: the team keeps reviewed dashboard JSON directly in the repository rather than generating it from Jsonnet.

For Grafana mixin or Jsonnet-oriented dashboard generation -- including mixin layouts, Jsonnet source patterns, render commands, and source-vs-rendered handoff -- see [`./references/grafana-mixin.md`](./references/grafana-mixin.md).

For export cleanup decisions, normalization targets, and ownership boundaries after UI edits or rendering, see [`./references/dashboard-structure.md`](./references/dashboard-structure.md).

## Validate the Result

Validate the common case with these checks:

- dashboard JSON is syntactically valid
- the dashboard has a stable `uid` and explicit title
- the dashboard structure and panel arrangement answer one operator question clearly
- query expressions and datasource references are deliberate rather than copied from a random export
- variables and repeated panels are present only when they improve the normal read path
- transformations, field config, units, thresholds, and legends improve readability instead of hiding query problems
- links and annotations add operator context without turning the dashboard into a navigation maze
- the default time range stays within the last 30 minutes unless a wider window is explicitly justified
- panel types match the operator question (not just defaulting to timeseries for everything)
- variable types match the data source (query for dynamic lists, custom for static enums)
- overrides target the correct fields with appropriate matchers
- value mappings handle edge cases (null, NaN, empty) explicitly
- JSON model ownership is explicit: either the dashboard JSON is reviewed directly, or a generated workflow is clearly documented

## Output contract

Return:

1. the recommended dashboard asset or review decision
2. the source-of-truth path or source-vs-rendered ownership decision
3. validation results for JSON structure, datasource explicitness, and layout/readability checks
4. any remaining blockers, risks, or follow-up review points

## References

| If the blocker is... | Read... |
| --- | --- |
| Complete JSON schema for ALL panel types (bar gauge, candlestick, trend, XY chart, node graph, traces, flame graph, canvas, geomap, dashboard list, alert list, annotations list, text/news) | [`./references/panel-types.md`](./references/panel-types.md) |
| Complete variable reference: 7 standard classic variable types, global vars, format options, advanced patterns, boolean-toggle convention | [`./references/variables.md`](./references/variables.md) |
| Complete field config, all override property IDs, value mappings, data link variables, data link builder patterns | [`./references/field-config.md`](./references/field-config.md) |
| Grafana mixin or Jsonnet-oriented dashboard generation, source-vs-rendered handoff, render commands | [`./references/grafana-mixin.md`](./references/grafana-mixin.md) |
| Export cleanup decisions, normalization targets, ownership boundaries after UI edits or rendering | [`./references/dashboard-structure.md`](./references/dashboard-structure.md) |

## Invariants

- MUST keep dashboard identity stable with an explicit `uid`.
- MUST validate edited JSON before claiming the dashboard is ready.
- MUST keep the default dashboard time range within 30 minutes unless a wider window is explicitly justified.
- MUST keep ordinary dashboard authoring and review understandable from this file alone.
- SHOULD keep panels organized around one operator story rather than a random metric collection.
- MUST keep datasource references explicit.
- SHOULD use variables, repeat, transformations, field config, thresholds, legends, links, and annotations only when they serve the operator question directly.
- SHOULD choose panel types that match the operator question, not default to timeseries for everything.
- SHOULD follow USE, RED, or Four Golden Signals framework when structuring service dashboards.
- MUST NOT include unstable runtime-only fields in Git-owned dashboard JSON.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| copying a UI export with unstable metadata and no cleanup | reviews become noisy and identity drifts | normalize the JSON and keep a stable `uid`, title, and panel structure |
| setting the default dashboard range broader than 30 minutes with no operator reason | live queries scan far more data than the common path needs | start with `now-30m` and widen only when the investigation needs more history |
| treating generated or exported dashboard JSON as authoritative without checking the source workflow behind it | reviewers lose track of where the asset really comes from | keep direct JSON and mixin-generated workflows explicit and review the right source of truth |
| mixing unrelated panels into one dashboard | operators cannot read the story quickly | group panels by one question such as traffic, latency, and errors |
| leaving datasource references implicit or environment-specific without review | dashboards break when moved between environments | make datasource references explicit in the dashboard asset itself |
| using timeseries panels for everything regardless of the question | single values look wrong on line charts, categorical data looks wrong on time axes | pick the panel type that matches the data shape and operator question |
| using `$var` syntax with `=` for multi-value variables in PromQL label matchers | multi-value selections expand as a regex alternation, not as one literal label value | use `=~"${var:regex}"` and set a safe custom All value such as `.+` when needed |
| forgetting to handle null/NaN in value mappings | missing data shows as ugly raw values or breaks visualizations | always include a `special` mapping for `null` and `NaN` |
| putting thresholds on a field without setting min/max when using percentage mode | percentage thresholds have no basis and behave unpredictably | either use absolute mode, or set explicit `min` and `max` for percentage mode |
| overriding by name on auto-generated field names like "Value #A" | field names change when queries are edited, breaking overrides | use `byRegexp` or `byFrameRefID` matchers for query-derived fields |

## Scope Boundaries

- Activate this skill for:
  - Grafana dashboard JSON authoring and review
  - dashboard identity, panel organization, variables, transformations, field config, thresholds, legends, units, links, annotations, panel types, overrides, value mappings, data links, query options, best practice frameworks, and JSON-model-aware review
- Do not activate for:
  - Prometheus rule files and rule evaluation guidance
  - deep PromQL language design beyond what is needed to read or place a dashboard query
  - dashboard provisioning configuration files and environment rollout policy
  - application instrumentation choices
  - generic UI styling choices outside observability dashboards
  - Grafana alerting rules and contact routing (alert rule JSON is a separate concern)
