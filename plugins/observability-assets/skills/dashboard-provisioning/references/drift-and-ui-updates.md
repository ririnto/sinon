---
title: "Dashboard Provisioning Drift and UI Updates"
description: "Open this when file-wins behavior, UI edits, or drift debugging is the blocker."
---

# Dashboard Provisioning Drift and UI Updates

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

## Review Questions

- can an operator tell where permanent changes must be made
- is `allowUiUpdates` aligned with the actual team workflow
- would a failed provisioning reload be easier to debug with a smaller, cleaner source tree
