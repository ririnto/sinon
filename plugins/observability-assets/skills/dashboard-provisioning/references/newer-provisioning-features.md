---
title: "Newer Grafana Provisioning Features"
description: "Open this when Git Sync, newer on-prem file provisioning, or Grafana provisioning toggles are the blocker."
---

# Newer Grafana Provisioning Features

Use this reference when legacy provider YAML is not the whole story and the deployment is explicitly using newer Grafana observability-as-code features.

## Version Note

- Treat Git Sync, the newer on-prem file provisioning flow, and the related `provisioning` plus `kubernetesDashboards` toggles as newer Grafana features rather than a universal baseline.

## Review Focus

- verify the deployment actually supports the newer provisioning path being discussed
- verify the needed feature toggles are enabled
- verify the team really needs the newer flow rather than stable legacy file provisioning

Example feature-toggle shape:

```ini
[feature_toggles]
enable = provisioning,kubernetesDashboards
```

Example newer resource shape:

```yaml
apiVersion: dashboard.grafana.app/v1beta1
kind: Dashboard
metadata:
  name: api-overview
spec:
  title: API Overview
```

Use when: the blocker is understanding the minimum config shape that distinguishes the newer provisioning path from legacy provider YAML.
