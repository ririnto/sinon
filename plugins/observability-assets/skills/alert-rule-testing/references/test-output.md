# Test Output Interpretation

Successful run:

```text
PASS  alerts/api-errors.test.yaml   0.003s
PASS  alerts/latency.test.yaml      0.002s

```

Failure output example:

```text
FAIL  alerts/api-errors.test.yaml   0.004s

Expected alert Api5xxRatioAbove5Percent to be firing but it was not firing at 16m0s
Test: api-error-firing
Expr: 5 < round(100 * sum(...) / sum(...), 0.001)
EvalTime: 16m0s
Expected:
  - alertname: Api5xxRatioAbove5Percent
    labels: {severity="page", service="api"}
Actual:
  (no alerts)

```

Common failure messages and their causes:

| Failure message | Likely cause | Fix |
| --- | --- | --- |
| "expected alert ... to be firing but it was not firing" | `eval_time` before `for` window completes, or expression never crosses threshold | Increase `eval_time` past `for` duration, or raise `input_series` values |
| "expected alert ... not to be firing but it was firing" | `eval_time` after `for` window completes unexpectedly | Decrease `eval_time`, lower `input_series` values, or add recovery samples |
| "expected label ... not found" | `exp_labels` contains a key the alert never emits | Remove the key from `exp_labels` or add it to the rule's `labels` block |
| "unexpected alert ... firing" | A different alert instance fired than expected | Add an `exp_alerts` entry for the unexpected alert, or constrain `input_series` so it does not trigger |
| "sample mismatch" (promql_expr_test) | Computed value differs from `exp_samples.value` | Check floating-point precision, use `fuzzy_compare: true`, or verify the expression matches the rule |
