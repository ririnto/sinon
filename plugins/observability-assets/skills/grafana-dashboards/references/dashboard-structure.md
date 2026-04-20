---
title: Grafana Dashboard Structure Reference
description: "Open this when export cleanup, JSON ownership, or normalization after UI edits or rendering is the blocker."
---

Use this reference when the main blocker is understanding what to keep or remove in dashboard JSON after an export, a manual edit, or a Grafana mixin render.

## Keep Stable

- `uid`
- `title`
- panel `id` values within the dashboard
- datasource references that match the intended environment model
- the mapping back to the source mixin file when the dashboard is generated

## Export Cleanup Decisions

Use this section when the blocker is deciding what to normalize after export rather than how to validate JSON syntax.

- remove unstable runtime-only fields that create review noise without changing operator behavior
- keep stable identity fields even when a UI export tries to regenerate them
- normalize titles, panel ordering, and datasource references before treating the exported file as Git-owned

Broken — raw UI export with unstable fields:

```json
{
  "id": 42,
  "uid": "abc123xyz",
  "slug": "api-overview-v2",
  "timezone": "browser",
  "version": 15,
  "panels": [
    {
      "id": 1,
      "type": "timeseries"
    }
  ]
}
```

Better — cleaned, Git-owned JSON with stable identity:

```json
{
  "uid": "api-overview",
  "title": "API Overview",
  "schemaVersion": 39,
  "version": 1,
  "time": {
    "from": "now-30m",
    "to": "now"
  },
  "panels": [
    {
      "id": 1,
      "title": "Request Rate",
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      }
    }
  ]
}
```

Use this when the blocker is deciding what the cleaned, Git-owned JSON should still preserve after unstable export fields are removed.

## Rendered-vs-Edited Review

When a dashboard comes from Grafana mixin or another generator, review two distinct questions:

1. Is the rendered JSON internally stable and readable?
2. Should this cleanup happen in the source generator instead of the rendered artifact?

If the same cleanup would be needed after every render, move that decision back into the source template rather than patching generated JSON repeatedly.

Concrete review split:

```text
Rendered JSON only changed:
- check whether the source generator change was forgotten

Source Jsonnet only changed:
- check whether rendered artifacts were intentionally deferred
```

Use this reference for ownership boundaries, not for ordinary dashboard review. Normal decisions about variables, legends, thresholds, links, annotations, or panel readability stay in [`../SKILL.md`](../SKILL.md).
