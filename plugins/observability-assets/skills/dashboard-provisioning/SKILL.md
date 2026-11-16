---
metadata:
  reference:
    Grafana provisioning:
      url: https://grafana.com/docs/grafana/latest/administration/provisioning/
    Grafana:
      version: 13.2.3
      url: https://github.com/grafana/grafana/releases/tag/v13.2.3
name: dashboard-provisioning
description: >-
  Use for Grafana dashboard provider YAML, folder and source-file mapping, file/UI drift, or dashboard delivery ownership.
---

# Dashboard Provisioning

Provision Grafana dashboards as reviewed files instead of relying on long-lived manual UI state.

## Task Focus

- Inspect the existing provider, dashboard source, target Grafana version, and delivery owner.
- Keep the provider name, source path, environment mapping, and folder strategy explicit.
- Review `allowUiUpdates`, `disableDeletion`, and `updateIntervalSeconds` when the task changes drift, deletion, or sync behavior.
- Preserve dashboard identity and the documented raw, wrapped, or resource file shape.
- Verify the intended provisioning directory and runtime mount or copy path from the deployment configuration.
  Preparing those files does not authorize installing them into a running environment.

Use the existing delivery owner, such as provider files, Git Sync, Terraform, or Helm, instead of adding competing API writes.
Obtain explicit authorization for the target before applying files, enabling sync, reloading Grafana, or making API writes.
Confirm overwrite and deletion effects before changing a watched provider path.

## Configuration Locations

When locating provider YAML or diagnosing configuration precedence, read [configuration locations](./references/config-locations.md).
Grafana loads provider config at startup from the deployment's provisioning directory; verify the actual configured paths.

## Provider Configuration

When authoring or reviewing provider fields and defaults, read [provider configuration](./references/provider-schema.md).
Use `type: file` with a readable `options.path`; provider-level `folder` or `folderUid` cannot be combined with `foldersFromFilesStructure`.
The latter mirrors up to four directory levels when both fixed-folder fields are unset.
Defaults are `orgId: 1`, `disableDeletion: false`, `updateIntervalSeconds: 30`, and `allowUiUpdates: false`.

## First Runnable Commands or Code Shape

Validate provider YAML syntax with an existing repository parser.
The following example resolves PyYAML through `uv`, so use it only when dependency resolution is already authorized:

```sh
uv run --with 'pyyaml>=6,<7' python -c "import yaml; yaml.safe_load(open('grafana/provisioning/dashboards.yaml'))"

```

Use when: the provider config was edited and you need a fast syntax check before treating it as ready for deployment.
Replace the path with your actual provider file location.

## Dashboard Source Shapes

When writing or translating raw, classic wrapper, or resource-style files, read [dashboard source shapes](./references/source-file-shapes.md).
Document the chosen file shape; do not confuse provider-path files with HTTP API envelopes or add API-only fields such as `message` without explicit file-format support.
Preserve the target Grafana schema version, remove instance-scoped `id` or set it to `null`, and use a stable `uid`.
Provider YAML owns placement and sync behavior for raw dashboard files; align provider and wrapper folder placement for wrapped files.

## Provider Examples

When adapting fixed-folder, filesystem-driven folder, dashboard source, or environment-specific layouts, read [provider examples](./references/provider-examples.md).

## Environment Variables

When parameterizing provider values, read [provider environment variables](./references/environment-variables.md).
Substitution applies to provider YAML values, not dashboard JSON.
Grafana resolves `${ENV_VAR}` before `$ENV_VAR`; use `$ENV_VAR` when the value may contain `$`, `${ENV_VAR}` to disambiguate adjacent text, and `$$` for a literal dollar sign.

## Sync and Deletion

When changing sync cadence, deletion, or export workflows, read [sync, deletion, and export behavior](./references/sync-and-deletion.md).
Above 10 seconds Grafana polls at `updateIntervalSeconds`; at 10 seconds or less it uses filesystem events, which may not reach Grafana on bind mounts or network filesystems.
Validate the actual runtime path. Removing a source file deletes the dashboard unless `disableDeletion: true`.
UI exports are raw JSON; extract the dashboard object from API responses for a raw-file path, or deliberately document the classic wrapper choice.

## Validate the Result

Check that provider paths resolve to the shipped files and runtime filesystem, folder strategy and environment application are explicit, and the chosen file shape is documented.
Check folder exclusivity, UI merge-back and file-wins behavior, update cadence, supported provider substitution, and reviewable dashboard sources.
Report local syntax and path checks separately from runtime discovery and sync evidence.
Use existing deployment checks when rollout is authorized and state unverified runtime behavior; valid YAML alone does not prove synchronization.

## Output contract

Use the following as recommended defaults.
Follow task, host, and dispatch requirements when they differ.

Return:

1. The recommended provider YAML or review decision
2. The dashboard source path, folder strategy, and environment application shape
3. Validation or deployment checks, including how Grafana will discover the provider file and dashboard path
4. Remaining risks, assumptions, or drift-related caveats

## References

| When working on... | Read... |
| --- | --- |
| folder mirroring, file layout, or `foldersFromFilesStructure` tradeoffs | [`./references/folder-organization.md`](./references/folder-organization.md) |
| UI edits, file-wins behavior, delete-on-remove, version property, or drift debugging | [`./references/drift-and-ui-updates.md`](./references/drift-and-ui-updates.md) |
| Git Sync, K8s resource shapes, or newer observability-as-code features | [`./references/newer-provisioning-features.md`](./references/newer-provisioning-features.md) |

## Invariants

- A `foldersFromFilesStructure` provider MUST NOT set `folder` or `folderUid`. Without folder mirroring, omitting both uses the default folder.
- Provider-path files MUST use a documented raw dashboard, classic wrapper, or Kubernetes resource shape.
- Remove the instance-scoped `id` or set it to `null` before sharing across instances.
- `options.path` MUST be explicit, consistent, and readable at runtime.
- `allowUiUpdates: true` MUST have a documented merge-back workflow; file source and drift behavior SHOULD be clear.
- Do not rely on environment substitution in dashboard JSON or present API-only fields such as `meta` and `message` as raw file content.

## Common Pitfalls

Choose and document one file shape, normalize instance-specific IDs, and keep sources reviewable.
Provisioning ignores dashboard `version` as a conflict gate.
Document merge-back and file-wins behavior before enabling UI updates.
Avoid dropped filesystem events by polling above 10 seconds or fixing the mount.
Do not combine fixed folders with folder mirroring, exceed four mirrored levels, or rely on environment substitution in JSON.

## Scope Boundaries

This skill covers dashboard provider YAML, file delivery, folder organization, drift, runtime sync, and exports.
It excludes panel design, Prometheus alerts and tests, Alertmanager routing, and datasource provisioning.
