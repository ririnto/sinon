---
title: Grafana Mixin Reference
description: >-
  Reference for Grafana mixin and Jsonnet-based dashboard workflows, including source layout, rendered dashboard outputs, and review boundaries.
---

Use this reference when the team already uses Grafana mixin or Jsonnet and the blocker is understanding how source files and rendered dashboard assets should relate to each other.

## Source vs Rendered Assets

- keep Jsonnet or mixin source files in one obvious directory such as `grafana/mixin/`
- keep rendered dashboards in predictable output directories
- review source and rendered assets together when a change affects operator-visible behavior

## Source Review Focus

Review the mixin source itself when the normal dashboard JSON review is no longer enough.

- check that stable identifiers such as `uid`, folder assumptions, and tags are set in the Jsonnet source rather than patched only in rendered output
- check that the default time range policy still starts at `now-30m` or less unless the source explicitly justifies something wider
- check that rounded display-oriented PromQL stays in the source templates instead of being hand-edited only in rendered JSON

Example source snippet to inspect:

```jsonnet
{
  'api-overview.json':
    grafana.dashboard.new('API Overview')
    + grafana.dashboard.withUid('api-overview')
    + grafana.dashboard.time.withFrom('now-30m')
    + grafana.dashboard.withPanel(
      grafana.timeseriesPanel.new('Request Rate')
      + grafana.timeseriesPanel.queryOptions.withTargets([
        {
          expr: 'round(sum(rate(http_requests_total{job="api"}[5m])), 0.001)',
          refId: 'A',
        },
      ])
    ),
}
```

Use this when the blocker is deciding whether an operator-facing rule such as time range or rounding lives in source Jsonnet or was applied later only in rendered JSON.

## Render Boundary Checks

Use this reference when the blocker is the handoff between source and rendered assets.

- render commands should target the same tree that the documented rendered-asset layout expects
- generated dashboards should land under `grafana/rendered/dashboards/`

Example source-to-rendered contract:

```text
grafana/mixin/dashboards.libsonnet
  -> jsonnet render
  -> grafana/rendered/dashboards/api-overview.json
  -> reviewed rendered dashboard asset
```

Example review question:

> Does the documented render command write to `grafana/rendered/...`, and do all rendered-asset examples point at that same tree?

## Diff Review Heuristics

When a mixin change is reviewed, compare source and rendered diffs together.

- if only rendered JSON changed, verify whether the source Jsonnet change was accidentally omitted
- if only source changed, verify whether the rendered artifacts are intentionally deferred or were forgotten
- if both changed, verify that the rendered diff actually reflects the source-level intent rather than unrelated churn

## Review Questions

- does the rendered dashboard keep the intended stable `uid`
- can a reviewer trace the rendered artifact back to the source mixin change without guesswork

## Common Mistakes

- reviewing only rendered JSON and never checking the source Jsonnet change
- changing source mixin layout without updating the rendered output path contract
- generating rendered dashboard files with unstable names or identities that break review continuity
