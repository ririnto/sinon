# Dashboard Ownership and Layout

## JSON Model Boundary

Keep dashboard authoring and dashboard delivery separated even when both are reviewed together:

```text
reviewed source of truth: grafana/dashboards/api-overview.json
adjacent but separate concern: provisioning file that points at this dashboard
```

Dashboard authoring belongs here.
Provisioning configuration and rollout concerns stay in the adjacent delivery domain.
Read-only API inspection and local authoring do not authorize imports, API writes, or publication to a Grafana instance.
Obtain explicit authorization for the target before changing live dashboards.

Direct dashboard asset layout -- keep the repository path explicit:

```text
grafana/
  dashboards/
    api-overview.json
```

Use when: the team keeps reviewed dashboard JSON directly in the repository rather than generating it from Jsonnet.
For mixin configuration, Jsonnet source patterns, and source-vs-rendered handoff, see [`./grafana-mixin.md`](./grafana-mixin.md).
For export cleanup decisions and ownership boundaries after UI edits or rendering, see [`./structure.md`](./structure.md).
