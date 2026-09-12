---
name: grafana-dashboards
description: >-
  Author and review Grafana dashboards as version-controlled JSON assets with stable uid, deliberate datasource handling, and operator-centric panel layout.
  Triggers on Grafana dashboard creation or review, classic dashboard JSON editing, panel or query configuration, variables, transformations, field config, thresholds, overrides, value mappings, uid stabilization, USE/RED/Golden Signals layout, or Grafana mixin and Jsonnet generation workflows.
---

# Grafana Dashboards

Author and review Grafana dashboards as version-controlled assets while keeping dashboard identity stable across environments.

The common case: one dashboard with a stable `uid`, a deliberate title, explicit datasource handling, a default time range no broader than the last 30 minutes, and a panel layout that answers a real operator question instead of becoming a generic metric scrapbook.

Detailed JSON schemas live in package-local references, not in this file:

- Complete panel-type schemas: [`./references/panel-types.md`](./references/panel-types.md)
- Variable types, syntax, global variables, and repeat fields: [`./references/variables.md`](./references/variables.md)
- Field config, overrides, value mappings, data links, and the unit catalog: [`./references/field-config.md`](./references/field-config.md)
- Grafana mixin configuration and Jsonnet generation: [`./references/grafana-mixin.md`](./references/grafana-mixin.md)
- Export cleanup decisions and ownership boundaries after UI edits or rendering: [`./references/dashboard-structure.md`](./references/dashboard-structure.md)

## Common-Case Workflow

1. Start from the operator question the dashboard must answer.
2. Keep one stable dashboard identity with a deliberate `uid` and title.
3. Choose the datasource and queries deliberately, then shape the dashboard around the returned data rather than copying an arbitrary UI export.
4. Pick the right panel type for each question (see Panel Type Decision Guide below).
5. Add variables, repeated panels, transformations, field config, units, thresholds, and legends only when they improve operator readability for the main question.
6. Keep links, annotations, and layout aligned to one narrative flow such as saturation, errors, and latency.
7. Default the dashboard time range to the last 30 minutes or less, and widen it only when the operator question needs more history.

Default to Grafana's V2 Resource model when the task is about Grafana 13 Observability as Code or the newer `/apis` dashboard flow.
Keep classic dashboard JSON for file provisioning, UI export cleanup, grafana.com dashboard sharing, or repositories that already store classic dashboard files.
When a classic example includes `schemaVersion`, treat it as an example/export value and preserve or regenerate it from the target Grafana version instead of asserting that one number is universally current.

## Minimal Setup

Minimal dashboard JSON -- smallest valid shape for syntax testing:

```json
{
  "uid": "api-overview",
  "title": "API Overview",
  "schemaVersion": 41,
  "version": 1,
  "refresh": "30s",
  "panels": []
}
```

Use when: you need a minimal valid dashboard shell to validate JSON syntax before adding content.

## First Runnable Commands or Code Shape

Start by validating the JSON structure before the file is treated as a Git-owned dashboard asset:

```sh
uv run -m json.tool grafana/dashboards/api-overview.json
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
  "schemaVersion": 41,
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
        "datasource": { "type": "prometheus", "uid": "prometheus" },
        "query": "label_values(up{job=\"api\"}, instance)",
        "current": { "selected": false, "text": "All", "value": "$__all" },
        "hide": 0
      }
    ]
  },
  "panels": [
    {
      "title": "Request Rate - ${instance}",
      "repeat": "instance",
      "repeatDirection": "h",
      "maxPerRow": 3
    }
  ]
}
```

Use when: one panel shape should repeat across a bounded variable set without copying panel JSON by hand.
This is a fragment to merge into the full dashboard shell, not a standalone importable dashboard.

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

Use when: operators need one direct drilldown and one timeline context source while reading the dashboard.
This is a fragment to merge into the full dashboard shell, not a complete dashboard by itself.

### Query Options

Per-target fields that control how a Prometheus query runs:

| Field | Type | Purpose |
| --- | --- | --- |
| `format` | string | `"time_series"` (default), `"table"`, or `"heatmap"`; maps to the UI Format option |
| `instant` / `range` | boolean | Selects an instant or range query (Both sets both `true`; omit `instant` for a range query) |
| `interval` | string | Min step and `$__interval` override (e.g., `15s`, `1m`); also accepts `$__rate_interval` |
| `intervalMs` / `maxDataPoints` | integer | Computed by Grafana per request and serialized into exports; not authored by hand |
| `legendFormat` | string | Legend template such as `"{{job}}"` or a fixed label |

Time-range overrides such as `timeFrom` and `timeShift` are panel-level fields, not per-target.
For day-over-day comparison inside one panel, shift the second query in PromQL with `offset` (for example `rate(http_requests_total[5m] offset 1d)`) instead of a per-target time shift.

## Panel Type Decision Guide

Choose the panel type based on what the operator needs to see.
The most common types are listed first.
Full JSON schemas for every panel type, including option keys such as `drawStyle`, `textMode`, and `graphMode`, are in [`./references/panel-types.md`](./references/panel-types.md).

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

## Variables, Transformations, and Field Config

The common path uses these defaults.
Open the owning reference for complete schemas:

- Variables: query variables for dynamic lists, custom variables for small static enums, textbox variables for ad-hoc input.
  - Sanitize textbox values in queries to prevent injection.
  - All 7 standard classic variable types, global variables, and format modifiers: [`./references/variables.md`](./references/variables.md)
- Transformations reshape query results before rendering.
  - Apply them in order.
  - Each transformation receives the output of the previous one.
  - Full catalog of 20+ transformation types: [`./references/variables.md`](./references/variables.md)
- Field config controls units, decimals, thresholds, mappings, and data links after queries return results.
  - The first threshold step always has `value: null` (base color).
  - Percentage mode requires explicit `min`/`max` on the field.
  - Overrides pair matchers with properties.
  - Value mappings are evaluated in order and the first match wins.
  - Unit catalog (~60 specifiers), all 5 override matcher types, all 4 mapping types, and the data link variable catalog: [`./references/field-config.md`](./references/field-config.md)

## Best Practice Frameworks

Structure dashboards around proven observability frameworks so every panel answers a meaningful operational question.

### USE Method

Focus on Utilization, Saturation, and Errors for resource-centric views.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Utilization | How busy is the resource? | `node_cpu_seconds_total` (non-idle %) |
| Saturation | How much demand is queued? | `node_load_avg`, queue depth, conn count |
| Errors | How many operations failed? | Error rate, 5xx count, error ratio |

### RED Method

Focus on Rate, Errors, and Duration for request-driven services.

| Dimension | Question | Example Metric |
| --- | --- | --- |
| Rate | How many requests per second? | `rate(http_requests_total[5m])` |
| Errors | How many are failing? | `rate(http_requests_total{status=~"5.."}[5m])` |
| Duration | How long do requests take? | `histogram_quantile(0.99, ...)` |

### Four Golden Signals

Google's SRE framework: Latency, Traffic, Errors, and Saturation.

| Signal | Question | Example Metric |
| --- | --- | --- |
| Latency | How long do requests take? | Service response time percentiles |
| Traffic | How much demand is there? | Requests/sec, connections/sec |
| Errors | How many are failing? | Error rate, failure percentage |
| Saturation | How close to capacity? | CPU, memory, disk, connection pool usage |

### Dashboard Maturity Model

Progressive levels of dashboard quality.
Aim for Level 3 minimum for production dashboards.

| Level | Characteristics |
| --- | --- |
| 1 | Basic metrics visible, no structure, no thresholds, copied from export |
| 2 | Panels answer questions, stable UID, explicit datasource, basic thresholds |
| 3 | Follows USE/RED/Golden Signals, consistent units, meaningful titles, variables used deliberately |
| 4 | Includes runbook links, alert annotations, drill-down paths, self-documenting layout |
| 5 | Automated testing, versioned alongside code, reviewed on every change, part of on-call rotation |

## Time Picker and Repeat Behavior

Dashboard-level time controls:

| Field | Values | Purpose |
| --- | --- | --- |
| `timezone` | `"browser"`, `"utc"`, `"America/New_York"` etc. | Which timezone to use for display |
| `graphTooltip` | `0` (single), `1` (per series), `2` (all series) | Tooltip behavior on hover |
| `timepicker.hidden` | `true`, `false` | Hide the time picker UI entirely |
| `refresh` | `"5s"`, `"10s"`, `"30s"`, `"1m"`, `"5m"` etc. | Auto-refresh interval |

Repeat fields on a panel or row:

| Field | Values | Effect |
| --- | --- | --- |
| `repeat` | variable name | Which variable drives repetition |
| `repeatDirection` | `"h"`, `"v"` | Horizontal or vertical layout |
| `maxPerRow` | integer | Max panels per row (horizontal mode only) |

Keep one repeat driver per repeating panel or row.
If you still need a two-dimensional layout, compose it from supported building blocks such as a repeated row for the outer dimension and panels inside that row using regular variable interpolation for titles and queries.

## JSON Model Boundary

Keep dashboard authoring and dashboard delivery separated even when both are reviewed together:

```text
reviewed source of truth: grafana/dashboards/api-overview.json
adjacent but separate concern: provisioning file that points at this dashboard
```

Dashboard authoring belongs here.
Provisioning configuration and rollout concerns stay in the adjacent delivery domain.

Direct dashboard asset layout -- keep the repository path explicit:

```text
grafana/
  dashboards/
    api-overview.json
```

Use when: the team keeps reviewed dashboard JSON directly in the repository rather than generating it from Jsonnet.
For mixin configuration, Jsonnet source patterns, and source-vs-rendered handoff, see [`./references/grafana-mixin.md`](./references/grafana-mixin.md).
For export cleanup decisions and ownership boundaries after UI edits or rendering, see [`./references/dashboard-structure.md`](./references/dashboard-structure.md).

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
- panel types match the operator question instead of defaulting to timeseries for everything
- variable types match the data source (query for dynamic lists, custom for static enums)
- overrides target the correct fields with appropriate matchers
- value mappings handle edge cases (null, NaN, empty) explicitly
- JSON model ownership is explicit: either the dashboard JSON is reviewed directly, or a generated workflow is clearly documented

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. the recommended dashboard asset or review decision
2. the source-of-truth path or source-vs-rendered ownership decision
3. validation results for JSON structure, datasource explicitness, and layout/readability checks
4. any remaining blockers, risks, or follow-up review points

## References

| If the blocker is... | Read... |
| --- | --- |
| Complete JSON schema for any panel type and its options | [`./references/panel-types.md`](./references/panel-types.md) |
| Complete variable reference: 7 classic types, global vars, format options, advanced patterns, boolean-toggle convention | [`./references/variables.md`](./references/variables.md) |
| Complete field config, override property IDs, value mappings, data link variables, unit catalog | [`./references/field-config.md`](./references/field-config.md) |
| Mixin configuration and Jsonnet generation | [`./references/grafana-mixin.md`](./references/grafana-mixin.md) |
| Export cleanup decisions, normalization targets, ownership boundaries after UI edits or rendering | [`./references/dashboard-structure.md`](./references/dashboard-structure.md) |

## Invariants

- MUST keep dashboard identity stable with an explicit `uid`.
- MUST validate edited JSON before claiming the dashboard is ready.
- MUST keep the default dashboard time range within 30 minutes unless a wider window is explicitly justified.
- MUST keep ordinary dashboard authoring and review understandable from this file alone.
- MUST keep datasource references explicit.
- MUST NOT include unstable runtime-only fields in Git-owned dashboard JSON.
- SHOULD keep panels organized around one operator story rather than a random metric collection.
- SHOULD use variables, repeat, transformations, field config, thresholds, legends, links, and annotations only when they serve the operator question directly.
- SHOULD choose panel types that match the operator question, not default to timeseries for everything.
- SHOULD follow USE, RED, or Four Golden Signals framework when structuring service dashboards.

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
