---
title: Grafana Mixin Reference
description: "Open this when generated dashboards, Jsonnet source review, or source-vs-rendered handoff is the blocker."
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
- check that datasource configuration is set explicitly in source rather than inherited implicitly from an environment default
- check that rounded display-oriented PromQL stays in the source templates instead of being hand-edited only in rendered JSON

Example source snippet to inspect:

```jsonnet
{
  'api-overview.json': {
    uid: 'api-overview',
    time: {
      from: 'now-30m',
    },
    panels: [
      {
        title: 'Request Rate',
        datasource: {
          type: 'prometheus',
          uid: 'prometheus',
        },
        targets: [
          {
            expr: 'round(sum(rate(http_requests_total{job="api"}[5m])), 0.001)',
            refId: 'A',
          },
        ],
      },
    ],
  },
}
```

Use this when the blocker is deciding whether an operator-facing rule such as time range or rounding lives in source Jsonnet or was applied later only in rendered JSON.
Treat source templates as trusted, reviewed inputs only, and review rendered output before sharing it because imported local file contents can be inlined into generated artifacts.

## Render Boundary Checks

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

## Layout Patterns

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
