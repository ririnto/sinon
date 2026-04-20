---
title: "Newer Grafana Provisioning Features"
description: "Open this when Git Sync, newer on-prem file provisioning, Grafana provisioning toggles, K8s resource shapes, or datasource provisioning reference is the blocker."
---

Use this reference when legacy provider YAML is not the whole story and the deployment is explicitly using newer Grafana observability-as-code features.

## Version Note

- Treat Git Sync, the newer on-prem file provisioning flow, and the related `provisioning` plus `kubernetesDashboards` toggles as newer Grafana features rather than a universal baseline.
- The legacy provider YAML path (`apiVersion: 1` with `providers` list) remains the stable, widely-supported baseline for all current Grafana versions.

## Review Focus

- verify the deployment actually supports the newer provisioning path being discussed
- verify the needed feature toggles are enabled
- verify the team really needs the newer flow rather than stable legacy file provisioning
- confirm the target Grafana version supports the chosen resource format

## Feature Toggles

Enable newer provisioning paths in `grafana.ini`:

```ini
[feature_toggles]
enable = provisioning,kubernetesDashboards
```

Toggle meanings:

| Toggle | Effect |
| --- | --- |
| `provisioning` | Enables the newer unified provisioning system (beyond legacy `apiVersion: 1` providers). Required for Git Sync and some cloud-managed provisioning flows. |
| `kubernetesDashboards` | Enables Kubernetes Custom Resource-based dashboard provisioning. Dashboards are defined as K8s resources rather than files on disk. |

## Kubernetes Dashboard Resource Shape

When `kubernetesDashboards` is enabled, dashboards can be defined as Kubernetes custom resources instead of filesystem-based JSON files.

Complete resource example:

```yaml
apiVersion: dashboard.grafana.app/v1beta1
kind: Dashboard
metadata:
  name: api-overview
  namespace: monitoring
  labels:
    team: platform
spec:
  # Folder placement for this dashboard inside Grafana.
  # Resolved by folder name or folderUid at sync time.
  folder: Operations
  # The dashboard definition. Same schema as the inner "dashboard" object
  # from the JSON wrapper -- no outer wrapper needed in K8s format.
  title: API Overview
  uid: k8s-api-overview
  timezone: browser
  schemaVersion: 38
  refresh: 30s
  tags:
    - generated
    - k8s-provisioned
  panels:
    - id: 1
      title: Request Rate
      type: timeseries
      gridPos:
        x: 0
        y: 0
        w: 12
        h: 8
      targets:
        - expr: sum(rate(http_requests_total[5m])) by (method)
          legendFormat: "{{method}}"
```

Key differences from legacy file provisioning:

| Aspect | Legacy (file) | K8s (CRD) |
| --- | --- | --- |
| Config location | Provider YAML + JSON files on disk | Kubernetes Custom Resources in cluster |
| Wrapper needed | Yes (`{ "dashboard": ..., "overwrite": true }`) | No (fields go directly into `.spec`) |
| ID management | Must set `id: null` manually | Handled by controller |
| Folder assignment | Provider-level `folder` or per-file `folderUid` | `.spec.folder` or `.spec.folderUid` on the resource |
| Update detection | Filesystem poll or watch | K8s watcher / reconciliation loop |
| Overwrite behavior | Explicit `overwrite: true` required | Always overwrites by default (controller manages state) |

Minimal K8s resource shape:

```yaml
apiVersion: dashboard.grafana.app/v1beta1
kind: Dashboard
metadata:
  name: my-dashboard
spec:
  title: My Dashboard
```

Use when: the blocker is understanding the minimum config shape that distinguishes the newer K8s provisioning path from legacy provider YAML.

## Datasource Provisioning Reference

Datasource provisioning is scope-adjacent to dashboard provisioning but is **not covered** by this skill. Use this section as a pointer to the correct adjacent domain.

What datasource provisioning covers:

- Defining Prometheus, Loki, Elasticsearch, MySQL, and other datasource connections as code
- Placing datasource YAML under `<grafana_home>/conf/provisioning/datasources/`
- Managing datasource credentials, default settings, and access controls declaratively

Example datasource provider shape (for reference only):

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
    jsonData:
      httpMethod: POST
      manageAlerts: true
      prometheusType: Prometheus
```

Key differences from dashboard provisioning:

| Aspect | Dashboard provisioning | Datasource provisioning |
| --- | --- | --- |
| Config directory | `provisioning/dashboards/` | `provisioning/datasources/` |
| Top-level key | `providers:` | `datasources:` |
| Source files | JSON with wrapper schema | Not applicable (YAML-only definitions) |
| Delete behavior | Controlled per-provider | Datasource deletion always allowed unless restricted by org policy |
| UI edit support | `allowUiUpdates` toggle | Datasource edits in UI persist independently of YAML |

When you need datasource provisioning details:

- Consult the official Grafana documentation for datasource provisioning configuration reference.
- Datasource YAML files use a different schema with different field names, defaults, and constraints than dashboard providers.
- This skill does not validate datasource configurations; switch to datasource-specific guidance for that domain.

Use when: the blocker is determining whether a question belongs to dashboard provisioning or the adjacent datasource provisioning domain.
