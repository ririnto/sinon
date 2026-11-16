# Provider Examples

## Ready-to-Adapt Templates

Fixed-folder provider -- send one path into one Grafana folder:

```yaml
apiVersion: 1
providers:
  - name: operations-dashboards
    folder: Operations
    type: file
    updateIntervalSeconds: 30
    allowUiUpdates: false
    options:
      path: /var/lib/grafana/dashboards/operations

```

Use when: the dashboard set belongs in one explicit Grafana folder.

Filesystem-driven folders -- mirror directories into Grafana folders:

```yaml
apiVersion: 1
providers:
  - name: team-dashboards
    type: file
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: true

```

Use when: repository directories already model the folder split and nested folder trees are not required.

Dashboard JSON source file with explicit uid -- correct legacy file-provisioning shape:

```json
{
  "id": null,
  "uid": "ops-api-overview",
  "title": "API Overview",
  "tags": ["generated"],
  "timezone": "browser",
  "schemaVersion": 41,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "title": "Request Rate",
      "type": "timeseries",
      "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
      "targets": [
        {
          "expr": "sum(rate(http_requests_total[5m])) by (method)",
          "legendFormat": "{{method}}"
        }
      ]
    }
  ]
}

```

Use when: you need a complete, copy-adaptable dashboard JSON file that can live directly under the provider path.

Environment application split -- keep one reviewed dashboard source with environment-specific provider paths:

```text
grafana/
  provisioning/
    dashboards-dev.yaml
    dashboards-prod.yaml
  dashboards/
    operations/
      api-overview.json

```

Use when: the same dashboard JSON should be reviewed once but applied through different provider files or paths per environment.

Dashboard source tree -- keep reviewed assets obvious:

```text
grafana/
  provisioning/
    dashboards.yaml
  dashboards/
    operations/
      api-overview.json

```

Use when: you need one repository layout that makes provider config and provisioned dashboard files reviewable together.
