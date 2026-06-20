---
description: >-
  Open this for generated dashboards, Jsonnet source review, and source-vs-rendered handoff blockers.
---

# Grafana Mixin Reference

Open this reference for Grafana mixin or Jsonnet source.
Open it to trace source files to rendered dashboard assets.

## Source vs Rendered Assets

- keep Jsonnet or mixin source files in one obvious directory such as `grafana/mixin/`
- keep rendered dashboards in predictable output directories
- review source and rendered assets together for operator-visible changes

## Source Review Focus

Review the mixin source itself when the normal dashboard JSON review is no longer enough.

- check Jsonnet source for stable identifiers
- include `uid`, folder assumptions, and tags in that source review
- check that the default time range policy starts at `now-30m` or less
- allow wider ranges only when the source explains why
- set datasource configuration explicitly in source
- do not inherit datasource defaults from an environment default
- keep rounded display-oriented PromQL in source templates
- do not hand-edit rounding only in rendered JSON

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

Decide whether an operator-facing rule belongs in source Jsonnet.
Examples include time ranges and rounding.
Treat source templates as trusted, reviewed inputs only.
Review rendered output before sharing it.
Jsonnet can inline imported local file contents into generated artifacts.

## Render Boundary Checks

- point render commands at the documented rendered-asset tree
- write generated dashboards under `grafana/rendered/dashboards/`

Example source-to-rendered contract:

```text
grafana/mixin/dashboards.libsonnet
  -> jsonnet render
  -> grafana/rendered/dashboards/api-overview.json
  -> reviewed rendered dashboard asset
```

Example review question:

> Does the documented render command write to `grafana/rendered/...`?
> Do all rendered-asset examples point at that same tree?

## Layout Patterns

Grafana mixin layout: keep source Jsonnet and rendered output relationship obvious:

```text
grafana/
  mixin/
    dashboards.libsonnet
    config.libsonnet
    mixin.libsonnet
    render-alerts.jsonnet
    render-dashboards.jsonnet
  rendered/
    dashboards/
      api-overview.json
    prometheus/
      alerts.yaml
```

Use when: a team generates dashboards from Grafana mixin or adjacent Jsonnet tooling.
Give reviewers one path from source to rendered asset.

## Minimal Mixin Configuration

Start with this local mixin shape before adding a larger upstream mixin dependency.
Provide the usual mixin fields: `_config`, `grafanaDashboards`, `prometheusAlerts`, and `prometheusRules`.
Keep `_config` hidden with `+::`.
The renderer can use local defaults without writing `_config` to output.

`grafana/mixin/config.libsonnet`:

```jsonnet
{
  _config+:: {
    component: 'api',
    dashboardTags: ['api', 'mixin'],
    defaultTimeRange: 'now-30m',
    prometheusDatasource: {
      type: 'prometheus',
      uid: 'prometheus',
    },
    selector: 'job="api"',
  },
}
```

`grafana/mixin/dashboards.libsonnet`:

```jsonnet
local config = import 'config.libsonnet';
local cfg = config._config;
local requestRateExpr = 'round(sum(rate(http_requests_total{' + cfg.selector + '}[5m])), 0.001)';

{
  'api-overview.json': {
    uid: 'api-overview',
    title: 'API Overview',
    tags: cfg.dashboardTags,
    time: {
      from: cfg.defaultTimeRange,
      to: 'now',
    },
    panels: [
      {
        id: 1,
        title: 'Request Rate',
        type: 'timeseries',
        datasource: cfg.prometheusDatasource,
        targets: [
          {
            expr: requestRateExpr,
            legendFormat: 'req/s',
            refId: 'A',
          },
        ],
      },
    ],
  },
}
```

`grafana/mixin/mixin.libsonnet`:

```jsonnet
local config = import 'config.libsonnet';
local dashboards = import 'dashboards.libsonnet';

config + {
  grafanaDashboards+:: dashboards,
  prometheusAlerts+:: [],
  prometheusRules+:: [],
}
```

`grafana/mixin/render-dashboards.jsonnet`:

```jsonnet
(import 'mixin.libsonnet').grafanaDashboards
```

Use `jb init` once for a Jsonnet tree.
Run `jb install` so jsonnet-bundler updates `jsonnetfile.json` when adding external mixins or Grafonnet.
Install Grafonnet only when the source imports Grafonnet:

```sh
jb install github.com/grafana/grafonnet/gen/grafonnet-latest@main
```

Do not hand-maintain dependency lock data when jsonnet-bundler already owns it.

Minimal render step: render mixin or Jsonnet source into a reviewed dashboard asset:

```sh
mkdir -p grafana/rendered/dashboards
jsonnet -J vendor -m grafana/rendered/dashboards grafana/mixin/render-dashboards.jsonnet
```

Render Prometheus fields only when the mixin owns those generated assets.

`grafana/mixin/render-alerts.jsonnet`:

```jsonnet
std.manifestYamlDoc((import 'mixin.libsonnet').prometheusAlerts)
```

Render the entrypoint to the documented output path:

```sh
mkdir -p grafana/rendered/prometheus
jsonnet -J vendor -S grafana/mixin/render-alerts.jsonnet > grafana/rendered/prometheus/alerts.yaml
```

Use when: reviewers need one command that turns source mixin files into reviewed dashboard artifacts.
Requires the `jsonnet` CLI.
Keep `-J vendor` only when the source imports vendored libraries from that path.
Keep the import search path aligned with the Jsonnet tree you are rendering.
Run it only on trusted Jsonnet or mixin sources, or inside an isolated workspace.
Jsonnet can render imported local file contents into output artifacts.

## Diff Review Heuristics

When a mixin change is reviewed, compare source and rendered diffs together.

- if only rendered JSON changed, verify whether the source Jsonnet change was accidentally omitted
- if only source changed, verify whether the rendered artifacts are intentionally deferred or were forgotten
- if both changed, verify that the rendered diff reflects the source-level intent rather than unrelated churn

## Review Questions

- does the rendered dashboard keep the intended stable `uid`
- can a reviewer trace the rendered artifact back to the source mixin change without guesswork

## Common Mistakes

- reviewing only rendered JSON and never checking the source Jsonnet change
- changing source mixin layout without updating the rendered output path contract
- generating rendered dashboard files with unstable names or identities that break review continuity
