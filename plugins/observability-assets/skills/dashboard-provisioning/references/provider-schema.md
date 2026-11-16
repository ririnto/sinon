# Provider Configuration

## Minimal Provider Config

Minimal provider -- smallest safe shape before adding optional controls:

```yaml
apiVersion: 1
providers:
  - name: observability-dashboards
    type: file
    options:
      path: /var/lib/grafana/dashboards/operations

```

Use when: you need the smallest safe provisioning shape before adding folder and update controls.
The provider file only matters after Grafana loads it from the environment's provisioning directory and can read the dashboard files at the configured `options.path`.

Complete provider config -- every field with type, default, and description:

```yaml
apiVersion: 1
providers:
  - name: observability-dashboards
    orgId: 1
    folder: Operations
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: false
    options:
      path: /var/lib/grafana/dashboards/operations
      foldersFromFilesStructure: false

```

Field meanings: `name` is the provider identifier.
`orgId` defaults to `1`.
`folder` fixes the target folder and is mutually exclusive with `options.foldersFromFilesStructure`.
`type` must be `file`.
`disableDeletion` defaults to `false`.
`updateIntervalSeconds` defaults to `30`.
`allowUiUpdates` defaults to `false`.
`options.path` must point at the dashboard JSON directory.
`options.foldersFromFilesStructure` mirrors the directory hierarchy under `options.path` into Grafana folders, up to four levels deep, when provider-level `folder` and `folderUid` are unset.

Use when: you need one stable provider file for dashboard JSON already tracked in Git, with all fields explicitly documented.
