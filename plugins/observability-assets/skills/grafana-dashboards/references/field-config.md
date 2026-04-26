---
title: Field Config, Overrides, Value Mappings, and Data Links Reference
description: "Open this when you need the complete field config option catalog, all override property IDs per panel type, value mapping patterns, data link variable reference, or data link builder patterns."
---

## Field Config, Overrides, Value Mappings, and Data Links Reference

Use this reference when you need complete details on how to configure field appearance, target specific fields for different visual treatment, map raw values to display text/colors, or attach clickable URLs to data points.

## Field Config Architecture

Field config is applied at two levels:

1. **Defaults** (`fieldConfig.defaults`): applied to every field in the panel
2. **Overrides** (`fieldConfig.overrides`): applied to matched fields after defaults

Processing order: defaults first, then each override in array order. Later overrides win on conflicts.

```json
{
  "fieldConfig": {
    "defaults": {
      "unit": "short",
      "min": 0,
      "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] }
    },
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

## Complete Default Field Config Options

These options apply to `fieldConfig.defaults`. Not all options are meaningful for every panel type; irrelevant options are silently ignored.

### Core Options

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `unit` | string | `"short"` | Unit specifier (see unit catalog below) |
| `decimals` | number/`null` | `null` (auto) | Decimal places to show. `null` = auto based on value magnitude |
| `displayName` | string | `""` | Override field name in legends and tooltips. Empty = use original name |
| `noValue` | string/`null` | `null` | Text shown when value is null/missing |
| `min` | number/`null` | `null` | Hard minimum for axis/scale |
| `max` | number/`null` | `null` | Hard maximum for axis/scale |
| `mappings` | array | `[]` | Value mappings (see Value Mappings section) |
| `thresholds` | object | see below | Threshold steps with colors |
| `color` | object | see below | Color scheme configuration |
| `links` | array | `[]` | Data links (see Data Links section) |
| `custom` | object | `{}` | Panel-type-specific custom options |

### Color Configuration

```json
{
  "color": {
    "mode": "palette-classic",
    "spec": {
      "mode": "fixed"
    }
  }
}
```

Color modes: `"palette-classic"`, `"palette-spectral"`, `"continuous-GrYlRd"`, `"continuous-RdYlGr"`, `"continuous-blues"`, `"continuous-reds"`, `"continuous-greens"`, `"continuous-purples"`, `"scheme"`, `"thresholds"`, `"fixed"`.

When mode is `"scheme"`, specify which color scheme:

```json
{
  "color": {
    "mode": "scheme",
    "spec": {
      "scheme": "Twilight"
    }
  }
}
```

Named schemes: `"Classic"`, `"Green Orange"`, `"Yellow to Red"`, `"Blue to Yellow"`, `"Cool to Warm"`, `"Purple Blue Green"`, `"Warm Colors"`, `"Spectrum"`, `"Red Shades"`, `"Orange Shades"`, `"Blue Shades"`, `"Green Shades"`, `"Gray Monochrome"`, `"Palette Classic"`, `"Palette Spectral"`, `"Dark"`, `"From Thresholds"`, `"State"`, `"Twilight"`, `"System"`.

### Complete Unit Catalog

Time units:

| Specifier | Example Output | Meaning |
| --- | --- | --- |
| `"s"` | `1.23 s` | Seconds |
| `"ms"` | `123 ms` | Milliseconds |
| `"us"` | `123 us` | Microseconds |
| `"ns"` | `123 ns` | Nanoseconds |

Data rate units:

| Specifier | Example Output | Meaning |
| --- | --- | --- |
| `"bps"` | `1.23 bps` | Bits per second |
| `"Bps"` | `1.23 Bps` | Bytes per second |
| `"pps"` | `1.23 pps` | Packets per second |
| `"reqps"` | `1.23 reqps` | Requests per second |
| `"ops"` | `1.23 ops` | Operations per second |
| `"cps"` | `1.23 cps` | Counts per second |
| `"cpm"` | `1.23 cpm` | Counts per minute |
| `"count:ps"` | `1.23 /sec` | Generic count per second |

Data size units:

| Specifier | Example Output | Meaning |
| --- | --- | --- |
| `"bytes"` | `1.23 GB` | Bytes (auto SI) |
| `"bits"` | `1.23 Gbit` | Bits (auto SI) |
| `"si:Y"` through `"si:y"` | varies | Explicit SI prefix (Y, Z, E, P, T, G, M, k, m, u, n, p, f, a, z, y) |

Percent units:

| Specifier | Example Output | Meaning |
| --- | --- | --- |
| `"percent"` | `12.3 %` | Percentage (0-100) |
| `"percentunit"` | `0.123` | Fractional (0-1) |

Miscellaneous units:

| Specifier | Example Output | Meaning |
| --- | --- | --- |
| `"short"` | `1.23 k` | Auto-shortened number |
| `"Hz"` | `60 Hz` | Hertz |
| `"rpm"` | `3000 rpm` | Revolutions per minute |
| `"volt"` | `12 V` | Volts |
| `"watt"` | `150 W` | Watts |
| `"degC"` / `"celsius"` | `23 C` | Degrees Celsius |
| `"degF"` / `"fahrenheit"` | `73 F` | Degrees Fahrenheit |
| `"ppm"` | `123 ppm` | Parts per million |
| `"dB"` | `-3 dB` | Decibels |
| `"lux"` | `500 lux` | Lux |
| `"pH"` | `7.4 pH` | pH level |
| `"misc"` | raw | No formatting |

Date/time units:

| Specifier | Format | Example |
| --- | --- | --- |
| `"time:YYYY-MM-DD HH:mm:ss"` | Custom datetime format | `2025-01-15 14:30:00` |
| `"datetimescale:ms"` | Relative time (ms) | `2h ago` |
| `"datetimescale:s"` | Relative time (seconds) | `2h ago` |
| `"clock:HH:mm"` | Time only | `14:30` |
| `"clock:ISO"` | ISO datetime | `2025-01-15T14:30:00Z` |
| `"d:h:m:s"` | Duration | `1d 2h 3m 4s` |
| `"d:days"` | Days | `1.08 days` |
| `"d:hours"` | Hours | `26.05 h` |
| `"d:minutes"` | Minutes | `1563 min` |
| `"d:seconds"` | Seconds | `93784 s` |
| `"d:milliseconds"` | Milliseconds | `93784000 ms` |

Custom units use `"prefix:suffix"` syntax. For example:

- `"$:USD"` displays as `$123 USD`
- `"%:used"` displays as `75% used`
- `"°C:temp"` displays as `23°C temp`

## Thresholds -- Complete Reference

Thresholds define color boundaries that drive visual indicators across many panel types (stat background color, gauge arc segments, timeseries threshold lines, table cell coloring).

### Absolute Mode

The default and most common mode. Steps trigger at exact numeric values.

```json
{
  "thresholds": {
    "mode": "absolute",
    "steps": [
      { "color": "green", "value": null },
      { "color": "yellow", "value": 50 },
      { "color": "red", "value": 100 }
    ]
  }
}
```

Rules:

- The **first step** MUST have `"value": null`. This is the base/default color.
- Subsequent steps define boundaries where the color changes.
- A value of `50` means: values from 50 up to the next step boundary get yellow.
- The last step's color applies to everything at or above its value.
- Steps MUST be sorted by value in ascending order.

Supported step colors: any CSS named color, hex code (`"#FF5733"`), or Grafana semantic names (`"green"`, `"yellow"`, `"red"`, `"orange"`, `"blue"`, `"purple"`, `"gray"`, `"semi-dark-green"`, `"dark-green"`, `"dark-yellow"`, `"dark-red"`, `"light-red"`).

### Percentage Mode

Steps trigger at percentages of the `(min, max)` range. Requires both `min` and `max` to be set on the same field config.

```json
{
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "min": 0,
      "max": 100,
      "thresholds": {
        "mode": "percentage",
        "steps": [
          { "color": "green", "value": 0 },
          { "color": "yellow", "value": 70 },
          { "color": "red", "value": 90 }
        ]
      }
    }
  }
}
```

Calculation: `trigger_value = min + (percentage * (max - min)) / 100`.

With `min: 0`, `max: 100`:

- yellow triggers at `0 + (70 * 100 / 100) = 70`
- red triggers at `0 + (90 * 100 / 100) = 90`

With `min: 0`, `max: 200`:

- yellow triggers at `0 + (70 * 200 / 100) = 140`
- red triggers at `0 + (90 * 200 / 100) = 180`

Use percentage mode when the absolute thresholds depend on a dynamic range that may change between environments or queries.

## Overrides System -- Complete Reference

### Matcher Types

Five matcher types determine which fields an override targets.

#### byName -- Exact Field Name Match

Matches fields whose name exactly equals the given string.

```json
{
  "matcher": { "id": "byName", "options": "errors" }
}
```

Best for: well-known field names that are stable across query edits. Avoid using with auto-generated names like `"Value #A"` since these change when query order changes.

#### byRegexp -- Regex Pattern Match

Matches fields whose name matches the regex pattern.

```json
{
  "matcher": { "id": "byRegexp", "options": "/_total$/" }
}
```

Best for: matching groups of related fields (e.g., all counter suffixes, all percentile fields). More robust than `byName` when query structure evolves.

Common useful patterns:

| Pattern | Matches |
| --- | --- |
| `/^p(50&#124;90&#124;95&#124;99)$/` | Percentile fields |
| `/_total$/` | Counter metrics ending in `_total` |
| `/_bucket$/` | Histogram bucket metrics |
| `/^Value #[A-Z]$/` | Auto-generated series values |
| `/^(Time&#124;__name__)$/` | Metadata columns |

#### byType -- Semantic Type Match

Matches fields by their data type.

```json
{
  "matcher": { "id": "byType", "options": "number" }
}
```

Types: `"number"`, `"string"`, `"time"`, `"boolean"`. Best for applying consistent formatting to all numeric or all string fields regardless of their names.

#### byFrameRefID -- Query Source Match

Matches all fields produced by a specific query (identified by refId).

```json
{
  "matcher": { "id": "byFrameRefID", "options": "B" }
}
```

Best for: hiding a secondary query's output from the legend while keeping it available for calculations, or applying different styling to data from different datasources.

#### byValue -- Specific Value Match

Matches fields where the field's value equals (or does not equal) a specific value.

```json
{
  "matcher": { "id": "byValue", "options": { "op": "=", "value": "200" } }
}
```

Operators: `"="`, `"!="`, `">"`, `"<"`, `">="`, `"<="`, `"regex"`, `"!regex"`.
Best for: table panels where specific cell values need distinct formatting (e.g., color HTTP 200 green, 5xx red).

### Override Property IDs by Category

Each property that can be overridden has a unique ID string. Below is the organized catalog.

#### Visual Properties

| Property ID | Value Type | Description |
| --- | --- | --- |
| `"color"` | `{ mode, fixedColor?, spec? }` | Color scheme override |
| `"unit"` | string | Unit specifier |
| `"decimals"` | number or null | Decimal places |
| `"min"` | number or null | Axis minimum |
| `"max"` | number or null | Axis maximum |
| `"displayName"` | string | Display name override |
| `"noValue"` | string or null | Null value text |
| `"thresholds"` | `{ mode, steps[] }` | Threshold definition |
| `"mappings"` | `mapping[]` | Value mappings |
| `"links"` | `link[]` | Data links |
| `"custom.cellOptions"` | object | Table cell rendering options |
| `"custom.align"` | string | Text alignment |
| `"custom.width"` | number | Column width in pixels |
| `"custom.filterable"` | boolean | Column filterable flag |
| `"custom.inspect"` | boolean | Inspect column toggle |

#### Timeseries-Specific Properties

| Property ID | Value Type | Description |
| --- | --- | --- |
| `"custom.lineWidth"` | number | Line thickness (pixels) |
| `"custom.fillOpacity"` | number | Area fill opacity (0-100) |
| `"custom.lineStyle"` | `{ fill, dash[] }` | Line style (solid/dashed) |
| `"custom.drawStyle"` | string | Draw style: line/bars/points |
| `"custom.pointSize"` | number | Point marker size |
| `"custom.stacking"` | `{ mode, group }` | Stacking configuration |
| `"custom.spanNulls"` | boolean | Connect across nulls |
| `"custom.showPoints"` | string | When to show points: never/auto/always |
| `"custom.gradientMode"` | string | Gradient fill mode |
| `"custom.barAlignment"` | number | Bar alignment (-1/0/1) |
| `"custom.barRadius"` | number | Bar corner radius |
| `"custom.lineInterpolation"` | string | Line smoothing: linear/smooth/stepAfter/stepBefore |
| `"custom.hideFrom"` | `{ legend, tooltip, viz }` | Hide from UI elements |
| `"custom.axisPlacement"` | string | Axis position: auto/left/right/top/bottom |
| `"custom.axisCenteredZero"` | boolean | Center Y axis at zero |
| `"custom.axisSoftMin"` | number or null | Soft minimum |
| `"custom.axisSoftMax"` | number or null | Soft maximum |
| `"custom.scaleDistribution"` | `{ type, log, linearThreshold }` | Scale type: linear/log |
| `"custom.thresholdsStyle"` | `{ mode }` | Threshold line style: off/line/area/dashed-line/dashed-area |

#### Stat/Gauge-Specific Properties

| Property ID | Value Type | Description |
| --- | --- | --- |
| `"custom.textMode"` | string | Text display mode |
| `"custom.colorMode"` | string | Color application mode |
| `"custom.graphMode"` | string | Sparkline graph mode |
| `"custom.orientation"` | string | Horizontal/vertical layout |
| `"custom.reduceOptions"` | `{ calcs[], fields, values }` | Reduction function selection |

### Override Examples by Scenario

**Hide a secondary query from the legend:**

```json
{
  "matcher": { "id": "byFrameRefID", "options": "B" },
  "properties": [
    { "id": "custom.hideFrom", "value": { "legend": true, "tooltip": false, "viz": false } }
  ]
}
```

**Make error counters red and dashed:**

```json
{
  "matcher": { "id": "byRegexp", "options": "/error/" },
  "properties": [
    { "id": "color", "value": { "mode": "fixed", "fixedColor": "red" } },
    { "id": "custom.lineWidth", "value": 2 },
    { "id": "custom.lineStyle", "value": { "fill": "solid", "dash": [10, 10] } },
    { "id": "custom.fillOpacity", "value": 0 }
  ]
}
```

**Format all numeric fields as percent with 1 decimal:**

```json
{
  "matcher": { "id": "byType", "options": "number" },
  "properties": [
    { "id": "unit", "value": "percent" },
    { "id": "decimals", "value": 1 }
  ]
}
```

**Color table cells by status code value:**

```json
{
  "matcher": { "id": "byValue", "options": { "op": ">=", "value": "500" } },
  "properties": [
    { "id": "custom.cellOptions", "value": { "type": "color-background" } },
    { "id": "thresholds", "value": { "mode": "absolute", "steps": [{ "color": "red", "value": null }] } }
  ]
}
```

## Value Mappings -- Complete Reference

Value mappings translate raw data values into displayed text and/or colors without changing the underlying data. They apply at render time only and do not affect query results or alert evaluations.

Four mapping types exist. Multiple types can coexist in the same `mappings` array. Evaluation order is array order; the first match wins.

### Type 1: Value Mapping

Exact match on specific scalar values.

```json
{
  "type": "value",
  "options": {
    "0": { "text": "OK", "color": "green" },
    "1": { "text": "WARNING", "color": "yellow" },
    "2": { "text": "CRITICAL", "color": "red" },
    "3": { "text": "UNKNOWN", "color": "gray" }
  }
}
```

Key-value pairs where keys are the raw values (as strings) and values contain optional `text` (display label) and `color` (display color). Both `text` and `color` are optional within each result.

### Type 2: Range Mapping

Map numeric ranges to labels. Ranges are inclusive on both ends.

```json
{
  "type": "range",
  "options": [
    { "from": 0, "to": 199, "result": { "text": "Healthy", "color": "green" } },
    { "from": 200, "to": 499, "result": { "text": "Degraded", "color": "yellow" } },
    { "from": 500, "to": 999999, "result": { "text": "Down", "color": "red" } }
  ]
}
```

Ranges must not overlap. Order does not matter for range evaluation -- Grafana finds the matching range by checking which range contains the value.

### Type 3: Regex Mapping

Pattern-based matching against string values.

```json
{
  "type": "regex",
  "options": [
    { "pattern": "^2..$", "result": { "text": "Success", "color": "green" } },
    { "pattern": "^3..$", "result": { "text": "Redirect", "color": "blue" } },
    { "pattern": "^4..$", "result": { "text": "Client Error", "color": "yellow" } },
    { "pattern": "^5..$", "result": { "text": "Server Error", "color": "red" } },
    { "pattern": ".*", "result": { "text": "Unknown", "color": "gray" } }
  ]
}
```

Patterns are evaluated in array order. Use `.*` as a catch-all fallback at the end of the list.

### Type 4: Special Mapping

Handle sentinel values that have no regular representation: null, NaN, boolean states, and empty strings.

```json
{
  "type": "special",
  "options": {
    "null": { "text": "No Data", "color": "gray" },
    "NaN": { "text": "Invalid", "color": "purple" },
    "true": { "text": "Yes", "color": "green" },
    "false": { "text": "No", "color": "red" },
    "": { "text": "(empty)", "color": "light-gray" }
  }
}
```

Always include special mappings when your data source can produce null or NaN values. Without them, stat panels show ugly raw values like `"null"` or `"NaN"`.

### Combined Mapping Example

Real-world pattern combining multiple mapping types:

```json
{
  "mappings": [
    {
      "type": "special",
      "options": {
        "null": { "text": "--", "color": "gray" },
        "NaN": { "text": "N/A", "color": "gray" }
      }
    },
    {
      "type": "value",
      "options": {
        "0": { "text": "UP", "color": "green" },
        "1": { "text": "DOWN", "color": "red" }
      }
    },
    {
      "type": "regex",
      "options": [
        { "pattern": "partial", "result": { "text": "DEGRADED", "color": "yellow" } }
      ]
    }
  ]
}
```

Evaluation order: special checks first (null/NaN), then exact value match, then regex pattern match.

## Data Links -- Complete Reference

Data links attach clickable URLs to data points. When an operator clicks a data point (or table cell, or bar segment), the URL opens with interpolated context variables.

### Basic Structure

```json
{
  "fieldConfig": {
    "defaults": {
      "links": [
        {
          "title": "View Traces",
          "url": "https://jaeger.example.com/search${__url_time_range}&service=${__data.fields.service}",
          "targetBlank": true,
          "tooltip": "Open trace view in Jaeger"
        },
        {
          "title": "Runbook",
          "url": "https://wiki.example.com/runbooks/${__data.fields.alertname}",
          "targetBlank": true,
          "tooltip": ""
        }
      ]
    }
  }
}
```

Link fields:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `title` | string | Yes | Link text shown in tooltip/hover |
| `url` | string | Yes | URL template with variable interpolation |
| `targetBlank` | boolean | No | Open in new tab (default: false) |
| `tooltip` | string | No | Additional hover text |

### Data Link Variables -- Complete Catalog

These variables are available inside data link `url` and `title` fields.

#### Series-Level Variables

Available when the link is attached to a series/field rather than a specific data point.

| Variable | Expands To | Example |
| --- | --- | --- |
| `${__series.name}` | Full series name / legend string | `http_requests_total{method="GET",status="200"}` |
| `${__series.labels.<label>}` | Specific label from the series | `${__series.labels.method}` -> `GET` |

#### Field-Level Variables

Available for the specific field/column the link is attached to.

| Variable | Expands To | Example |
| --- | --- | --- |
| `${__field.name}` | Field name | `Value #A` |
| `${__field.displayName}` | Display name (after displayName override) | `requests_per_sec` |
| `${__field.labels.<label>}` | Label value by key | `${__field.labels.instance}` -> `host-01:9100` |
| `${__field.type}` | Field data type | `number` |

#### Row/Data Frame Variables

Access other fields in the same row or data frame.

| Variable | Expands To | Example |
| --- | --- | --- |
| `${__data.fields.<name>}` | Any field value in the same row | `${__data.fields.path}` -> `/api/users` |
| `${__data.frames.<index>.fields.<name>}` | Field from another frame by index | `${__data.frames.1.fields.status}` -> `200` |

#### Value Variables

Available when clicking a specific data point (not just hovering over a series).

| Variable | Expands To | Example |
| --- | --- | --- |
| `${__value.time}` | Timestamp of the clicked point (epoch ms) | `1713556800000` |
| `${__value.raw}` | Raw numeric value of the point | `42.5` |
| `${__value.numeric}` | Numeric value (same as raw for numbers) | `42.5` |
| `${__value.text}` | Formatted/display text of the value | `42.50 req/s` |
| `${__value.calc}` | Calculated/reduced value (if reduce applied) | `38.2` |

#### System Variables

| Variable | Expands To | Example |
| --- | --- | --- |
| `${__url_time_range}` | Current dashboard time range as query parameters, including leading `?` | `?from=now-30m&to=now` |
| `${__from}` | Dashboard start time (epoch ms) | `1713556800000` |
| `${__to}` | Dashboard end time (epoch ms) | `1713558600000` |
| `${__dashboard.uid}` | Current dashboard UID | `api-overview` |
| `${__dashboard.name}` | Dashboard title | `API Overview` |

### Data Link Builder Patterns

#### Pattern 1: Drill-down to tracing backend

```json
{
  "title": "View Trace",
  "url": "https://jaeger.example.com/search${__url_time_range}&service=${__data.fields.service}&operation=${__data.fields.operation}",
  "targetBlank": true
}
```

#### Pattern 2: Open runbook for alert

```json
{
  "title": "Runbook",
  "url": "https://wiki.example.com/runbooks/${__data.fields.alertname}",
  "targetBlank": true
}
```

#### Pattern 3: Filter downstream dashboard by clicked value

```json
{
  "title": "Drill into Service",
  "url": "/d/service-detail?var-service=${__data.fields.service}&from=${__from}&to=${__to}"
}
```

Note: internal dashboard links use relative paths starting with `/d/<uid>` or `/d-solo/<uid>`.

#### Pattern 4: External monitoring tool with time context

```json
{
  "title": "View in Datadog",
  "url": "https://app.datadoghq.com/metric/explorer?query=${__series.name:percentencode}&from=${__from}&to=${__to}",
  "targetBlank": true
}
```

#### Pattern 5: Source code lookup with instance context

```json
{
  "title": "View Source",
  "url": "https://github.example.com/org/repo/search?q=instance:${__data.fields.instance:percentencode}",
  "targetBlank": true
}
```

### Per-Field vs Per-Default Links

Links can be set at the default level (apply to all fields) or via overrides (apply to specific fields):

```json
{
  "fieldConfig": {
    "defaults": {
      "links": [
        { "title": "Details", "url": "https://example.com/detail?id=${__data.fields.id}" }
      ]
    },
    "overrides": [
      {
        "matcher": { "id": "byName", "options": "error_count" },
        "properties": [
          {
            "id": "links",
            "value": [
              { "title": "Error Details", "url": "https://example.com/errors?id=${__data.fields.error_id}", "targetBlank": true },
              { "title": "Runbook", "url": "https://wiki.example.com/runbooks/${__data.fields.alertname}" }
            ]
          }
        ]
      }
    ]
  }
}
```

Override links replace (do not merge with) default links for matched fields. Include all needed links in the override if you want to preserve default links alongside additional ones.
