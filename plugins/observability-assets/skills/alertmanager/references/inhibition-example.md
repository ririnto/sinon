# Inhibition Example

## Inhibition Rules

Inhibition mutes target alerts when a source alert is already firing and both share specified equal labels.

```yaml
inhibit_rules:
  - name: severity-suppression
    source_matchers:
      - severity="critical"
    target_matchers:
      - severity="warning"
    equal:
      - alertname
      - cluster
      - namespace
```

A rule needs `source_matchers`, `target_matchers`, and `equal` labels that must be identical between source and target.
Deprecated `source_match`/`target_match` map forms still parse but MUST NOT be used in new configs.
Alertmanager prevents an alert from inhibiting itself: a source alert never suppresses the exact same alert instance.
For guidance on choosing `equal` labels safely and reviewing source/target shape, see [`./inhibition-rules.md`](./inhibition-rules.md).
