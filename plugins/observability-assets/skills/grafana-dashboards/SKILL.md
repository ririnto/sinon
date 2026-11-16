---
metadata:
  reference:
    Grafana release:
      version: Grafana 13.2.3
      url: https://github.com/grafana/grafana/releases/tag/v13.2.3
    Grafana releases:
      url: https://github.com/grafana/grafana/releases
    Grafana query variable reducer:
      version: Grafana 13.2.3
      url: https://raw.githubusercontent.com/grafana/grafana/v13.2.3/public/app/features/variables/query/reducer.ts
    Grafana variable docs:
      version: Grafana 13.2.3
      url:
        - https://grafana.com/docs/grafana/v13.2/dashboards/variables/variable-syntax/
        - https://grafana.com/docs/grafana/v13.2/datasources/prometheus/template-variables/
    Grafonnet:
      url: https://github.com/grafana/grafonnet
name: grafana-dashboards
description: >-
  Use for Grafana dashboard JSON, panel and query configuration, variables, field settings, stable identity, or Jsonnet and mixin generation.
---

# Grafana Dashboards

Author and review Grafana dashboards as version-controlled assets while keeping dashboard identity stable across environments.

## Official Baseline

- For new Grafana installations, check the official Grafana releases for the latest stable version compatible with the target deployment.
  Preserve the target instance's version and dashboard model when editing existing assets.
- The classic dashboard JSON model (`schemaVersion`, `panels`, `templating`) and the Grafana 13 V2 Resource model differ.
  Check the target instance before copying examples.
- Schema details in the references are authored summaries, not verbatim copies.

Select the supporting reference for the affected fields or generation path below.

## Task Focus

- Inspect the dashboard source, target Grafana model, and operator question before editing.
- Preserve the stable `uid`, title, and datasource contract unless the task requires a deliberate change.
- Shape affected panels around query results and the operator question, using the Panel Type Decision Guide when needed.
- Add variables, repeats, transformations, field settings, and navigation only when they improve the intended view.
- Choose a default time range that supports the operator question and fits the target's query cost and retention.
- Change Jsonnet or mixin source when it owns the dashboard, then review the affected rendered output.

Default to Grafana's V2 Resource model when the task is about Grafana 13 Observability as Code or the newer `/apis` dashboard flow.
Keep classic dashboard JSON for file provisioning, UI export cleanup, grafana.com dashboard sharing, or repositories that already store classic dashboard files.
When a classic example includes `schemaVersion`, treat it as an example/export value and preserve or regenerate it from the target Grafana version instead of asserting that one number is universally current.

## First Runnable Command

Validate edited JSON before treating it as a Git-owned asset, using the actual path:

```sh
uv run -m json.tool grafana/dashboards/api-overview.json
```

## Dashboard Examples

For a dashboard shell, repeated panels, links, annotations, or query options, read [dashboard examples](./references/dashboard-examples.md) when implementing those features.
Query `timeFrom` and `timeShift` are panel-level, not target-level; use PromQL `offset` for a per-query comparison.
Do not hand-author computed `intervalMs` and `maxDataPoints` export values.

## Panel Selection

When choosing a panel, read [panel selection](./references/panel-decision-guide.md) for operator questions and data shapes, and [panel types](./references/panel-types.md) for options and schemas.

## Variables, Transformations, and Field Config

Use query variables for dynamic lists, custom variables for small static enums, and textbox variables for ad-hoc input; sanitize textbox query values against injection.
Apply transformations in order; each receives the previous result.
Field config sets units, decimals, thresholds, mappings, and links: the first threshold step has `value: null`, percentage mode needs explicit `min` and `max`, overrides pair matchers with properties, and first matching value mapping wins.
For variable types, global variables, formats, and transformations, read [variables](./references/variables.md) when configuring them.
For units, overrides, mappings, and links, read [field config](./references/field-config.md) when configuring those fields.

## Frameworks and Time Controls

For resource views use USE (utilization, saturation, errors); for request services use RED (rate, errors, duration) or the four golden signals (latency, traffic, errors, saturation).
When applying these frameworks or configuring time and repeat controls, read [dashboard frameworks and time controls](./references/dashboard-layout.md).
Keep one repeat driver per panel or row; compose a repeated row with interpolated panels for two dimensions.

## JSON Model Boundary

Keep authoring separate from provisioning and rollout.
Read-only inspection and local authoring do not authorize imports, API writes, or publication; obtain explicit target authorization before changing live dashboards.
For direct-source layout, mixin generation, export cleanup, or UI-edit ownership, read [ownership and layout](./references/ownership-and-layout.md), [mixin](./references/grafana-mixin.md), and [structure](./references/structure.md) as applicable.

## Validate the Result

Check JSON syntax, stable `uid` and title, explicit datasource and source ownership, and a panel arrangement that answers the operator question.
Check query expressions, panel and variable types, repeated panels, transformations, fields, thresholds, legends, links, and annotations against that question.
Ensure the time range fits the question, query cost, and retention; overrides match the intended fields and mappings handle null, NaN, and empty values.
Run existing JSON or renderer checks, use query or rendering evidence when behavior changes, and state what remains unverified.
JSON parsing alone does not prove datasource access or correct rendering in the target Grafana instance.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. the recommended dashboard asset or review decision
2. the source-of-truth path or source-vs-rendered ownership decision
3. validation results for JSON structure, datasource explicitness, and layout/readability checks
4. any remaining blockers, risks, or follow-up review points

## Invariants

- MUST keep an explicit stable `uid`, validate edited JSON, and keep datasource references explicit.
- MUST NOT include unstable runtime-only fields in Git-owned JSON.
- SHOULD organize panels around one operator story, choose fitting panel types, and add variables, repeat, transformations, field config, thresholds, legends, links, or annotations only when useful to that question.
- SHOULD use USE, RED, or Four Golden Signals for service dashboards.

## Common Pitfalls

Normalize UI exports and keep stable identity and the correct source of truth.
Select the time range, panel type, and datasource for the operator question and target environment.
For multivalue PromQL variables use `=~"${var:regex}"` and a safe custom All value such as `.+` when needed.
Map null and NaN explicitly; percentage thresholds require field `min` and `max`.
Use `byRegexp` or `byFrameRefID` for query-derived fields whose generated names may change.

## Scope Boundaries

This skill covers Grafana dashboard assets, identity, layout, panel and query options, variables, transformations, field settings, and model-aware review.
It excludes Prometheus rule files, deep PromQL design, provisioning and rollout, instrumentation, generic UI styling, and Grafana alerting or contact routing.
