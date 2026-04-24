---
name: dashboard-provisioning
description: >-
  Provision Grafana dashboards as code via provider YAML with drift control, reviewable folder layouts, and predictable delete behavior. Use this skill when provisioning Grafana dashboards as code via provider YAML files, organizing dashboard-as-code folder layouts, controlling drift between file sources and UI state, delivering reviewable dashboard configurations, or understanding Grafana provisioning file locations, environment variable syntax, and delete behavior.
---

# Dashboard Provisioning

Provision Grafana dashboards as reviewed files instead of relying on long-lived manual UI state. The common case is one provider YAML file, one deliberate dashboard source directory, one folder strategy, and one review path that keeps file content as the source of truth when Grafana and Git disagree.

## Common-Case Workflow

1. Start from the dashboard files that should become the reviewed source of truth.
2. Define one provider YAML with a clear `name`, one dashboard path, and one folder strategy.
3. Decide how each environment applies the same dashboard source: shared provider path, separate provider files, or environment-specific directories with the same review rules.
4. Decide whether the path maps to a fixed folder or to `foldersFromFilesStructure`.
5. Keep `allowUiUpdates` and `updateIntervalSeconds` deliberate so operators understand whether UI edits are temporary or part of the delivery path.
6. Review the dashboard JSON wrapper structure: `dashboard` key, `overwrite: true`, `id: null`, and optional `folderUid` inside each JSON file.
7. Place the provider YAML where Grafana actually loads provisioning config for the target environment, and ensure the referenced dashboard path is mounted or copied into the Grafana runtime before expecting the dashboards to appear.
8. Check the provider YAML itself for path, folder strategy, environment application, and file-wins behavior before treating the dashboard delivery workflow as ready.

## Grafana Config File Locations

Grafana discovers provisioning config from these paths at startup:

| Environment | Provider YAML location | Default dashboard base |
| --- | --- | --- |
| Docker / official image | `/etc/grafana/provisioning/dashboards/*.yaml` | `/var/lib/grafana/dashboards` |
| Linux package install | `<grafana_home>/conf/provisioning/dashboards/*.yaml` | `<grafana_home>/data/dashboards` |
| Custom binary | set via `[paths].provisioning` in `grafana.ini` | set via `[paths].data` |

Config file load order (later values override earlier):

```text
1. $GF_PATHS_HOME/conf/defaults.ini          (bundled defaults)
2. $GF_PATHS_HOME/conf/custom.ini             (distribution overrides)
3. /etc/grafana/grafana.ini                   (system-level config)
4. --config=<path> CLI flag                   (explicit override)
5. GF_ environment variables                  (highest priority)
```

Use when: you need to know exactly where Grafana reads its config and which file takes precedence for a given setting.

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
  # Required. Human-readable identifier for this provider.
  - name: observability-dashboards
    # Optional. Grafana organization ID. Default: 1 (default org).
    orgId: 1
    # Optional. Fixed Grafana folder name for all dashboards from this provider.
    # Mutually exclusive with options.foldersFromFilesStructure.
    # If omitted and foldersFromFilesStructure is false, dashboards go to Grafana root.
    folder: Operations
    # Required. Must be "file" for filesystem-based provisioning.
    type: file
    # Optional. Prevent deletion of provisioned dashboards when source file is removed.
    # Default: false (removing the source file deletes the dashboard).
    disableDeletion: false
    # Optional. How often Grafana rescans the dashboard directory.
    # >10 seconds: polling-based reload.
    # <=10 seconds: filesystem watch events (inotify / FSEvents).
    # Default: 30.
    updateIntervalSeconds: 30
    # Optional. Allow UI edits on provisioned dashboards.
    # Default: false.
    allowUiUpdates: false
    options:
      # Required. Filesystem path to the directory containing dashboard JSON files.
      path: /var/lib/grafana/dashboards/operations
      # Optional. Mirror top-level subdirectories into Grafana folders.
      # When true: folder and folderUid at provider level MUST be unset.
      # When true: only one level of nesting is supported (no nested folders).
      # Default: false.
      foldersFromFilesStructure: false
```

Use when: you need one stable provider file for dashboard JSON already tracked in Git, with all fields explicitly documented.

## First Runnable Commands or Code Shape

Start by validating the provider YAML structure:

```bash
python3 -c "import yaml; yaml.safe_load(open('grafana/provisioning/dashboards.yaml'))"
```

Use when: the provider config was just edited and you need a fast syntax check before treating it as ready for deployment. Replace the path with your actual provider file location.

## Dashboard JSON Wrapper Schema

Every dashboard source file MUST use the Grafana provisioning wrapper format. The raw panel JSON exported from the UI is not sufficient by itself.

Required wrapper structure:

```json
{
  "dashboard": {
    "id": null,
    "uid": "team-api-overview",
    "title": "API Overview",
    "tags": ["operations", "api"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "30s",
    "panels": []
  },
  "folderUid": "",
  "overwrite": true,
  "message": ""
}
```

Field semantics inside the wrapper:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `dashboard` | object | yes | The actual dashboard definition. Always nested under this key. |
| `dashboard.id` | integer/null | recommended | Set to `null` for provisioning. Grafana auto-assigns a new ID on first import; subsequent updates match by uid. Never hardcode an ID from another instance. |
| `dashboard.uid` | string | recommended | Stable identifier for this dashboard. Used in URLs (`/d/<uid>/...`) and cross-dashboard links. Auto-generated if omitted, but explicit UIDs are strongly recommended for reproducible deployments. |
| `folderUid` | string | no | UID of the target Grafana folder. Only needed when using fixed-folder mode and you want to reference a specific folder by UID rather than name. Auto-resolved from the provider's `folder` name if omitted. |
| `overwrite` | boolean | **yes** | Must be `true`. Without this, Grafana will import the dashboard once and never update it on subsequent syncs even if the file content changes. This is the most common provisioning gotcha. |
| `message` | string | no | Change message shown in the dashboard version history when the dashboard is updated. Leave empty for automated provisioning. |

**Critical invariant**: The `version` property inside `dashboard` is ignored by Grafana during provisioning. Even if the file contains `"version": 1` and the database has `"version": 15`, Grafana uses the file content regardless. Do not attempt to manage version numbers in source files.

Use when: you need the exact wrapper shape that Grafana expects for file-based provisioning, including why `overwrite: true` and `id: null` are non-negotiable.

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

Dashboard JSON file with explicit uid and overwrite -- correct provisioning shape:

```json
{
  "dashboard": {
    "id": null,
    "uid": "ops-api-overview",
    "title": "API Overview",
    "tags": ["generated"],
    "timezone": "browser",
    "schemaVersion": 39,
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
  },
  "folderUid": "",
  "overwrite": true
}
```

Use when: you need a complete, copy-adaptable dashboard JSON file that follows all provisioning conventions.

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

## Environment Variable Syntax

Environment variable substitution works in **provider YAML config values only**, not inside dashboard JSON definition files.

Syntax rules:

| Syntax | Behavior |
| --- | --- |
| `$ENV_VAR` | Replaced with value of `ENV_VAR`. |
| `${ENV_VAR}` | Same as above; use when adjacent text would consume the name. |
| `$$` | Literal dollar sign. Produces `$` in the resolved value. |
| `${ENV_VAR:-default}` | Use `default` if `ENV_VAR` is unset or empty. |

Two-pass replacement order:

1. First pass: replace `${...}` and `$VAR` patterns with environment variable values.
2. Second pass: replace `$$` with literal `$`.

Example provider using environment variables:

```yaml
apiVersion: 1
providers:
  - name: '${DASHBOARD_PROVIDER_NAME}'
    type: file
    options:
      path: '${DASHBOARD_PATH}/dashboards'
```

Resolved at runtime with `DASHBOARD_PROVIDER_NAME=prod-dashboards` and `DASHBOARD_PATH=/opt/grafana`:

```yaml
apiVersion: 1
providers:
  - name: 'prod-dashboards'
    type: file
    options:
      path: '/opt/grafana/dashboards'
```

Constraints:

- Substitution only occurs in provider YAML values, never in dashboard `.json` files.
- Unresolved variables (no default, env var missing) produce an error at Grafana startup.
- Use `$$` when a config value must contain a literal `$` character.

Use when: the blocker is parameterizing provider configs across environments without maintaining duplicate YAML files.

## Update Interval and Filesystem Watch Behavior

The `updateIntervalSeconds` value controls how Grafana detects changes to dashboard source files:

| Value range | Detection method | Typical use |
| --- | --- | --- |
| `> 10` seconds | Polling (directory scan on interval) | Docker bind mounts, NFS, any filesystem without reliable event notification |
| `<= 10` seconds | Filesystem watch events (inotify on Linux, FSEvents on macOS) | Local filesystems with native event support |

Gotcha: Docker bind mounts and NFS shares do not reliably forward filesystem watch events to the container/host. When running Grafana in Docker with bind-mounted dashboard directories, always use `updateIntervalSeconds: 30` or higher. Values of 10 or below will silently fail to detect file changes because the watch events never arrive.

Example safe Docker provider:

```yaml
apiVersion: 1
providers:
  - name: docker-dashboards
    type: file
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

Use when: the blocker is choosing an update interval or debugging why dashboard file changes are not picked up by a running Grafana instance.

## Delete Behavior

Removing a dashboard source file triggers deletion in Grafana unless `disableDeletion: true` is set on the provider.

Default behavior (`disableDeletion: false`):

```text
1. api-overview.json exists in the provider path
2. Grafana provisions the dashboard
3. operator removes api-overview.json from the source directory
4. next sync cycle detects the file is gone
5. Grafana deletes the corresponding dashboard
```

With deletion disabled (`disableDeletion: true`):

```text
1. api-overview.json exists in the provider path
2. Grafana provisions the dashboard
3. operator removes api-overview.json from the source directory
4. next sync cycle detects the file is gone
5. Grafana keeps the dashboard (deletion blocked)
6. re-adding the file later resumes normal update behavior
```

UI export workflows for moving a dashboard into provisioning:

| Export method | Post-export step required |
| --- | --- |
| Dashboard menu -> **Save JSON to file** | Wrap the output in `{ "dashboard": <output>, "overwrite": true }`; set `id: null` |
| Dashboard menu -> **Copy JSON to clipboard** | Paste into a new file, wrap as above, strip the `id` field |
| API `GET /api/dashboards/uid/<uid>` | Extract the `dashboard` object from the response, wrap as above |

All three export methods produce raw dashboard JSON that lacks the required provisioning wrapper. The `id` field from the export MUST be set to `null` or removed entirely; keeping the original ID from one Grafana instance causes conflicts when provisioning to a different instance.

Use when: the blocker is understanding what happens when dashboard files are added, modified, or removed from the source directory.

## Validate the Result

Validate the common case with these checks:

- the provider YAML points at the actual dashboard file path that will ship
- folder strategy is explicit and consistent with the file layout
- environment-specific application is explicit and does not hide which file each environment loads
- `allowUiUpdates` matches the intended workflow for UI edits versus Git-owned changes
- reviewers can tell whether file content or Grafana UI state wins after drift
- dashboard JSON source files use the correct wrapper schema (`dashboard` key, `overwrite: true`, `id: null`)
- `folderUid` is used correctly when stable cross-instance folder references are needed
- `updateIntervalSeconds` accounts for Docker bind mount or NFS constraints
- `foldersFromFilesStructure` providers do not also set `folder` or `folderUid` at the provider level
- dashboard JSON files are reviewable and not just opaque exports dropped into the tree
- the provider YAML is internally consistent about path, folder mapping, and update behavior
- environment variables in provider YAML use correct syntax and have fallback defaults where appropriate

## Output contract

Return:

1. the recommended provider YAML or review decision
2. the dashboard source path, folder strategy, and environment application shape
3. validation or deployment checks, including how Grafana will discover the provider file and dashboard path
4. remaining risks, assumptions, or drift-related caveats

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| folder mirroring, file layout, or `foldersFromFilesStructure` tradeoffs | [`./references/folder-organization.md`](./references/folder-organization.md) |
| UI edits, file-wins behavior, delete-on-remove, version property, or drift debugging | [`./references/drift-and-ui-updates.md`](./references/drift-and-ui-updates.md) |
| Git Sync, K8s resource shapes, newer observability-as-code features, or datasource provisioning reference | [`./references/newer-provisioning-features.md`](./references/newer-provisioning-features.md) |

## Invariants

- A provider MUST specify exactly one of `folder` or `options.foldersFromFilesStructure`, not both.
- A provider using `foldersFromFilesStructure: true` MUST NOT set `folder` or `folderUid` at the provider level.
- Every dashboard JSON file MUST contain the `dashboard` wrapper key with `overwrite: true`.
- `dashboard.id` MUST be `null` in provisioning context; never hardcode IDs from other instances.
- `options.path` MUST resolve to a readable directory at Grafana runtime.
- `allowUiUpdates: true` MUST be accompanied by a documented merge-back workflow.
- The ordinary dashboard provisioning path MUST remain understandable from this file alone.
- Dashboard source paths MUST be explicit and consistent across the provider config and the actual filesystem layout.
- File content SHOULD remain the reviewable source of truth over UI state.
- Drift behavior SHOULD be obvious before enabling UI edits.
- Environment variable substitution MUST NOT be relied upon inside dashboard JSON files.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| omitting `overwrite: true` from the dashboard JSON wrapper | Grafana imports once and never updates on subsequent syncs | always include `"overwrite": true` in every dashboard source file |
| leaving `dashboard.id` set to a numeric value from a UI export | ID conflicts between instances; provisioning cannot reliably match by ID | set `"id": null` in every provisioning source file |
| managing `version` numbers in dashboard JSON files | Grafana ignores the `version` property during provisioning entirely | leave version as-is from export; do not attempt to bump it |
| using `updateIntervalSeconds: 5` with Docker bind mounts | Filesystem watch events do not traverse bind mounts; changes go undetected | use `>= 30` for any containerized or network-mounted path |
| mixing dashboard authoring guidance into provisioning docs | users lose the distinction between dashboard content and dashboard delivery | keep panel design and visualization authoring out of this skill, and keep file delivery here |
| enabling `allowUiUpdates` without a clear merge-back workflow | UI edits appear to work and are later overwritten by files | document file-wins behavior and keep Git as the source of truth |
| using `foldersFromFilesStructure` without unsetting `folder` | Grafana rejects the provider config at startup | ensure `folder` and `folderUid` are absent when `foldersFromFilesStructure: true` |
| assuming nested directories create nested Grafana folders | only one level of directory-to-folder mapping exists | flatten to single level or use multiple providers |
| putting environment variables inside dashboard JSON files | substitution only runs in provider YAML config values | parameterize at the provider level, not the dashboard level |
| provisioning raw exports with no cleanup or review path | drift and noisy diffs accumulate quickly | keep provisioned dashboard files normalized and reviewable |

## Scope Boundaries

- Activate this skill for:
  - Grafana dashboard provider YAML and dashboard-as-code delivery
  - folder organization, drift control, and reviewability
  - dashboard provisioning behavior and file ownership decisions
  - Grafana config file locations and load order
  - environment variable syntax in provider configs
  - delete behavior and UI export workflows
- Do not activate for:
  - dashboard panel authoring and visualization design
  - Prometheus alert-rule authoring or testing
  - Alertmanager routing and notification design
  - datasource provisioning configuration (see references for pointer)
