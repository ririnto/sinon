---
title: "Dashboard Provisioning Folder Organization"
description: "Open this when folder mapping, file layout, foldersFromFilesStructure constraints, folderUid field, or no-nested-folders behavior is the blocker."
---

Use this reference when the provider exists, but the file tree and folder strategy are still unclear.

## Fixed Folder vs Filesystem Mirroring

Use a fixed `folder` when:

- all provisioned dashboards belong in one Grafana folder
- the repository tree is optimized for source control rather than Grafana folder names
- you need a stable, named target that does not depend on directory layout

Use `foldersFromFilesStructure: true` when:

- top-level directories already represent the intended folder split
- a flat folder mapping is enough
- the team wants directory names to drive Grafana folder creation automatically

Example fixed-folder provider:

```yaml
apiVersion: 1
providers:
  - name: team-dashboards
    folder: Operations
    type: file
    options:
      path: /var/lib/grafana/dashboards/operations
```

Example filesystem-mirror provider:

```yaml
apiVersion: 1
providers:
  - name: team-dashboards
    type: file
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: true
```

Example tree for filesystem mirroring:

```text
/var/lib/grafana/dashboards/
  api/
    overview.json
  platform/
    capacity.json
```

This produces two Grafana folders: `api` (containing overview) and `platform` (containing capacity).

Use when: the blocker is deciding whether one provider should mirror top-level directories into Grafana folders.

## `foldersFromFilesStructure` Constraints

Enabling `foldersFromFilesStructure: true` imposes strict requirements on the provider configuration.

**Must unset both `folder` and `folderUid`:**

When `foldersFromFilesStructure` is active, the provider-level `folder` and `folderUid` fields MUST be absent. Grafana rejects the provider at startup if both are present.

Invalid -- will fail at startup:

```yaml
apiVersion: 1
providers:
  - name: broken-provider
    folder: Operations              # <-- CONFLICTS with foldersFromFilesStructure
    type: file
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: true   # <-- CONFLICTS with folder
```

Valid -- folder is omitted:

```yaml
apiVersion: 1
providers:
  - name: working-provider
    type: file
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: true
```

**No nested folder support (one level only):**

Only the first level of subdirectories under `options.path` maps to Grafana folders. Deeper nesting is ignored -- files in sub-subdirectories are not provisioned and produce no error or warning.

Tree that works correctly:

```text
dashboards/
  team-a/
    dashboard-1.json        --> Grafana folder "team-a"
  team-b/
    dashboard-2.json        --> Grafana folder "team-b"
  root-dashboard.json       --> Grafana root (no folder)
```

Tree with ignored nesting:

```text
dashboards/
  team-a/
    subproject/
      dashboard-3.json      --> NOT provisioned (too deep)
    dashboard-1.json        --> Grafana folder "team-a" (this one works)
```

**Root-level dashboards go to Grafana root:**

JSON files placed directly in `options.path` (not inside any subdirectory) are provisioned into the Grafana root folder (General / no parent folder).

```text
dashboards/
  global-overview.json       --> Grafana root folder
  team-a/
    sprint.json              --> Grafana folder "team-a"
```

**Folder UIDs are auto-generated:**

When using `foldersFromFilesStructure`, Grafana creates folders with auto-generated UIDs. If you need stable, cross-instance folder references, use fixed-folder mode with explicit `folderUid` in each dashboard JSON wrapper instead.

Use when: the blocker is debugging why a `foldersFromFilesStructure` provider fails to start, why nested directories do not create nested Grafana folders, or where root-level dashboards end up.

## The `folderUid` Field

The `folderUid` field exists in two places with different semantics:

### Provider-level `folderUid` (not commonly used)

Set at the provider top level alongside `folder`. References an existing Grafana folder by its UID rather than by name. Mutually exclusive with `foldersFromFilesStructure`.

```yaml
apiVersion: 1
providers:
  - name: targeted-provider
    folderUid: "abc123def"          # targets folder by UID, not name
    type: file
    options:
      path: /var/lib/grafana/dashboards/target
```

Use when: the folder name might change but the UID must remain stable across deployments.

### Dashboard JSON wrapper `folderUid`

Set inside each individual dashboard source file. Overrides the provider-level folder placement for that specific dashboard.

```json
{
  "dashboard": {
    "id": null,
    "uid": "my-dashboard",
    "title": "My Dashboard",
    ...
  },
  "folderUid": "xyz789ghi",
  "overwrite": true
}
```

Behavior:

- If set to a non-empty string: places this dashboard into the folder with that UID.
- If set to empty string `""` or omitted: falls back to the provider's `folder` or `folderUid` setting.
- Works with both fixed-folder providers and `foldersFromFilesStructure` providers (overrides the auto-assigned folder for that one dashboard).

Use when: the blocker is placing individual dashboards into specific folders by UID regardless of the provider's default folder assignment.

## Review Questions

- does the repository tree communicate ownership clearly
- would folder mirroring create confusing names or unstable placement
- are nested directories being mistaken for nested Grafana folders
- is `folder` or `folderUid` still set when `foldersFromFilesStructure: true`
- does the team need stable folder UIDs for cross-dashboard links or programmatic access
- are root-level dashboard files intentionally placed outside all subdirectories
