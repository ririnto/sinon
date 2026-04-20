---
title: "Dashboard Provisioning Drift and UI Updates"
description: "Open this when file-wins behavior, UI edits, delete-on-source-removal, version property behavior, export workflows, or drift debugging is the blocker."
---

Use this reference when the delivery path is mostly correct, but source-of-truth behavior is still ambiguous.

## File-Wins Rule

Provisioned dashboard files remain the durable source of truth. UI edits do not replace the provisioning source unless the reviewed file changes too.

## `allowUiUpdates`

- `allowUiUpdates: false` keeps UI saves blocked for provisioned dashboards
- `allowUiUpdates: true` allows temporary UI edits, but file updates still overwrite that state later

Example provider:

```yaml
apiVersion: 1
providers:
  - name: operations-dashboards
    type: file
    allowUiUpdates: true
    options:
      path: /var/lib/grafana/dashboards/operations
```

Drift example:

```text
1. operator edits a provisioned dashboard in the UI
2. reviewed JSON in Git remains unchanged
3. next file sync reloads the provisioned JSON
4. UI-only edit disappears because file content still wins
```

Use when: the blocker is explaining why a UI change seemed to work temporarily but did not become the durable dashboard state.

## Delete on Source File Removal

Removing a dashboard source file from the provider path triggers deletion in Grafana by default.

Default flow (`disableDeletion: false`, the default):

```text
1. api-overview.json exists at options.path
2. Grafana provisions (or updates) the dashboard
3. operator deletes or moves api-overview.json out of the directory
4. next sync cycle detects the file is missing
5. Grafana deletes the dashboard from its database
6. any bookmarks or linked alert rules that referenced this dashboard break
```

With deletion blocked (`disableDeletion: true`):

```text
1. api-overview.json exists at options.path
2. Grafana provisions the dashboard
3. operator removes api-overview.json from the directory
4. next sync cycle detects the file is missing
5. Grafana retains the dashboard; deletion is suppressed
6. re-adding the file later resumes normal overwrite behavior
7. the dashboard receives whatever content is now in the re-added file
```

When to use `disableDeletion: true`:

- dashboards that operators may temporarily remove from source control during refactoring
- shared Grafana instances where one team's cleanup should not destroy another team's viewed dashboard
- migration scenarios where old source files are removed before new ones are fully validated

Example deletion-safe provider:

```yaml
apiVersion: 1
providers:
  - name: operations-dashboards
    type: file
    disableDeletion: true
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards/operations
```

Use when: the blocker is deciding whether removing a source file should destroy the corresponding Grafana dashboard.

## Version Property Behavior

The `version` field inside `dashboard` JSON is **ignored** by Grafana's provisioning system.

What actually happens:

```text
1. source file contains "version": 1
2. Grafana database has version 15 for the same uid
3. operator updates the source file content (panels, layout, queries)
4. source file still says "version": 1
5. Grafana syncs: reads the file, applies all changes, increments internal version to 16
6. the "version": 1 in the file is never compared or enforced
```

Practical consequence:

- Do not attempt to manage version numbers in source files.
- Do not use version as a conflict-detection mechanism for provisioning.
- The version field in exported files can be left as-is; it has no effect on provisioning behavior.
- Version only matters for manual UI save conflicts between two browser sessions editing the same dashboard simultaneously -- a scenario that does not apply to file-based provisioning where file content always wins.

Use when: the blocker is understanding why version numbers in dashboard JSON files seem to have no effect during provisioning.

## UI Export Workflows

All three Grafana UI export methods produce raw dashboard JSON that requires post-processing before it works as a provisioning source file.

### Save JSON to file (Dashboard menu -> Save JSON to file)

Produces raw dashboard JSON without the provisioning wrapper.

Required post-processing:

```json
{
  "dashboard": <paste exported JSON here>,
  "folderUid": "",
  "overwrite": true
}
```

Additionally, set `"id": null` inside the pasted dashboard object.

### Copy JSON to clipboard (Dashboard menu -> Copy JSON to clipboard)

Identical output format to Save JSON to file. Same wrapper and id-stripping steps apply.

### API export (`GET /api/dashboards/uid/<uid>`)

Response shape:

```json
{
  "dashboard": { ... },
  "meta": { ... }
}
```

The response already nests the dashboard under a `dashboard` key, but you must still:

1. Wrap in `{ "dashboard": <response.dashboard>, "overwrite": true }`
2. Set `id: null` inside the dashboard object
3. Add `folderUid` if targeting a specific folder by UID

### Common export gotcha: ID leakage

Every export method includes the numeric `id` assigned by the exporting Grafana instance. This ID is instance-specific and will conflict (or silently map wrong) when provisioning to a different instance. Always strip or nullify `id`.

Before (exported -- unsafe for provisioning):

```json
{
  "id": 42,
  "uid": "abc123def",
  "title": "API Overview",
  ...
}
```

After (provisioning-ready):

```json
{
  "dashboard": {
    "id": null,
    "uid": "abc123def",
    "title": "API Overview",
    ...
  },
  "overwrite": true
}
```

Use when: the blocker is converting a UI-exported dashboard into a valid provisioning source file.

## Review Questions

- can an operator tell where permanent changes must be made
- is `allowUiUpdates` aligned with the actual team workflow
- would a failed provisioning reload be easier to debug with a smaller, cleaner source tree
- is `disableDeletion` set correctly for the team's file-removal workflow
- are exported dashboards being wrapped with `overwrite: true` before committing
- is `id` being stripped from all exported dashboard files
