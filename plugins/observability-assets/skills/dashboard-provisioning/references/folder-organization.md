---
title: "Dashboard Provisioning Folder Organization"
description: "Open this when folder mapping, file layout, or foldersFromFilesStructure is the blocker."
---

# Dashboard Provisioning Folder Organization

Use this reference when the provider exists, but the file tree and folder strategy are still unclear.

## Fixed Folder vs Filesystem Mirroring

Use a fixed `folder` when:

- all provisioned dashboards belong in one Grafana folder
- the repository tree is optimized for source control rather than Grafana folder names

Use `foldersFromFilesStructure: true` when:

- top-level directories already represent the intended folder split
- a flat folder mapping is enough

Example provider:

```yaml
apiVersion: 1
providers:
  - name: team-dashboards
    type: file
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: true
```

Example tree:

```text
/var/lib/grafana/dashboards/
  api/
    overview.json
  platform/
    capacity.json
```

Use when: the blocker is deciding whether one provider should mirror top-level directories into Grafana folders.

## Review Questions

- does the repository tree communicate ownership clearly
- would folder mirroring create confusing names or unstable placement
- are nested directories being mistaken for nested Grafana folders
