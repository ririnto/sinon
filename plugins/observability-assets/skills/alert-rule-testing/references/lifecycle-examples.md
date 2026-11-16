# Alert Fixture Examples

## Ready-to-Adapt Templates

Below-threshold check -- prove the alert does not fire during normal traffic:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []

```

Use when: you need a fast regression check for the non-firing case.

Pending check -- prove the rule crossed the threshold but has not satisfied `for` yet:

```yaml
alert_rule_test:
  - eval_time: 8m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []

```

Use when: the alert has a `for` window and you need to prove it is not firing too early.

Pending-state interpretation rule:

- `promtool test rules` does not expose a separate pending assertion type in `exp_alerts`
- Prove pending behavior by pairing an empty `exp_alerts: []` result with an `eval_time` where the threshold is crossed but the `for` window is not yet complete
- Prove true non-firing behavior with another empty `exp_alerts: []` check where the threshold was never crossed

Firing check -- prove expected labels or annotations once the `for` window completes:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts:
      - exp_labels:
          severity: page
          service: api

```

Use when: you need one stable contract for the actual firing state.

Resolved check -- prove the alert stopped firing after recovery:

```yaml
alert_rule_test:
  - eval_time: 25m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts: []

```

Use when: an earlier eval time already proved the alert fired, and a later eval time now proves the alert cleared after the input series recovered.

Resolved-state interpretation rule:

- `promtool test rules` shows resolved behavior by returning no expected firing alerts at a later `eval_time`
- Distinguish resolved from never-firing by pairing this check with an earlier firing assertion on the same alert
- If the rule uses `keep_firing_for`, place the resolved check after that hold-open window rather than immediately after the signal drops

Full lifecycle test covering all four states:

The fixture uses low error rate samples for 0-8m, high error rate samples for 8-20m, and recovery samples for 20-28m.
The assertions then check below-threshold, pending, firing, and resolved behavior in order.

```yaml
tests:
  - name: api-error-full-lifecycle
    interval: 1m
    input_series:
      - series: 'http_requests_total{job="api",status="500"}'
        values: '0+2x8 0+15x12 0+2x8'
      - series: 'http_requests_total{job="api",status="200"}'
        values: '0+98x8 0+85x12 0+98x8'
    alert_rule_test:
      - eval_time: 4m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      - eval_time: 14m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []
      - eval_time: 20m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts:
          - exp_labels:
              severity: page
              service: api
      - eval_time: 26m
        alertname: Api5xxRatioAbove5Percent
        exp_alerts: []

```

Use when: you need one test that covers the entire inactive -> pending -> firing -> resolved lifecycle.

PromQL expression check -- verify one intermediate query result directly:

```yaml
promql_expr_test:
  - expr: sum(increase(http_requests_total{job="api"}[5m]))
    eval_time: 16m
    exp_samples:
      - labels: '{}'
        value: 500

```

Use when: the blocker is an intermediate query shape rather than only the final alert state.

Annotation content check -- verify rendered template output:

```yaml
alert_rule_test:
  - eval_time: 16m
    alertname: Api5xxRatioAbove5Percent
    exp_alerts:
      - exp_labels:
          severity: page
          service: api
        exp_annotations:
          summary: API 5xx ratio is high

```

Use when: the annotation template itself must stay stable alongside the firing condition.
