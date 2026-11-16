# Sync, Deletion, and Export Behavior

## Update Interval and Runtime Filesystem Behavior

Treat `updateIntervalSeconds` as the configured rescan interval for provisioned dashboard files.

Choose the value based on how quickly file changes need to appear and how predictable the deployment filesystem is:

- values greater than 10 seconds make Grafana poll the provider path at that interval
- values of 10 seconds or less make Grafana rely on filesystem watch events
- Docker bind mounts, network filesystems, and other indirect filesystem paths should be validated in the real runtime path.
  - Watch events may not reach Grafana on those filesystems.

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
| Dashboard menu -> Save JSON to file | Keep the raw dashboard JSON; normalize instance-specific fields such as `id` before committing |
| Dashboard menu -> Copy JSON to clipboard | Paste the raw dashboard JSON into the source file and normalize `id` if present |
| API `GET /api/dashboards/uid/<uid>` | Extract the `dashboard` object from the response and save that raw object as the source file |

UI export methods already produce raw dashboard JSON.
API responses add extra envelope fields such as `dashboard` and `meta`.
Import requests may add `folderUid`, `overwrite`, or `message`.
Extract the dashboard object for the ordinary raw-file path, or deliberately store Grafana's classic wrapper shape and document that choice.
The `id` field from one Grafana instance SHOULD be removed or set to `null` before the file becomes shared source.

Use when: the blocker is understanding what happens when dashboard files are added, modified, or removed from the source directory.
