# Dashboard Examples and Query Options

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
