# Minimal Route Example

## Minimal Setup

Minimal route tree and receiver layout:

```yaml
route:
  receiver: platform-default
  group_by:
    - alertname
    - service
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - receiver: api-pager
      matchers:
        - team="api"
        - severity="page"

receivers:
  - name: platform-default
  - name: api-pager
```

Use when: you need one readable Alertmanager baseline with a default receiver and one label-based branch.
