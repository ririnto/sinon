---
title: Complete Panel Type Reference
description: "Open this when you need the full JSON schema for a specific panel type, or when deciding which panel type to use for a given data shape."
---

## Complete Panel Type Reference

Use this reference when you need the complete JSON structure for any Grafana panel type, including options fields unique to each type. The common panel types (timeseries, stat, gauge, barchart, table, heatmap, statetimeline, logs, piechart, histogram) are summarized in [`../SKILL.md`](../SKILL.md). This file covers ALL 23+ panel types with their complete option schemas.

## Panel Type Index

| Type String | Display Name | Data Shape | Primary Use |
| --- | --- | --- | --- |
| `timeseries` | Time series | Time-indexed numeric | Trends over time |
| `stat` | Stat | Single value | Current value with sparkline |
| `gauge` | Gauge | Single value in range | Value vs threshold |
| `bargauge` | Bar gauge | Single value per category | Category comparison |
| `barchart` | Bar chart | Categorical numeric | Category comparison |
| `histogram` | Histogram | Bucketed distribution | Value distribution |
| `heatmap` | Heatmap | Time x bucket grid | Density visualization |
| `piechart` | Pie chart | Categorical parts-of-whole | Proportional view |
| `table` | Table | Tabular rows/columns | Raw data inspection |
| `logs` | Logs | Log lines | Log search and analysis |
| `statetimeline` | State timeline | State periods over time | Status tracking |
| `statushistory` | Status history | Discrete status events | Event timeline |
| `candlestick` | Candlestick | OHLC data | Financial/range data |
| `trend` | Trend | Single value trend line | Direction indicator |
| `xychart` | XY chart | X-Y scatter/line | Correlation analysis |
| `nodegraph` | Node graph | Nodes and edges | Topology/dependency |
| `traces` | Traces | Trace spans | Distributed tracing |
| `flamegraph` | Flame graph | Call stack profiling | Performance profiling |
| `canvas` | Canvas | Free-form elements | Custom visual layout |
| `geomap` | Geo map | Geographic coordinates | Location-based data |
| `dashboardlist` | Dashboard list | Dashboard metadata | Navigation |
| `alertlist` | Alert list | Alert instances | Alert overview |
| `annotationslist` | Annotations list | Annotation events | Event timeline |
| `text` | Text / News | Static content | Documentation/instructions |

## Timeseries (`timeseries`)

Full options schema for the most commonly used panel type.

```json
{
  "type": "timeseries",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": ["mean", "max", "min"],
      "showLegend": true
    },
    "tooltip": {
      "mode": "single",
      "sort": "desc",
      "anchor": "auto"
    },
    "drawStyle": "line",
    "lineWidth": 1,
    "fillOpacity": 10,
    "pointSize": 5,
    "spanNulls": false,
    "showPoints": "never",
    "stacking": {
      "mode": "none",
      "group": "A"
    },
    "axisPlacement": "auto",
    "axisColorMode": "text",
    "axisLabel": "",
    "axisCenteredZero": false,
    "axisSoftMin": null,
    "axisSoftMax": null,
    "scaleDistribution": {
      "type": "linear",
      "log": 2,
      "linearThreshold": null
    },
    "thresholdsStyle": {
      "mode": "off"
    },
    "gradientMode": "none",
    "reverseYAxis": false,
    "barAlignment": 0,
    "lineInterpolation": "smooth",
    "lineStyle": {
      "fill": "solid",
      "dash": []
    },
    "hideFrom": {
      "tooltip": false,
      "legend": false,
      "viz": false
    },
    "mappingType": 1,
    "rangeMaps": [],
    "nullPointMode": "connected"
  }
}
```

Key `options.drawStyle` values: `"line"`, `"bars"`, `"points"`.
Key `options.legend.displayMode`: `"list"`, `"table"`, `"hidden"`.
Key `options.legend.calcs`: `"mean"`, `"count"`, `"total"`, `"min"`, `"max"`, `"range"`, `"first"`, `"delta"`, `"step"`, `"diff"`, `"allValues"`, `"allNullValues"`.
Key `options.tooltip.mode`: `"single"`, `"multi"`, `"none"`.
Key `options.stacking.mode`: `"none"`, `"normal"`, `"percent"`.
Key `options.gradientMode`: `"none"`, `"hue"`, `"opacity"`, `"scheme"`.
Key `options.showPoints`: `"never"`, `"auto"`, `"always"`.

## Stat (`stat`)

```json
{
  "type": "stat",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "textMode": "auto",
    "colorMode": "value",
    "graphMode": "area",
    "justifyMode": "auto",
    "orientation": "horizontal",
    "reduceOptions": {
      "calcs": ["lastNotNull"],
      "fields": "",
      "values": false
    },
    "sparkline": {
      "show": true,
      "full": false,
      "lineWidth": 1,
      "fillOpacity": 20,
      "yMax": null,
      "yMin": null
    },
    "wideLayout": true
  }
}
```

Key `options.textMode`: `"auto"`, `"name"`, `"none"`, `"value"`, `"name_value"`.
Key `options.colorMode`: `"none"`, `"value"`, `"background"`.
Key `options.graphMode`: `"none"`, `"area"`, `"linear"`.
Key `options.justifyMode`: `"auto"`, `"center"`.

## Gauge (`gauge`)

```json
{
  "type": "gauge",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "orientation": "horizontal",
    "reduceOptions": {
      "calcs": ["lastNotNull"],
      "fields": "",
      "values": false
    },
    "showThresholdMarkers": true,
    "showThresholdLabels": true",
    "minVizHeight": 0,
    "minVizWidth": 0
  }
}
```

## Bar Gauge (`bargauge`)

Displays one bar per category, oriented horizontally. Good for comparing a small number of static categories.

```json
{
  "type": "bargauge",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "orientation": "horizontal",
    "showValue": "auto",
    "displayMode": "gradient",
    "lowerIsBetter": false,
    "calculation": "lastNotNull",
    "justifyMode": "auto"
  }
}
```

Key `options.orientation`: `"horizontal"`, `"vertical"`.
Key `options.showValue`: `"auto"`, `"always"`, `"never"`.
Key `options.displayMode`: `"gradient"`, `"basic"`, `"lcd"`.
Key `options.calculation`: same reducer names as reduce transformation.

## Bar Chart (`barchart`)

```json
{
  "type": "barchart",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "orientation": "auto",
    "showValue": "auto",
    "stacking": "none",
    "groupWidth": 0.7,
    "fullHighlight": false",
    "xTickLabelRotation": 0,
    "xTickLabelSpacing": 0,
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": []
    },
    "tooltip": {
      "mode": "single",
      "sort": "none",
      "anchor": "auto"
    },
    "drawStyle": "bars",
    "lineWidth": 1,
    "fillOpacity": 70,
    "gradientMode": "none",
    "pointSize": 5,
    "showPoints": "never",
    "barRadius": 0,
    "hideFrom": {
      "tooltip": false,
      "legend": false,
      "viz": false
    }
  }
}
```

Key `options.orientation`: `"auto"`, `"vertical"`, `"horizontal"`.
Key `options.stacking`: `"none"`, `"normal"`, `"percent"`.
Key `options.drawStyle`: `"bars"`, `"line"`.

## Histogram (`histogram`)

```json
{
  "type": "histogram",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": []
    },
    "tooltip": {
      "mode": "single",
      "sort": "none"
    },
    "bucketOffset": 0,
    "bucketSize": 0,
    "bucketCount": null,
    "combine": false,
    "histogramBase": 10
  }
}
```

When `bucketSize` is 0 (default), Grafana auto-calculates bucket boundaries. Set it explicitly (e.g., `100`) for fixed-width buckets. `histogramBase` controls logarithmic bucketing: `2` for binary histogram buckets, `10` for decimal.

## Heatmap (`heatmap`)

```json
{
  "type": "heatmap",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "calculate": false,
    "cellGap": 2,
    "cellVariance": 0,
    "color": {
      "mode": "opacity-spectrum",
      "spec": {
        "mode": "fixed"
      }
    },
    "exemplars": {
      "color": "rgba(255,255,255,0.7)"
    },
    "filterValues": {
      "le": 1e-9
    },
    "legend": {
      "show": true,
      "calc": ["mean", "max"]
    },
    "rowsFrame": {
      "layout": "auto"
    },
    "tooltip": {
      "show": true,
      "showHistogram": false,
      "colorMode": "background",
      "yHistogram": false
    },
    "yAxis": {
      "axisPlacement": "left",
      "reverse": false,
      "decimals": null,
      "unit": "short",
      "distribution": null,
      "max": null,
      "min": null,
      "splitFactor": null
    },
    "dataFormat": "timeSeriesBuckets",
    "highlightCards": true,
    "hideZeroBuckets": false,
    "invertColorScheme": false,
    "valuesInReverseOrder": false
  }
}
```

Key `options.color.mode`: `"opacity-spectrum"`, `"palette-classic"`, `"continuous-GrYlRd"`, `"continuous-RdYlGr"`, `"continuous-blues"`, `"continuous-reds"`, `"continuous-greens"`, `"continuous-purples"`, `"scheme"`.
Key `options.dataFormat`: `"timeSeriesBuckets"`, `"timeSeries"`. Use `"timeSeriesBuckets"` for Prometheus histogram buckets.

## Pie Chart (`piechart`)

```json
{
  "type": "piechart",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "pieType": "donut",
    "tooltip": {
      "mode": "single",
      "sort": "none",
      "anchor": "auto"
    },
    "legend": {
      "displayMode": "table",
      "placement": "right",
      "calcs": ["percent"],
      "values": ["percent"],
      "showLegend": true
    },
    "reduceOptions": {
      "calcs": ["lastNotNull"],
      "fields": "",
      "values": false
    },
    "displayLabels": [],
    "text": {},
    "emptyDataMode": "zero",
    "layout": 0,
    "heightCalc": "auto",
    "borderWidth": 0,
    "borderRadius": 0
  }
}
```

Key `options.pieType`: `"pie"`, `"donut"`, `"half-pie"`.
Key `options.legend.displayMode`: `"list"`, `"table"`, `"hidden"`.
Key `options.emptyDataMode`: `"zero"`, `"hide"`, `"nothing"`.

## Table (`table`)

```json
{
  "type": "table",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "showHeader": true,
    "sortBy": [],
    "cellHeight": "sm",
    "footer": {
      "countRows": false,
      "fields": "",
      "reducer": ["sum"],
      "show": false
    }
  }
}
```

Table column customization lives inside `fieldConfig.defaults.custom` per field:

```json
{
  "fieldConfig": {
    "defaults": {
      "custom": {
        "align": "auto",
        "cellOptions": {
          "type": "auto",
          "inspect": false
        },
        "filterable": true,
        "width": 150,
        "inspect": false
      }
    }
  }
}
```

Key `custom.align`: `"auto"`, `"left"`, `"center"`, `"right"`.
Key `custom.cellOptions.type`: `"auto"`, `"color-background"`, `"color-text"`, `"image"`, `"link"`, `"json-view"`, `"sparkline"`, `"data-bar"`, `"icon"`, `"button"`, `"code"`, `"html"`, `"uri"`, `"loading-spinner"`.
Key `options.cellHeight`: `"sm"`, `"md"`, `"lg"`.

## Logs (`logs`)

```json
{
  "type": "logs",
  "datasource": { "type": "loki", "uid": "loki" },
  "targets": [
    { "expr": "{job=\"api\"}", "refId": "A" }
  ],
  "options": {
    "showLabels": false,
    "showTime": true,
    "wrapLogMessage": true,
    "prettifyLogMessage": true,
    "enableLogDetails": true,
    "dedupStrategy": "none",
    "sortOrder": "Descending",
    "columns": [
      { "id": "severity_text", "label": "Level", "width": "auto", "visible": true, "filterable": true },
      { "id": "message", "label": "Message", "width": "auto", "visible": true, "filterable": true }
    ]
  }
}
```

Key `options.dedupStrategy`: `"none"`, `"exact"`, `"numbers"`, `"signature"`.
Key `options.sortOrder`: `"Descending"`, `"Ascending"`.

## State Timeline (`statetimeline`)

```json
{
  "type": "statetimeline",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "mergeValues": true,
    "showValue": "always",
    "rowHeight": 0.9,
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": []
    },
    "tooltip": {
      "mode": "single",
      "sort": "none"
    },
    "alignValue": "center",
    "colorMode": "thresholds"
  }
}
```

Key `options.showValue`: `"always"`, `"never"`.
Key `options.colorMode`: `"thresholds"`, `"palette-classic"`, `"continuous-GrYlRd"`, etc.
Requires query results with time range columns (start/end) and a state field. For Prometheus, use `changes()` or `resets()` to detect state transitions.

## Status History (`statushistory`)

Similar to state timeline but optimized for discrete status change events rather than continuous state bands.

```json
{
  "type": "statushistory",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "mergeValues": true,
    "showValue": "always",
    "rowHeight": 0.8,
    "legend": {
      "displayMode": "list",
      "placement": "bottom"
    },
    "tooltip": {
      "mode": "single"
    },
    "graphMode": "area",
    "colorMode": "thresholds"
  }
}
```

Use status history when events are point-in-time changes rather than continuous states. The difference from state timeline is primarily in how gaps between events are rendered -- status history shows no-state gaps explicitly.

## Candlestick (`candlestick`)

For Open-High-Low-Close style data. Commonly used for financial data but applicable to any min/max/current/previous pattern.

```json
{
  "type": "candlestick",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": []
    },
    "tooltip": {
      "mode": "single"
    },
    "candleStyle": {
      "color": {
        "up": "#56a64b",
        "down": "#e24d42",
        "unchanged": "gray"
      },
      "width": 0.8,
      "wickWidth": 0.6
    },
    "volumeStyle": {
      "enabled": false,
      "fillOpacity": 60
    },
    "axisPlacement": "auto",
    "showAxis": true
  }
}
```

Data requirements: queries must return open, high, low, close fields per time period. This is not a standard Prometheus output shape; typically requires transformations or a datasource that natively produces OHLC data.

## Trend (`trend`)

Shows a single value with a directional trend indicator (arrow + sparkline). More compact than a stat panel when only direction matters.

```json
{
  "type": "trend",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "showTrend": {
      "mode": "line"
    },
    "trendOptions": {
      "show": true,
      "lineWidth": 1,
      "fillOpacity": 20,
      "yMax": null,
      "yMin": null
    },
    "orientation": "auto",
    "reduceOptions": {
      "calcs": ["lastNotNull"],
      "fields": "",
      "values": false
    },
    "colorMode": "value",
    "graphMode": "area",
    "justification": "auto",
    "textMode": "auto",
    "wideLayout": true,
    "legendDisplayMode": "list",
    "legendPlacement": "bottom",
    "legendCalcs": ["mean", "max", "min"]
  }
}
```

## XY Chart (`xychart`)

Scatter plot or line chart where X is an arbitrary field (not necessarily time).

```json
{
  "type": "xychart",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "legend": {
      "displayMode": "list",
      "placement": "bottom",
      "calcs": []
    },
    "tooltip": {
      "mode": "single",
      "sort": "none"
    },
    "scatter": {
      "size": 3,
      "symbol": "circle"
    },
    "resultMode": "scatter",
    "easing": "easeInOutQuad",
    "xAxis": {
      "show": true,
      "axisPlacement": "auto",
      "axisColorMode": "text",
      "axisLabel": "",
      "axisSoftMin": null,
      "axisSoftMax": null,
      "scaleDistribution": {
        "type": "linear",
        "log": 2,
        "linearThreshold": null
      }
    },
    "yAxis": {
      "show": true,
      "axisPlacement": "auto",
      "axisColorMode": "text",
      "axisLabel": "",
      "axisSoftMin": null,
      "axisSoftMax": null,
      "scaleDistribution": {
        "type": "linear",
        "log": 2,
        "linearThreshold": null
      }
    }
  }
}
```

Key `options.resultMode`: `"scatter"`, `"lines"`.
Key `options.scatter.symbol`: `"circle"`, `"square"`, `"diamond"`, `"triangle"`, `"cross"`, "+".

## Node Graph (`nodegraph`)

Visualize nodes and edges as a force-directed graph. Useful for service dependency maps, call graphs, and topology views.

```json
{
  "type": "nodegraph",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "nodes": [
      {
        "id": "api-server",
        "title": "API Server",
        "mainStatUnit": "short",
        "subTitle": ""
      }
    ],
    "edges": [
      {
        "source": "api-server",
        "target": "database",
        "mainStatUnit": "reqps"
      }
    ],
    "layout": {
      "direction": "LR"
    },
    "options": {
      "showEdges": true,
      "showNodes": true,
      "enableHighlights": true,
      "nodeGravity": -100,
      "edgeLength": 200
    }
  }
}
```

Node graph requires structured node/edge data. Queries must return frames with `id`, `title`, and optionally `mainStat`, `subTitle`, `color` for nodes, and `source`, `target`, `mainStat` for edges. This is typically produced by specialized datasources or transformations, not raw Prometheus queries.

## Traces (`traces`)

Distributed tracing viewer showing span waterfall and duration breakdown.

```json
{
  "type": "traces",
  "datasource": { "type": "jaeger", "uid": "jaeger" },
  "targets": [
    { "queryType": "search", "refId": "A", "services": ["api"], "tags": [], "limit": 20 }
  ],
  "options": {
    "spanName": {
      "width": 250,
      "columnFilter": false
    },
    "duration": {
      "width": 150,
      "columnFilter": false
    },
    "service": {
      "width": 150,
      "columnFilter": false
    },
    "traceId": {
      "width": 180,
      "columnFilter": false
    },
    "logMessage": {
      "width": 300,
      "columnFilter": false
    },
    "tags": {
      "width": 200,
      "columnFilter": false
    },
    "filterByTraceId": "",
    "filterBySpanId": "",
    "collapseTraces": false,
    "showErrorsOnly": false,
    "sortOrder": "descending-longest-first"
  }
}
```

Requires a tracing datasource (Jaeger, Tempo, Zipkin, X-Ray). Query structure depends on the specific tracing backend.

## Flame Graph (`flamegraph`)

Hierarchical flame graph for profiling data. Shows call stacks with width proportional to resource consumption.

```json
{
  "type": "flamegraph",
  "datasource": { "type": "pyroscope", "uid": "pyroscope" },
  "targets": [
    { "queryType": "both", "refId": "A", "profileTypeId": "cpu:cpu:nanoseconds:samples", "labelSelector": "{job=\"api\"}" }
  ],
  "options": {
    "showSummary": true,
    "type": "flame",
    "displayMode": "normal",
    "minValue": null,
    "maxValue": null,
    "colorScheme": {
      "mode": "palette-classic",
      "spec": {}
    },
    "showSelfCounts": false,
    "topItems": 15,
    "sortDirection": "descending"
  }
}
```

Key `options.type`: `"flame"`, `"icicle"`, `"tree"`, `"table"`, `"both"`.
Key `options.displayMode`: `"normal"`, `"diff"`.
Requires a continuous profiling datasource (Pyroscope, Parca, Grafana Phlare).

## Canvas (`canvas`)

Free-form canvas with positioned elements. Use for custom dashboards that do not fit the standard grid layout.

```json
{
  "type": "canvas",
  "elements": [
    {
      "type": "text",
      "config": {
        "content": "Service Overview",
        "fontSize": "h2",
        "color": "blue"
      },
      "gridPos": { "x": 0, "y": 0, "w": 12, "h": 4 }
    },
    {
      "type": "row",
      "config": {},
      "gridPos": { "x": 0, "y": 4, "w": 24, "h": 1 }
    },
    {
      "type": "metricText",
      "config": {
        "layout": { "position": "inline" },
        "options": {
          "textMode": "auto",
          "colorMode": "value",
          "graphMode": "area"
        },
        "query": {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "targets": [{ "expr": "up{job=\"api\"}", "refId": "A" }]
        }
      },
      "gridPos": { "x": 0, "y": 5, "w": 6, "h": 4 }
    }
  ],
  "options": {}
}
```

Canvas element types: `"text"`, `"row"`, `"image"`, `"ellipse"`, `"rect"`, `"arc"`, `"line"`, `"path"`, `"iframe"`, `"metricText"`, `"nodeGraph"`, `"heatmap"`, `"timeseries"`, `"stat"`, `"bargauge"`, `"table"`, `"logs"`, `"alertGraph"`, `"alertList"`, `"annolist"`, `"news"`, `"debug"`. Each element has its own `config` schema matching the standalone panel's options.

## Geo Map (`geomap`)

Geographic visualization using latitude/longitude coordinates.

```json
{
  "type": "geomap",
  "fieldConfig": { "defaults": {}, "overrides": [] },
  "options": {
    "basemap": {
      "type": "osm-standard"
    },
    "controls": {
      "mouseWheelZoom": true,
      "showAttribution": true,
      "showDebug": false,
      "showMeasure": false,
      "showScale": true,
      "showZoom": true
    },
    "layers": [
      {
        "location": {
          "mode": "auto",
          "latitude": "lat",
          "longitude": "lon"
        },
        "tooltip": true,
        "trace": true,
        "config": {
          "showLegend": true,
          "circleRadius": {
            "fixed": 5,
            "min": 2,
            "max": 20,
            "field": "Value #A"
          },
          "mapBounds": {
            "west": -130,
            "east": -60,
            "south": 20,
            "north": 50
          }
        }
      }
    ],
    "view": {
      "id": "zoom",
      "lat": 40,
      "lon": -95,
      "zoom": 3,
      "pitch": 0,
      "bearing": 0
    }
  }
}
```

Key `options.basemap.type`: `"osm-standard"`, `"osm-dark"`, `"carto-db-positron"`, `"carto-db-dark-matter"`, `"esri-world-imagery"`, `"esri-standard"`, `"esri-dark-gray"`, `"esri-topographic"`, `"here-container"`, `"here-nav"`, `"here-basic"`, `"here-satellite"`, `"maptiler-basic"`, `"maptiler-hybrid"`, `"maptiler-topo"`, `"maptiler-toner"`, `"maptiler-backdrop"`, `"maptiler-satellite"`, `"maptiler-terrain"`, `"maptiler-streets"`, `"custom"`.
Queries must return `lat` and `lon` fields (or configure `location.latitude`/`location.longitude` mapping).

## Dashboard List (`dashboardlist`)

Shows links to other dashboards, filtered by tag, folder, or search term.

```json
{
  "type": "dashboardlist",
  "targets": [{ "refId": "dashboard-list-data" }],
  "options": {
    "headings": { "lastViewedTime": "Last Viewed Time", "sort": "Sort" },
    "showHeadings": true,
    "showRecentlyViewed": false,
    "showSearch": true,
    "showStarred": false,
    "skipRecentViews": false,
    "folderId": 0,
    "maxPerRow": 3,
    "query": "",
    "tags": [],
    "sortDirection": 1,
    "sortField": 0
  }
}
```

Key `options.sortField`: `0` (Alpha), `1` (Last Viewed), `2` (Last Updated).
Key `options.sortDirection`: `1` (ascending), `2` (descending).

## Alert List (`alertlist`)

Shows active alert instances with state, labels, and duration.

```json
{
  "type": "alertlist",
  "targets": [{ "refId": "alert-list-data" }],
  "options": {
    "alertInstanceLabelMatcher": "",
    "alertName": "",
    "commonLabels": false,
    "dashboardAlerts": true,
    "groupMode": "default",
    "groupBy": ["alertname", "instance"],
    "maxItems": 100,
    "showAnnotations": true,
    "sortOrder": 1,
    "stateFilter": {
      "firing": true,
      "noData": true,
      "pending": true,
      "error": true,
      "normal": false
    }
  }
}
```

Key `options.stateFilter.*`: boolean toggles for which alert states to display.
Key `options.sortOrder`: `1` (ascending), `2` (descending).
Key `options.groupMode`: `"default"`, `"groupBy"`, `"groupBySort"`.
Key `options.dashboardAlerts`: `true` (Grafana-managed alerts), `false` (external/Prometheus alerts).

## Annotations List (`annotationslist`)

Shows annotation events on a timeline with details.

```json
{
  "type": "annotationslist",
  "targets": [{ "refId": "annotation-list-data" }],
  "options": {
    "limit": 10,
    "tags": [],
    "onlyFromThisDashboard": false,
    "onlyFromThisOrg": true,
    "typeFilter": {
      "annotation": true,
      "alert": true
    }
  }
}
```

## Text / News (`text`)

Static text content for documentation, instructions, or context panels. Supports Markdown and HTML.

```json
{
  "type": "text",
  "options": {
    "mode": "markdown",
    "content": "## Service Overview\n\n**Environment:** ${env}\n\n> [!NOTE]\n> This dashboard follows the RED method.\n\n### Quick Links\n\n- [Runbook](https://wiki.example.com/runbooks/api)\n- [SLA Definition](https://wiki.example.com/sla/api)"
  }
}
```

Key `options.mode`: `"markdown"`, `"html"`, `"text"`.
Markdown supports headings, bold, italic, links, lists, blockquotes, code blocks, tables, images, horizontal rules, and GitHub-style alert syntax (`> [!NOTE]`, `> [!WARNING]`, etc.).

HTML mode allows arbitrary HTML/CSS but may be restricted by Grafana's Content Security Policy settings.

## Panel Grid Position Reference

All panels use `gridPos` to define position and size within the 24-column grid system:

```json
{
  "gridPos": {
    "h": 8,
    "w": 12,
    "x": 0,
    "y": 0
  }
}
```

| Field | Description |
| --- | --- |
| `h` | Height in grid units (each unit ~30px) |
| `w` | Width in grid units (max 24) |
| `x` | Horizontal offset from left edge (0-based) |
| `y` | Vertical offset from top edge (0-based) |

Common sizes:

- Full-width row: `{ "w": 24, "h": 8 }`
- Half-width: `{ "w": 12, "h": 8 }`
- Quarter-width (stat/gauge): `{ "w": 6, "h": 4 }`
- Third-width: `{ "w": 8, "h": 8 }`
- Small inline: `{ "w": 4, "h": 4 }`
