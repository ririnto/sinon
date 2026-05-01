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
6. Review the raw dashboard JSON source files for stable `uid`, deliberate `title`, expected `schemaVersion`, and instance-specific cleanup such as removing or nulling `id` before the file becomes Git-owned source.
7. Place the provider YAML where Grafana actually loads provisioning config for the target environment, and ensure the referenced dashboard path is mounted or copied into the Grafana runtime before expecting the dashboards to appear.
8. Check the provider YAML and dashboard source files themselves for path, folder strategy, environment application, and file-wins behavior before treating the dashboard delivery workflow as ready.

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

Field meanings: `name` is the provider identifier; `orgId` defaults to `1`; `folder` fixes the target folder and is mutually exclusive with `options.foldersFromFilesStructure`; `type` must be `file`; `disableDeletion` defaults to `false`; `updateIntervalSeconds` defaults to `30`; `allowUiUpdates` defaults to `false`; `options.path` must point at the dashboard JSON directory; `options.foldersFromFilesStructure` mirrors one directory level into Grafana folders when no provider-level folder is set.

Use when: you need one stable provider file for dashboard JSON already tracked in Git, with all fields explicitly documented.

## First Runnable Commands or Code Shape

Start by validating the provider YAML structure:

```bash
python3 -c "import yaml; yaml.safe_load(open('grafana/provisioning/dashboards.yaml'))"
```

Use when: the provider config was just edited and you need a fast syntax check before treating it as ready for deployment. Replace the path with your actual provider file location.

## Dashboard Source File Shape

Legacy file provisioning reads raw dashboard JSON files from the provider `options.path`. Keep the source files as plain dashboard definitions rather than API import payloads.

Representative source file:

```json
{
  "id": null,
  "uid": "team-api-overview",
  "title": "API Overview",
  "tags": ["operations", "api"],
  "timezone": "browser",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "30s",
  "panels": []
}
```

Field semantics in the source file:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer/null | recommended | Remove it or set it to `null` before committing cross-instance source. Numeric IDs are database-local and should not be treated as portable identifiers. |
| `uid` | string | recommended | Stable identifier for this dashboard. Used in URLs (`/d/<uid>/...`) and cross-dashboard links. Auto-generated if omitted, but explicit UIDs are strongly recommended for reproducible deployments. |
| `title` | string | yes | Human-readable dashboard title. |
| `schemaVersion` | integer | yes | Classic dashboard JSON schema version for the file. |
| `version` | integer | no | Dashboard revision metadata. Grafana ignores it as a conflict gate during file provisioning. |
| `panels` | array | yes | Panel definitions that make up the dashboard. |

The provider YAML controls placement and sync behavior. Keep `folder`, provider-level `folderUid`, `disableDeletion`, `allowUiUpdates`, and `updateIntervalSeconds` in the provider config instead of mixing API-only wrapper fields into the dashboard source file.

Use when: you need the correct on-disk shape for dashboard files that Grafana loads from a provider path.

## API and Resource Payload Boundary

Do not confuse raw file-provisioning source files with API or resource envelopes.

Dashboard import-style payloads use a separate outer object:

```json
{
  "dashboard": {
    "id": null,
    "uid": "team-api-overview",
    "title": "API Overview",
    "schemaVersion": 39,
    "version": 1,
    "panels": []
  },
  "folderUid": "operations",
  "overwrite": true,
  "message": "sync from automation"
}
```

Use this envelope only for API-driven or resource-specific workflows that explicitly ask for it. Do not commit this wrapper as the raw dashboard file under a legacy provider `options.path`.

Use when: the blocker is translating between provider-path source files and API or resource payloads that add `dashboard`, `folderUid`, `overwrite`, or `message` fields.

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

## Environment Variable Syntax

Environment variable substitution works in **provider YAML config values only**, not inside dashboard JSON definition files.

Syntax rules:

| Syntax | Behavior |
| --- | --- |
| `$ENV_VAR` | Replaced with value of `ENV_VAR`. Prefer this form when the resolved value itself may contain `$`. |
| `${ENV_VAR}` | Replaced with value of `ENV_VAR`. Use this form only when adjacent literal text would otherwise be parsed as part of the variable name. |
| `$$` | Escapes a literal dollar sign. Produces `$` in the resolved value. |

Two-pass replacement order:

1. First pass: replace `${ENV_VAR}` patterns with environment variable values.
2. Second pass: replace `$ENV_VAR` patterns.

This means `${ENV_VAR}` can be reinterpreted if the substituted value itself contains `$`. Use `$ENV_VAR` for those cases because Grafana resolves it in the second pass and does not run a third substitution pass over the inserted value.

Example provider using environment variables:

```yaml
apiVersion: 1
providers:
  - name: '$DASHBOARD_PROVIDER_NAME'
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
- Use `$ENV_VAR` instead of `${ENV_VAR}` when the resolved value may itself contain `$`.
- Example: if `DASHBOARD_PROVIDER_NAME=ops$team`, `name: $DASHBOARD_PROVIDER_NAME` resolves to `ops$team`, but `name: ${DASHBOARD_PROVIDER_NAME}` resolves to `ops` when `team` is unset because the second pass treats `$team` as another variable.
- Use `$$` only when the YAML value itself must contain a literal `$` character.

Use when: the blocker is parameterizing provider configs across environments without maintaining duplicate YAML files.

## Update Interval and Runtime Filesystem Behavior

Treat `updateIntervalSeconds` as the configured rescan interval for provisioned dashboard files.

Choose the value based on how quickly file changes need to appear and how predictable the deployment filesystem is:

- lower values shorten the time between Grafana rescan attempts
- higher values reduce scan frequency and can be easier to reason about on slower or indirect filesystem paths
- Docker bind mounts, network filesystems, and other non-local paths should be validated in the real runtime path instead of relying on an assumed watch-versus-poll threshold

Example Docker provider with a 30-second rescan interval:

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
| Dashboard menu -> **Save JSON to file** | Keep the raw dashboard JSON; normalize instance-specific fields such as `id` before committing |
| Dashboard menu -> **Copy JSON to clipboard** | Paste the raw dashboard JSON into the source file and normalize `id` if present |
| API `GET /api/dashboards/uid/<uid>` | Extract the `dashboard` object from the response and save that raw object as the source file |

UI export methods already produce raw dashboard JSON. API responses and import payloads add extra envelope fields such as `dashboard`, `meta`, `folderUid`, or `overwrite`; strip those envelopes before storing a file under the legacy provider path. The `id` field from one Grafana instance SHOULD be removed or set to `null` before the file becomes shared source.

Use when: the blocker is understanding what happens when dashboard files are added, modified, or removed from the source directory.

## Validate the Result

Validate the common case with these checks:

- the provider YAML points at the actual dashboard file path that will ship
- folder strategy is explicit and consistent with the file layout
- environment-specific application is explicit and does not hide which file each environment loads
- `allowUiUpdates` matches the intended workflow for UI edits versus Git-owned changes
- reviewers can tell whether file content or Grafana UI state wins after drift
- dashboard source files are raw dashboard JSON objects rather than API import payloads
- fixed-folder providers use `folder` or provider-level `folderUid` deliberately when stable folder targeting is needed
- `updateIntervalSeconds` matches the intended rescan cadence for the actual runtime filesystem
- `foldersFromFilesStructure` providers do not also set `folder` or `folderUid` at the provider level
- dashboard JSON files are reviewable and not just opaque exports dropped into the tree
- the provider YAML is internally consistent about path, folder mapping, and update behavior
- environment variables in provider YAML use only supported Grafana substitution syntax: `$ENV_VAR`, `${ENV_VAR}`, and `$$`

## Output contract

Return:

1. the recommended provider YAML or review decision
2. the dashboard source path, folder strategy, and environment application shape
3. validation or deployment checks, including how Grafana will discover the provider file and dashboard path
4. remaining risks, assumptions, or drift-related caveats

## References

| If the blocker is... | Read... |
| --- | --- |
| folder mirroring, file layout, or `foldersFromFilesStructure` tradeoffs | [`./references/folder-organization.md`](./references/folder-organization.md) |
| UI edits, file-wins behavior, delete-on-remove, version property, or drift debugging | [`./references/drift-and-ui-updates.md`](./references/drift-and-ui-updates.md) |
| Git Sync, K8s resource shapes, or newer observability-as-code features | [`./references/newer-provisioning-features.md`](./references/newer-provisioning-features.md) |

## Invariants

- A provider MUST specify exactly one of `folder` or `options.foldersFromFilesStructure`, not both.
- A provider using `foldersFromFilesStructure: true` MUST NOT set `folder` or `folderUid` at the provider level.
- Legacy file-provisioning source files MUST be raw dashboard JSON objects, not API import payloads.
- Dashboard `id` SHOULD be removed or set to `null` before a source file is reused across Grafana instances.
- `options.path` MUST resolve to a readable directory at Grafana runtime.
- `allowUiUpdates: true` MUST be accompanied by a documented merge-back workflow.
- The ordinary dashboard provisioning path MUST remain understandable from this file alone.
- Dashboard source paths MUST be explicit and consistent across the provider config and the actual filesystem layout.
- File content SHOULD remain the reviewable source of truth over UI state.
- Drift behavior SHOULD be obvious before enabling UI edits.
- Environment variable substitution MUST NOT be relied upon inside dashboard JSON files.
- API or resource payloads that use `dashboard`, `folderUid`, `overwrite`, or `message` MUST stay in API-specific sections and MUST NOT be documented as raw provider-path file content.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| storing API import payloads under the provider path | legacy file provisioning expects raw dashboard JSON files, not API envelopes with `dashboard` / `overwrite` / `folderUid` | commit the raw dashboard object itself as the source file |
| leaving `id` set to a numeric value from a UI export | numeric IDs are instance-local and create portability problems across Grafana instances | remove `id` or set it to `null` before committing the shared source file |
| managing `version` numbers in dashboard JSON files | Grafana ignores the `version` property during provisioning entirely | leave version as-is from export; do not attempt to bump it |
| choosing `updateIntervalSeconds` by assumed watch-versus-poll cutoffs | hard-coded thresholds are deployment-specific and may not match the actual runtime filesystem behavior | set the interval as the desired rescan cadence and verify change pickup on the real path Grafana reads |
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
  - datasource provisioning configuration
