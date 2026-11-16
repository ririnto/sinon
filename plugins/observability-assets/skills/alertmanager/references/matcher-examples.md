# Matcher Examples

## Label Matchers

Matchers select alerts by label values for routes and inhibition rules.
Each matcher is a string using one of four operators: `=` (exact), `!=` (negated exact), `=~` (anchored regex), `!~` (negated anchored regex).

```yaml
matchers:
  - team="api"
  - severity="critical"
  - alertname=~"NodeDown|InstanceDown"
  - environment!="staging"
```

Modern Alertmanager supports UTF-8 label names and handles the transition from classic ASCII-only names transparently.
Write label names as they appear in your Prometheus metrics.
