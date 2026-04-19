---
name: dashboard-provisioning
description: >-
  Use this skill when the user asks to "provision Grafana dashboards from files", "write dashboard provider YAML", "organize dashboard-as-code folders", "prevent dashboard drift", or needs guidance on Grafana dashboard provisioning and reviewable dashboard delivery.
metadata:
  title: "Dashboard Provisioning"
  official_project_url: "https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards"
  reference_doc_urls:
    - "https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards"
    - "https://grafana.com/docs/grafana/latest/dashboards/json-model/"
    - "https://grafana.com/docs/grafana/latest/observability-as-code/provision-resources/provisioned-dashboards/"
  version: "latest"
---

# Dashboard Provisioning

## Overview

Use this skill to provision Grafana dashboards as reviewed files instead of relying on long-lived manual UI state. The common case is one provider YAML file, one deliberate dashboard source directory, one folder strategy, and one review path that keeps file content as the source of truth when Grafana and Git disagree.

## Use This Skill When

- You are writing or reviewing Grafana dashboard provisioning config.
- You need guidance on provider YAML, dashboard file layout, folder mapping, `allowUiUpdates`, or drift control.
- You need to deliver dashboards as code without redesigning the dashboard panels themselves.
- Do not use this skill when the main task is dashboard authoring, panel design, or Prometheus alert-rule behavior rather than dashboard delivery and lifecycle control.

## Common-Path Coverage

Keep the ordinary path inside this file. Cover these topics here before sending the reader to a reference:

- provider YAML structure and required fields
- dashboard JSON source management and source-of-truth decisions
- folder and file organization
- environment application strategy
- `allowUiUpdates`, `updateIntervalSeconds`, and drift behavior
- reviewability and file-wins semantics

## Version-Sensitive Features

- Legacy dashboard file provisioning through provider YAML is the stable baseline in this skill and is documented without a narrower minimum Grafana version.
- Git Sync, the newer on-prem file provisioning flow, and the `provisioning` plus `kubernetesDashboards` feature toggles should be treated as newer Grafana features rather than a universal baseline.
- Use version-gated observability-as-code features only when the deployment actually supports the newer Grafana behavior they require.

## Common-Case Workflow

1. Start from the dashboard files that should become the reviewed source of truth.
2. Define one provider YAML with a clear `name`, one dashboard path, and one folder strategy.
3. Decide how each environment applies the same dashboard source: shared provider path, separate provider files, or environment-specific directories with the same review rules.
4. Decide whether the path maps to a fixed folder or to `foldersFromFilesStructure`.
5. Keep `allowUiUpdates` and `updateIntervalSeconds` deliberate so operators understand whether UI edits are temporary or part of the delivery path.
6. Review the dashboard JSON or resource files and the provider config together so folder placement, environment application, and drift behavior are explicit.
7. Place the provider YAML where Grafana actually loads provisioning config for the target environment, and ensure the referenced dashboard path is mounted or copied into the Grafana runtime before expecting the dashboards to appear.
8. Check the provider YAML itself for path, folder strategy, environment application, and file-wins behavior before treating the dashboard delivery workflow as ready.

## Minimal Setup

Minimal provider config:

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
```

Use when: you need one stable provider file for dashboard JSON already tracked in Git.

## First Runnable Commands or Code Shape

Start from one provider file that makes the source path and folder strategy explicit:

```yaml
apiVersion: 1
providers:
  - name: observability-dashboards
    type: file
    options:
      path: /var/lib/grafana/dashboards/operations
```

Use when: you need the smallest safe provisioning shape before adding folder and update controls.
The provider file only matters after Grafana loads it from the environment’s provisioning directory and can read the dashboard files at the configured `options.path`.

## Ready-to-Adapt Templates

Fixed-folder provider — send one path into one Grafana folder:

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

Filesystem-driven folders — mirror directories into Grafana folders:

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

Environment application split — keep one reviewed dashboard source with environment-specific provider paths:

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

Dashboard source tree — keep reviewed assets obvious:

```text
grafana/
  provisioning/
    dashboards.yaml
  dashboards/
    operations/
      api-overview.json
```

Use when: you need one repository layout that makes provider config and provisioned dashboard files reviewable together.

## Validate the Result

Validate the common case with these checks:

- the provider YAML points at the actual dashboard file path that will ship
- folder strategy is explicit and consistent with the file layout
- environment-specific application is explicit and does not hide which file each environment loads
- `allowUiUpdates` matches the intended workflow for UI edits versus Git-owned changes
- reviewers can tell whether file content or Grafana UI state wins after drift
- dashboard JSON source files are reviewable and not just opaque exports dropped into the tree
- the provider YAML is internally consistent about path, folder mapping, and update behavior

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
| UI edits, file-wins behavior, or drift debugging | [`./references/drift-and-ui-updates.md`](./references/drift-and-ui-updates.md) |
| Git Sync or newer observability-as-code provisioning features | [`./references/newer-provisioning-features.md`](./references/newer-provisioning-features.md) |

## Invariants

- MUST keep the ordinary dashboard provisioning path understandable from this file alone.
- MUST make the dashboard source path explicit.
- MUST make folder strategy explicit.
- SHOULD keep file content as the reviewable source of truth.
- SHOULD make drift behavior obvious before enabling UI edits.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mixing dashboard authoring guidance into provisioning docs | users lose the distinction between dashboard content and dashboard delivery | keep panel design and visualization authoring out of this skill, and keep file delivery here |
| enabling `allowUiUpdates` without a clear merge-back workflow | UI edits appear to work and are later overwritten by files | document file-wins behavior and keep Git as the source of truth |
| using `foldersFromFilesStructure` without checking the directory model | folder placement becomes surprising | make the filesystem layout deliberate before enabling folder mirroring |
| provisioning raw exports with no cleanup or review path | drift and noisy diffs accumulate quickly | keep provisioned dashboard files normalized and reviewable |

## Scope Boundaries

- Activate this skill for:
  - Grafana dashboard provider YAML and dashboard-as-code delivery
  - folder organization, drift control, and reviewability
  - dashboard provisioning behavior and file ownership decisions
- Do not use this skill as the primary source for:
  - dashboard panel authoring and visualization design
  - Prometheus alert-rule authoring or testing
  - Alertmanager routing and notification design
