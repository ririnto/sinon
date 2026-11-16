# Dashboard Source File Shapes

## Dashboard Source File Shape

File provisioning reads dashboard files from the provider `options.path`.
Current Grafana accepts classic dashboard wrapper files and Kubernetes resource-style files.
The ordinary source-control path in this skill uses plain dashboard definitions so the provider YAML owns placement and sync behavior.

Representative source file:
`"schemaVersion": 41` is an illustrative classic JSON value.
Preserve or generate the schema version for the target Grafana instance.

```json
{
  "id": null,
  "uid": "team-api-overview",
  "title": "API Overview",
  "tags": ["operations", "api"],
  "timezone": "browser",
  "schemaVersion": 41,
  "version": 1,
  "refresh": "30s",
  "panels": []
}

```

Field semantics in the source file:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer/null | recommended | Remove it or set it to `null` before committing cross-instance source. Numeric IDs are database scoped and should not be treated as portable identifiers. |
| `uid` | string | recommended | Stable identifier for this dashboard. Used in URLs (`/d/<uid>/...`) and cross-dashboard links. Auto-generated if omitted, but explicit UIDs are strongly recommended for reproducible deployments. |
| `title` | string | yes | Human-readable dashboard title. |
| `schemaVersion` | integer | yes | Classic dashboard JSON schema version for the file. |
| `version` | integer | no | Dashboard revision metadata. Grafana ignores it as a conflict gate during file provisioning. |
| `panels` | array | yes | Panel definitions that make up the dashboard. |

The provider YAML controls placement and sync behavior for this ordinary raw shape.
Keep placement and sync fields in the provider config when the source file is a plain dashboard definition.
Those fields include `folder`, provider-level `folderUid`, `disableDeletion`, `allowUiUpdates`, and `updateIntervalSeconds`.
If the repository intentionally stores Grafana's classic wrapper shape, document that choice.
Keep provider-level placement consistent with the wrapper's `folderUid`.

Use when: you need the correct on-disk shape for dashboard files that Grafana loads from a provider path.

## File Shapes and API Boundary

Do not confuse the chosen provider-path file shape with HTTP API response envelopes.

Grafana's classic file-provisioning wrapper uses a separate outer object:

```json
{
  "dashboard": {
    "id": null,
    "uid": "team-api-overview",
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

This wrapper can live under a provider `options.path` when the team chooses the classic wrapped file shape.
Do not label it as a raw dashboard source file.
HTTP API request bodies can add API-only fields such as `message`.
Keep those API-only fields out of provider-path files unless the current Grafana file provisioning documentation explicitly supports them.

Use when: the blocker is translating between provider-path source files and API or resource payloads that add `dashboard`, `folderUid`, `overwrite`, or `message` fields.
