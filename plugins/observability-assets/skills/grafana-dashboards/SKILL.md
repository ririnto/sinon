---
name: grafana-dashboards
description: >-
  Use this skill when the user asks to "create or review a Grafana dashboard", "edit dashboard JSON", "use a Grafana mixin", "stabilize dashboard uid", or needs guidance on Grafana dashboard asset workflows and panel structure.
metadata:
  title: "Grafana Dashboards"
  official_project_url: "https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/"
  reference_doc_urls:
    - "https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/"
    - "https://grafana.com/docs/grafana/latest/dashboards/variables/"
    - "https://grafana.com/docs/grafana/latest/panels-visualizations/query-transform-data/transform-data/"
    - "https://grafana.com/docs/grafana/latest/dashboards/json-model/"
  version: "latest"
---

# Grafana Dashboards

## Overview

Use this skill to author and review Grafana dashboards as version-controlled assets while keeping dashboard identity stable across environments. The common case is one dashboard with a stable `uid`, a deliberate title, explicit datasource handling, a default time range no broader than the last 30 minutes, and a panel layout that answers a real operator question instead of becoming a generic metric scrapbook. This skill owns the ordinary path for dashboard structure, queries, variables, transformations, field configuration, thresholds, legends, units, layout, repeat behavior, links, annotations, and the dashboard JSON model boundary; it does not require a sibling skill just to complete normal dashboard authoring or review.

## Use This Skill When

- You are creating or reviewing Grafana dashboard JSON.
- You need guidance on dashboard `uid`, panel organization, datasource references, variables, transformations, field config, thresholds, legends, units, links, annotations, or Grafana mixin structure.
- Do not use this skill when the main task is Prometheus rule evaluation guidance rather than dashboard shape or dashboard asset review.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- dashboard structure, panel composition, and operator-facing review goals
- query placement and datasource assumptions needed to keep the dashboard asset valid
- variables, repeated panels, and other templating needed for ordinary dashboard use
- transformations, field config, units, thresholds, and legends that shape the data already returned by the query
- links, annotations, default time range, and layout choices that affect how the dashboard is read
- JSON model ownership, including when a dashboard JSON file is the Git-owned source of truth and when provisioning or generated workflows are adjacent rather than primary

## Version-Sensitive Features

- Classic dashboard JSON is the long-standing baseline used by the examples in this skill.
- If you are working with Grafana resource-style dashboard schemas, treat them as a newer Grafana capability rather than the baseline assumed by this skill.
- If you are considering JSON schema v2 features such as newer layout models, treat them as version-sensitive public-preview capability rather than a universal baseline.

## Common-Case Workflow

1. Start from the operator question the dashboard must answer.
2. Keep one stable dashboard identity with a deliberate `uid` and title.
3. Choose the datasource and queries deliberately, then shape the dashboard around the returned data rather than copying an arbitrary UI export.
4. Add variables, repeated panels, transformations, field config, units, thresholds, and legends only when they improve operator readability for the main question.
5. Keep links, annotations, and layout aligned to one narrative flow such as saturation, errors, and latency.
6. Default the dashboard time range to the last 30 minutes or less, and widen it only when the operator question needs more history.
7. If the team uses Grafana mixin or Jsonnet, keep generated dashboards and source mixin files aligned deliberately.

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
python3 -m json.tool grafana/dashboards/api-overview.json
```

Use when: the dashboard JSON was edited manually and you need the first fast syntax check.
Run this from the repository root shown in the path examples, or replace the path with the dashboard file location in your tree.

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

Variable and repeat pattern — merge this fragment into the earlier full dashboard shell to drive repeated panels from one bounded query variable:

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
        "query": "label_values(up{job=\"api\"}, instance)"
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
      "repeat": "instance"
    }
  ]
}
```

Use when: one panel shape should repeat across a bounded variable set without copying panel JSON by hand. This is a fragment to merge into the earlier full dashboard shell, not a standalone importable dashboard.

Transformations and field config — merge this fragment into the target panel when the query already returns the right signal:

```json
{
  "fieldConfig": {
    "defaults": {
      "unit": "reqps",
      "thresholds": {
        "mode": "absolute",
        "steps": [
          { "color": "green", "value": null },
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
    {
      "id": "organize",
      "options": {}
    }
  ]
}
```

Use when: the query already returns the right signal and the panel only needs clearer units, thresholds, legend behavior, or column organization. This is a fragment to merge into an existing panel object, not a standalone dashboard asset or dashboard-root object.

Links and annotations — merge this fragment into the dashboard shell to attach one drilldown path and one event context source directly to the dashboard:

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

Use when: operators need one direct drilldown and one timeline context source while reading the dashboard. This is a fragment to merge into the earlier full dashboard shell, not a complete dashboard by itself.

JSON model boundary — keep dashboard authoring and dashboard delivery separated even when both are reviewed together:

```text
reviewed source of truth: grafana/dashboards/api-overview.json
adjacent but separate concern: provisioning file that points at this dashboard
```

Use when: you need to show that dashboard JSON ownership belongs here, while provisioning configuration and rollout concerns stay in the adjacent delivery domain.

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
{
  'api-overview.json': {
    uid: 'api-overview',
    title: 'API Overview',
    time: {
      from: 'now-30m',
      to: 'now',
    },
    panels: [
      {
        id: 1,
        title: 'Request Rate',
        type: 'timeseries',
        datasource: {
          type: 'prometheus',
          uid: 'prometheus',
        },
        targets: [
          {
            expr: 'round(sum(rate(http_requests_total{job="api"}[5m])), 0.001)',
            legendFormat: 'req/s',
            refId: 'A',
          },
        ],
      },
    ],
  },
}
```

Use when: the team already keeps dashboard source in Jsonnet or Grafana mixin form and needs one minimal source example instead of raw rendered JSON only.
Keep Jsonnet or mixin sources trusted and reviewed; repository presence alone is not a trust boundary, and untrusted templates can import local files and surface their contents in rendered output.

Minimal render step — render mixin or Jsonnet source into a reviewed dashboard asset:

```bash
jsonnet -m grafana/rendered/dashboards grafana/mixin/dashboards.libsonnet
```

Use when: reviewers need one concrete command that turns source mixin files into reviewed dashboard artifacts.
This example assumes the `jsonnet` CLI is installed. Add `-J vendor` only when the source actually imports vendored libraries from that path, and keep the import search path aligned with the Jsonnet tree you are rendering.
Run it only on trusted Jsonnet or mixin sources, or inside an isolated workspace, because imported local file contents can be rendered into output artifacts.

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
- JSON model ownership is explicit: either the dashboard JSON is reviewed directly, or a generated workflow is clearly documented
- mixin or Jsonnet source stays traceable to the rendered dashboard assets when generation is part of the workflow

## Output contract

Return:

1. the recommended dashboard asset or review decision
2. the source-of-truth path or source-vs-rendered ownership decision
3. validation results for JSON structure, datasource explicitness, and layout/readability checks
4. any remaining blockers, risks, or follow-up review points

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| export cleanup, JSON ownership, or deciding what to normalize after UI edits or rendering | [`./references/dashboard-structure.md`](./references/dashboard-structure.md) |
| generated dashboards, Jsonnet source review, or source-vs-rendered handoff | [`./references/grafana-mixin.md`](./references/grafana-mixin.md) |

## Invariants

- MUST keep dashboard identity stable with an explicit `uid`.
- MUST validate edited JSON before claiming the dashboard is ready.
- MUST keep the default dashboard time range within 30 minutes unless a wider window is explicitly justified.
- MUST keep ordinary dashboard authoring and review understandable from this file alone.
- SHOULD keep panels organized around one operator story rather than a random metric collection.
- MUST keep datasource references explicit.
- SHOULD use variables, repeat, transformations, field config, thresholds, legends, links, and annotations only when they serve the operator question directly.
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
  - dashboard identity, panel organization, variables, transformations, field config, thresholds, legends, units, links, annotations, and JSON-model-aware review
- Do not use this skill as the primary source for:
  - Prometheus rule files and rule evaluation guidance
  - deep PromQL language design beyond what is needed to read or place a dashboard query
  - dashboard provisioning configuration files and environment rollout policy
  - application instrumentation choices
  - generic UI styling choices outside observability dashboards
