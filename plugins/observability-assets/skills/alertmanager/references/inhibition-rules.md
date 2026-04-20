---
title: "Alertmanager Inhibition Rules"
description: "Open this when suppressing secondary alerts with inhibition rules is the blocker."
---

Use this reference when the blocker is deciding whether one alert should suppress another after both are already routed into Alertmanager.

## When Inhibition Is Appropriate

Use inhibition when:

- a higher-severity alert already explains the outage
- the lower-severity alert would only repeat the same operator action
- the shared labels that define the same failure domain are stable and explicit

## Choosing `equal` Labels Safely

The `equal` labels should define the smallest scope where both alerts still describe the same incident.

- start with the concrete failure-domain labels such as `cluster`, `service`, `namespace`, or `shard`
- do not use labels that drift between the source and target alerts, or inhibition will become flaky
- do not add labels like `severity` or `alertname` to `equal` when those labels are intentionally different across the source and target alerts

Safe pattern:

```yaml
equal:
  - cluster
  - service
```

Over-broad pattern that can suppress across unrelated environments:

```yaml
equal:
  - service
```

Over-narrow pattern that prevents intended inhibition because the labels differ:

```yaml
equal:
  - cluster
  - service
  - severity
```

## Source and Target Shape Check

- keep the source side narrower and stronger than the target side
- prefer source alerts that already imply the operator action for the target alerts
- if the same alert can satisfy both sides, rewrite the matchers until the relationship is directional

## Review Questions

- can the source and target alerts ever be the same alert
- do the `equal` labels define one real failure domain
- would cleaner routing remove the need for inhibition entirely
