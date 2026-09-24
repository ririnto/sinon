---
metadata:
  reference:
    Go tool dependencies:
      url: https://go.dev/doc/modules/managing-dependencies#tools
    Prometheus:
      version: 3.14.0
      url: https://prometheus.io/docs/prometheus/3.14/configuration/unit_testing_rules/
---

# Prometheus Rule Tests

Run from `scripts/tests/promtool`:

```sh
go tool promtool check rules alerts/api-errors.rules.yaml
go tool promtool test rules alerts/api-errors.test.yaml
```

`go.mod` pins Prometheus module version `v0.314.0`, which shares the release commit with Prometheus `v3.14.0`.
The rule and tests adapt the examples in `plugins/observability-assets/skills/prometheus-alert-rules/SKILL.md` and `plugins/observability-assets/skills/alert-rule-testing/SKILL.md`.
These files verify the documentation examples; they are not deployable monitoring configuration.
