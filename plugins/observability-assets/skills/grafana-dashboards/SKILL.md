---
name: grafana-dashboards
description: >-
  Use this skill when the user asks to "create or review a Grafana dashboard", "edit dashboard JSON", "use a Grafana mixin", "stabilize dashboard uid", or needs guidance on Grafana dashboard asset workflows and panel structure.
---

# Grafana Dashboards

## Overview

Use this skill to author Grafana dashboards as version-controlled assets and keep dashboard identity stable across environments. The common case is one dashboard with a stable `uid`, one default time range no broader than the last 30 minutes, and one panel layout that answers a real operator question instead of becoming a generic metric scrapbook. When the team already generates dashboards from Jsonnet, Grafana mixin artifacts belong in the same review path rather than being treated as unrelated tooling.

## Use This Skill When

- You are creating or reviewing Grafana dashboard JSON.
- You need guidance on dashboard `uid`, panel organization, datasource references, or Grafana mixin structure.
- Do not use this skill when the main task is Prometheus rule evaluation guidance rather than dashboard shape or dashboard asset review.

## Common-Case Workflow

1. Start from the operator question the dashboard must answer.
2. Keep one stable dashboard identity with a deliberate `uid` and title.
3. Default the dashboard time range to the last 30 minutes or less, and widen it only when the operator question needs more history.
4. Group panels by one narrative flow such as saturation, errors, and latency.
5. If the team uses Grafana mixin or Jsonnet, keep generated dashboards and source mixin files aligned deliberately.

## Minimal Setup

Minimal dashboard JSON shape:

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

## First Runnable Commands or Code Shape

Start by validating the JSON structure before the file is treated as a Git-owned dashboard asset:

```bash
python -m json.tool grafana/dashboards/api-overview.json
```

Use when: the dashboard JSON was edited manually and you need the first fast syntax check.

## Ready-to-Adapt Templates

Simple dashboard shell — stable `uid`, clear title, and one time-picker baseline:

```json
{
  "uid": "api-overview",
  "title": "API Overview",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "30s",
  "time": {
    "from": "now-30m",
    "to": "now"
  },
  "templating": {
    "list": []
  },
  "panels": [
    {
      "id": 1,
      "title": "Request Rate",
      "type": "timeseries",
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "targets": [
        {
          "expr": "round(sum(rate(http_requests_total{job=\"api\"}[5m])), 0.001)",
          "legendFormat": "req/s",
          "refId": "A"
        }
      ],
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 0,
        "y": 0
      }
    }
  ]
}
```

Use when: you need a small but production-shaped dashboard asset to expand from Git.

Direct dashboard asset layout — keep the repository path explicit:

```text
grafana/
  dashboards/
    api-overview.json
```

Use when: the team keeps reviewed dashboard JSON directly in the repository rather than generating it from Jsonnet.

Grafana mixin layout — keep source Jsonnet and rendered output relationship obvious:

```text
grafana/
  mixin/
    dashboards.libsonnet
    config.libsonnet
  rendered/
    dashboards/
      api-overview.json
```

Use when: dashboards are generated from Grafana mixin or adjacent Jsonnet tooling and reviewers need to understand both source and rendered dashboard assets.

Minimal mixin source — keep the generated dashboard traceable back to one small Jsonnet entrypoint:

```jsonnet
local grafana = import 'grafonnet/grafana.libsonnet';

{
  'api-overview.json':
    grafana.dashboard.new('API Overview')
    + grafana.dashboard.withUid('api-overview')
    + grafana.dashboard.time.withFrom('now-30m')
    + grafana.dashboard.time.withTo('now')
    + grafana.dashboard.withPanel(
      grafana.timeseriesPanel.new('Request Rate')
      + grafana.timeseriesPanel.queryOptions.withTargets([
        {
          expr: 'round(sum(rate(http_requests_total{job="api"}[5m])), 0.001)',
          legendFormat: 'req/s',
          refId: 'A',
        },
      ])
    ),
}
```

Use when: the team already keeps dashboard source in Jsonnet or Grafana mixin form and needs one minimal source example instead of raw rendered JSON only.

Minimal render step — render mixin or Jsonnet source into a reviewed dashboard asset:

```bash
jsonnet -J vendor -m grafana/rendered/dashboards grafana/mixin/dashboards.libsonnet
```

Use when: reviewers need one concrete command that turns source mixin files into reviewed dashboard artifacts.

## Validate the Result

Validate the common case with these checks:

- dashboard JSON is syntactically valid
- the dashboard has a stable `uid` and explicit title
- the default time range stays within the last 30 minutes unless a wider window is explicitly justified
- datasource references are deliberate rather than copied from a random export
- panels are organized around an operator question instead of an arbitrary metric dump
- mixin or Jsonnet source stays traceable to the rendered dashboard assets when generation is part of the workflow

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| dashboard JSON structure, panel identity, or export cleanup after UI edits or rendering | `./references/dashboard-structure.md` |
| writing, rendering, or reviewing Grafana mixin and Jsonnet source files | `./references/grafana-mixin.md` |

## Invariants

- MUST keep dashboard identity stable with an explicit `uid`.
- MUST validate edited JSON before claiming the dashboard is ready.
- MUST keep the default query time range within 30 minutes unless a wider window is explicitly justified.
- SHOULD keep panels organized around one operator story rather than a random metric collection.
- MUST keep datasource references explicit.
- SHOULD keep Grafana mixin source and rendered outputs aligned when generation is part of the workflow.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| copying a UI export with unstable metadata and no cleanup | reviews become noisy and identity drifts | normalize the JSON and keep a stable `uid`, title, and panel structure |
| setting the default dashboard range broader than 30 minutes with no operator reason | live queries scan far more data than the common path needs | start with `now-30m` and widen only when the investigation needs more history |
| treating generated or exported dashboard JSON as authoritative without checking the source workflow behind it | reviewers lose track of where the asset really comes from | keep direct JSON and mixin-generated workflows explicit and review the right source of truth |
| mixing unrelated panels into one dashboard | operators cannot read the story quickly | group panels by one question such as traffic, latency, and errors |
| leaving datasource references implicit or environment-specific without review | dashboards break when moved between environments | make datasource references explicit in the dashboard asset itself |

## Scope Boundaries

- Activate this skill for:
  - Grafana dashboard JSON authoring and review
  - Grafana mixin and Jsonnet-oriented dashboard generation
  - dashboard identity and panel organization
- Do not use this skill as the primary source for:
  - Prometheus rule files and rule evaluation guidance
  - application instrumentation choices
  - generic UI styling choices outside observability dashboards
