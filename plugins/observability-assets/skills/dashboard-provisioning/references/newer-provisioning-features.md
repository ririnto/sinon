---
description: >-
  Open this when Grafana provisioning details are the blocker.
  Covers Git Sync, local-path provisioning, feature toggles, dashboard resource shapes, and datasource boundaries.
---

# Grafana Provisioning Feature Boundaries

Use this reference when legacy provider YAML is not the whole story.
Use it when the deployment explicitly uses Grafana observability-as-code features.

## Version Note

- Treat Git Sync, local-path provisioning, and dashboard resource files as observability-as-code features.
  - Do not present them as the legacy provider YAML baseline.
- In Grafana Cloud and Grafana v13 OSS/Enterprise, the `provisioning` feature toggle is enabled by default.
- For Grafana v12.4 OSS/Enterprise Git Sync, configure `repository_types` under `[provisioning]`.
  - This applies to pure Git, GitLab, Bitbucket, and local providers.
- The legacy provider YAML path remains the stable, widely-supported baseline for all current Grafana versions.
  - That path uses `apiVersion: 1` with a `providers` list.

## Review Focus

- verify the deployment actually supports the Git Sync or local-path provisioning flow being discussed
- verify the needed toggles or `[provisioning]` settings for the target Grafana version
- verify the team really needs the observability-as-code flow rather than stable legacy file provisioning
- confirm the target Grafana version supports the chosen resource format

## Feature Toggles

- For Grafana Cloud and Grafana v13 OSS/Enterprise, no manual `provisioning` toggle is required.
- For older OSS/Enterprise deployments, enable the unified provisioning flow only when the target version requires it:

    ```ini
    [feature_toggles]
    enable = provisioning
    ```

- For Grafana v12.4 OSS/Enterprise Git Sync, configure supported repository types:
  - This applies to pure Git, GitLab, Bitbucket, and local providers.

    ```ini
    [provisioning]
    repository_types = "git|github|bitbucket|gitlab|local"
    ```

| Setting | Effect |
| --- | --- |
| `provisioning` | Enables the newer unified provisioning system in versions where it is not enabled by default. |
| `repository_types` | Enables pure Git, GitLab, Bitbucket, and local provider types. |
| | Use it for Grafana v12.4 OSS/Enterprise Git Sync deployments. |

## Dashboard Resource Shape

Grafana v12+ dashboard APIs and observability-as-code workflows use the `dashboard.grafana.app/v1` resource shape.
That shape uses `metadata` and `spec`.
This is separate from Kubernetes CRDs managed by Grafana Operator or Crossplane.

Within `spec`, `folder` controls Grafana placement.
The remaining fields are the raw dashboard definition rather than an API import wrapper.

Complete resource example:

```yaml
apiVersion: dashboard.grafana.app/v1
kind: Dashboard
metadata:
  name: api-overview
  namespace: monitoring
  labels:
    team: platform
spec:
  folder: Operations
  title: API Overview
  uid: k8s-api-overview
  timezone: browser
  schemaVersion: 41
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

Key differences from raw or classic dashboard files:

| Aspect | Raw or classic provider file | Dashboard resource file |
| --- | --- | --- |
| Config location | Provider YAML + JSON files on disk | Git Sync, local-path provisioning, or dashboard API source |
| Source file shape | Raw dashboard JSON or Grafana classic wrapper | Fields go directly into `.spec` |
| ID management | Remove `id` or set it to `null` before committing shared source | Keep portable identity in metadata. |
| | | Use supported spec fields when Grafana accepts them. |
| Folder assignment | Provider-level `folder` or `folderUid` | `.spec.folder` or `.spec.folderUid` on the resource |
| Update detection | Filesystem poll or watch | Filesystem poll or watch |
| HTTP API response envelope | Separate concern; do not store API response wrappers on disk | Not applicable |

Minimal dashboard resource shape:

```yaml
apiVersion: dashboard.grafana.app/v1
kind: Dashboard
metadata:
  name: my-dashboard
spec:
  title: My Dashboard
```

Use when: the blocker is understanding the minimum dashboard resource shape.
Use this to distinguish dashboard resource files from legacy provider YAML.

## HTTP API Payload Boundary

File provisioning and dashboard resource files both differ from HTTP API response payloads.

Representative API payload:

```json
{
  "dashboard": {
    "id": null,
    "uid": "api-overview",
    "title": "API Overview",
    "schemaVersion": 41,
    "version": 1,
    "panels": []
  },
  "folderUid": "operations",
  "overwrite": true,
  "message": "sync from automation"
}
```

Use when: an HTTP API workflow explicitly requires wrapper fields.
Those fields include `dashboard`, `folderUid`, `overwrite`, and `message`.
Grafana's classic wrapped file shape may use `dashboard`, `folderUid`, and `overwrite` under a provider path.
Keep API-only fields such as `message` and response-only fields such as `meta` out of provider-path source directories.

## Datasource Provisioning Reference

Datasource provisioning is scope-adjacent to dashboard provisioning but is not covered by this skill.
Use this section as a pointer to the correct adjacent domain.

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
| Source files | Raw dashboard JSON files on disk | Not applicable (YAML-only definitions) |
| Delete behavior | Controlled per-provider | Datasource deletion always allowed unless restricted by org policy |
| UI edit support | `allowUiUpdates` toggle | Datasource edits in UI persist independently of YAML |

When you need datasource provisioning details:

- Consult the official Grafana documentation for datasource provisioning configuration reference.
- Datasource YAML files use a different schema than dashboard providers.
  - Field names, defaults, and constraints differ.
- This skill does not validate datasource configurations.
  - Switch to datasource-specific guidance for that domain.

Use when: the blocker is routing a question between dashboard provisioning and datasource provisioning.
